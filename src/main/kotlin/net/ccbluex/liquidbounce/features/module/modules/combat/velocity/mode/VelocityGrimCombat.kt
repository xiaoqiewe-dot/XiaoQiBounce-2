/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2025 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.velocity.mode

import com.google.common.collect.Queues
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.sequenceHandler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.utils.raycast
import net.ccbluex.liquidbounce.utils.client.PacketSnapshot
import net.ccbluex.liquidbounce.utils.client.handlePacket
import net.ccbluex.liquidbounce.utils.combat.CombatManager
import net.ccbluex.liquidbounce.utils.combat.findEnemy
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen
import net.minecraft.item.consume.UseAction
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import kotlin.random.Random

/**
 * VelocityGrimCombat - Advanced Grim AC bypass for Velocity with combat-specific features.
 * Uses interaction spoofing to trigger Grim's exemption and adds combat-related restrictions.
 *
 * 基于交互欺骗的高级Grim反作弊绕过模块，用于无击退，并增加了战斗相关限制。
 * 核心是通过模拟玩家与方块交互来触发Grim的合法豁免，而非直接取消Velocity包。
 */
internal object VelocityGrimCombat : VelocityMode("GrimCombat") {

    // --- CONFIGURABLES ---
    /** Delay in ticks after taking damage before allowing attacks. 0-20 ticks.
     *
     *  受到伤害后，在允许攻击前等待的刻数。范围 0-20 刻。
     */
    private val attackDelay by int("AttackDelayAfterHit", 10, 0..20, "ticks")

    /** Radius in blocks to disable jumping when enemies are nearby. 0-50 blocks.
     *
     *  当附近有敌人时禁用跳跃的半径。范围 0-50 格。
     */
    private val enemyJumpRadius by float("EnemyJumpRadius", 10f, 0f..50f, "blocks")

    /** Debug mode to print messages to chat.
     *
     *  调试模式，向聊天框打印消息。
     */
    private val debug by boolean("Debug", false)
    // --- END CONFIGURABLES ---

    // --- INTERNAL STATE ---
    /** Flag indicating if a damage packet has been received and we should prepare to cancel velocity.
     *
     *  标志位，表示是否已收到伤害包，准备取消击退。
     */
    private var canCancel = false

    /** Flag indicating if packet delaying is active.
     *
     *  标志位，表示是否正在延迟数据包。
     */
    private var delayPackets = false

    /** Flag indicating if a click interaction is needed to spoof the exemption.
     *
     *  标志位，表示是否需要执行点击交互来欺骗豁免。
     */
    private var needClick = false

    /** Flag indicating if we are waiting for a block update packet to confirm the spoof.
     *
     *  标志位，表示是否正在等待方块更新包来确认欺骗。
     */
    private var waitForBlockUpdate = false

    /** Flag to skip the next interaction packet to avoid conflicts.
     *
     *  标志位，表示跳过下一个交互包以避免冲突。
     */
    private var shouldSkipNextInteract = false

    /** Queue to hold delayed packets.
     *
     *  用于存储被延迟的数据包的队列。
     */
    private val delayedPackets = Queues.newConcurrentLinkedQueue<PacketSnapshot>()

    /** Timer to track ticks since last damage.
     *
     *  计时器，用于追踪自上次受到伤害以来的刻数。
     */
    private var damageTicks = 0

    /** Flag to indicate if the player is currently taking damage.
     *
     *  标志位，表示玩家当前是否正在受到伤害。
     */
    private var isTakingDamage = false
    // --- END INTERNAL STATE ---

    override fun enable() {
        resetState()
    }

    override fun disable() {
        flushDelayedPackets()
        resetState()
    }

    /** Resets all internal state variables to their default values.
     *
     *  将所有内部状态变量重置为默认值。
     */
    private fun resetState() {
        canCancel = false
        delayPackets = false
        needClick = false
        waitForBlockUpdate = false
        shouldSkipNextInteract = false
        delayedPackets.clear()
        damageTicks = 0
        isTakingDamage = false
    }

    /** Processes and sends all delayed packets.
     *
     *  处理并发送所有被延迟的数据包。
     */
    private fun flushDelayedPackets() {
        delayedPackets.forEach { handlePacket(it.packet) }
        delayedPackets.clear()
    }

    // --- PACKET HANDLERS ---

    /**
     * Intercepts outgoing packets related to block interaction.
     * Cancels them to prevent the server from knowing about the action if needed.
     *
     * 拦截与方块交互相关的传出数据包。
     * 如果需要，取消它们以防止服务器知道该操作。
     */
    @Suppress("unused")
    private val packetHandler = sequenceHandler<PacketEvent> { event ->
        val packet = event.packet

        // --- CRITICAL: NEVER delay KeepAlive packets to prevent TransactionOrder detection ---
        // 关键：永远不要延迟 KeepAlive 包，以防止 TransactionOrder 检测
        if (packet is KeepAliveS2CPacket) {
            return@sequenceHandler
        }

        // --- SKIP INTERACTION PACKETS TO AVOID CONFLICTS ---
        // 跳过交互包以避免冲突
        if (packet is PlayerInteractEntityC2SPacket || packet is PlayerInteractBlockC2SPacket) {
            shouldSkipNextInteract = true
        }

        // --- BLOCK POSITION UPDATES WHILE WAITING FOR BLOCK UPDATE ---
        // 等待方块更新时阻止位置更新
        if (packet is PlayerMoveC2SPacket && packet.changePosition && waitForBlockUpdate) {
            event.cancelEvent()
            return@sequenceHandler
        }

        // --- HANDLE BLOCK UPDATE CONFIRMATION ---
        // 处理方块更新确认
        if (waitForBlockUpdate && packet is BlockUpdateS2CPacket && packet.pos == player.blockPos) {
            waitTicks(1)
            waitForBlockUpdate = false
            needClick = false
            return@sequenceHandler
        }

        // --- DELAY NON-CRITICAL PACKETS IF ACTIVE ---
        // 如果处于活动状态，则延迟非关键包
        if (waitForBlockUpdate) return@sequenceHandler

        if (delayPackets) {
            delayedPackets.add(PacketSnapshot(packet, event.origin, System.currentTimeMillis()))
            event.cancelEvent()
            return@sequenceHandler
        }

        // --- ARM ON DAMAGE ---
        // 受到伤害时准备
        if (packet is EntityDamageS2CPacket && packet.entityId == player.id) {
            if (debug) {
                player.sendMessage(net.minecraft.text.Text.literal("[GrimCombat] Damage received."), false)
            }
            canCancel = true
            isTakingDamage = true
            damageTicks = 0 // Reset damage timer
        }

        // --- INTERCEPT VELOCITY OR EXPLOSION ---
        // 拦截击退或爆炸
        if (((packet is EntityVelocityUpdateS2CPacket && packet.entityId == player.id)
                || packet is ExplosionS2CPacket)
            && canCancel
        ) {
            if (debug) {
                player.sendMessage(net.minecraft.text.Text.literal("[GrimCombat] Velocity/Explosion packet intercepted."), false)
            }

            val rotation = Rotation(player.yaw, 90f)
            val hitResult = raycast(rotation = rotation)
            val blockState = world.getBlockState(hitResult.blockPos)

            // --- CHECK IF INTERACTION IS VALID ---
            // 检查交互是否有效
            val isValidBlock = !blockState.isAir && blockState.isOpaqueFullCube
            val canSpoof = player.activeItem.useAction != UseAction.EAT
                && player.activeItem.useAction != UseAction.DRINK
                && !InventoryManager.isInventoryOpen
                && mc.currentScreen !is GenericContainerScreen

            if (canSpoof && isValidBlock) {
                event.cancelEvent()
                delayPackets = true
                needClick = true
                if (debug) {
                    player.sendMessage(net.minecraft.text.Text.literal("[GrimCombat] Spoof initiated."), false)
                }
            }
            canCancel = false
            isTakingDamage = false
        }
    }

    // --- TICK HANDLERS ---

    /**
     * Handles player tick events for interaction spoofing and combat restrictions.
     *
     * 处理玩家刻事件，用于交互欺骗和战斗限制。
     */
    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> { event ->
        // --- UPDATE DAMAGE TIMER ---
        // 更新伤害计时器
        if (isTakingDamage) {
            damageTicks++
            if (damageTicks > 20) { // Cap the timer to prevent overflow
                isTakingDamage = false
                damageTicks = 0
            }
        }

        // --- COMBAT RESTRICTIONS: DISABLE ATTACK ---
        // 战斗限制：禁用攻击
        if (isTakingDamage && damageTicks < attackDelay) {
            CombatManager.pauseCombatForAtLeast(attackDelay - damageTicks)

            if (debug) {
                player.sendMessage(net.minecraft.text.Text.literal("[GrimCombat] Combat paused for ${attackDelay - damageTicks} ticks."), false)
            }
        }

        // --- COMBAT RESTRICTIONS: DISABLE JUMP ---
        // 战斗限制：禁用跳跃
        if (enemyJumpRadius > 0f) {
            val nearbyEnemy = world.findEnemy(0f..enemyJumpRadius)
            if (nearbyEnemy != null) {
                player.abilities.allowFlying = false // Prevent creative flight jump
                // Note: Directly preventing jump input requires mixin or deeper integration.
                // This is a partial solution.
                if (debug) {
                    player.sendMessage(net.minecraft.text.Text.literal("[GrimCombat] Jump restricted due to nearby enemy."), false)
                }
            }
        }

        // --- INTERACTION SPOOFING LOGIC ---
        // 交互欺骗逻辑
        if (needClick) {
            val pitch = 90f - (Random.nextFloat() * 0.09f) - 0.01f
            val hitResult = raycast(rotation = Rotation(player.yaw, pitch))
            val pos = hitResult.blockPos.offset(hitResult.side)

            if (pos == player.blockPos && !shouldSkipNextInteract) {
                delayPackets = false
                flushDelayedPackets()

                network.sendPacket(
                    PlayerMoveC2SPacket.LookAndOnGround(
                        player.yaw,
                        pitch,
                        player.isOnGround,
                        player.horizontalCollision
                    )
                )

                if (interaction.interactBlock(player, Hand.MAIN_HAND, hitResult) == ActionResult.SUCCESS) {
                    player.swingHand(Hand.MAIN_HAND)
                }

                waitForBlockUpdate = true
                needClick = false
                if (debug) {
                    player.sendMessage(net.minecraft.text.Text.literal("[GrimCombat] Interaction spoofed."), false)
                }
            } else {
                // If interaction fails, flush packets to avoid getting stuck
                // 如果交互失败，则刷新数据包以避免卡住
                delayPackets = false
                flushDelayedPackets()
                needClick = false
                if (debug) {
                    player.sendMessage(net.minecraft.text.Text.literal("[GrimCombat] Interaction failed, flushing packets."), false)
                }
            }
        }

        if (waitForBlockUpdate) {
            event.cancelEvent()
        }

        shouldSkipNextInteract = false
    }

    /**
     * Handles timeout for interaction spoofing to prevent getting stuck.
     *
     * 处理交互欺骗的超时，以防止卡住。
     */
    @Suppress("unused")
    private val timeoutHandler = tickHandler {
        waitUntil { waitForBlockUpdate }

        repeat(5) {
            waitTicks(1)
            if (!waitForBlockUpdate) return@tickHandler
        }

        waitForBlockUpdate = false
        needClick = false
        delayPackets = false
        flushDelayedPackets()
        if (debug) {
            player.sendMessage(net.minecraft.text.Text.literal("[GrimCombat] Interaction timed out, flushing packets."), false)
        }
    }
}
