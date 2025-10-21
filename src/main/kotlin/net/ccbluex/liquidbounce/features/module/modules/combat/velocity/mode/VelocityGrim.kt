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
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import kotlin.random.Random

internal object VelocityGrim : VelocityMode("VelocityGrim") {

    private var canCancel = false
    private var delayPackets = false
    private var needClick = false
    private var waitForBlockUpdate = false
    private var shouldSkipNextInteract = false
    private val delayedPackets = Queues.newConcurrentLinkedQueue<PacketSnapshot>()

    override fun enable() {
        reset()
    }

    override fun disable() {
        flushDelayedPackets()
    }

    private fun reset() {
        canCancel = false
        delayPackets = false
        needClick = false
        waitForBlockUpdate = false
        shouldSkipNextInteract = false
        delayedPackets.clear()
    }

    private fun flushDelayedPackets() {
        delayedPackets.forEach { handlePacket(it.packet) }
        delayedPackets.clear()
    }

    @Suppress("unused")
    private val packetHandler = sequenceHandler<PacketEvent> { event ->
        val packet = event.packet

        // NEVER delay KeepAlive — prevents TransactionOrder
        if (packet is KeepAliveS2CPacket) {
            return@sequenceHandler
        }

        if (packet is PlayerInteractEntityC2SPacket || packet is PlayerInteractBlockC2SPacket) {
            shouldSkipNextInteract = true
        }

        if (packet is PlayerMoveC2SPacket && packet.changePosition && waitForBlockUpdate) {
            event.cancelEvent()
            return@sequenceHandler
        }

        if (waitForBlockUpdate && packet is BlockUpdateS2CPacket && packet.pos == player.blockPos) {
            waitTicks(1)
            waitForBlockUpdate = false
            needClick = false
            return@sequenceHandler
        }

        if (waitForBlockUpdate) return@sequenceHandler

        if (delayPackets) {
            delayedPackets.add(PacketSnapshot(packet, event.origin, System.currentTimeMillis()))
            event.cancelEvent()
            return@sequenceHandler
        }

        if (packet is EntityDamageS2CPacket && packet.entityId == player.id) {
            canCancel = true
        }

        if (packet is EntityVelocityUpdateS2CPacket && packet.entityId == player.id && canCancel) {
            val rotation = Rotation(player.yaw, 90f)
            val hitResult = raycast(rotation = rotation)
            val blockState = world.getBlockState(hitResult.blockPos)

            val isValid = !blockState.isAir && blockState.isOpaqueFullCube
            val canSpoof = player.activeItem.useAction != UseAction.EAT
                && player.activeItem.useAction != UseAction.DRINK
                && !InventoryManager.isInventoryOpen
                && mc.currentScreen !is GenericContainerScreen

            if (canSpoof && isValid) {
                event.cancelEvent()
                delayPackets = true
                needClick = true
            }
            canCancel = false
        }
    }

    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> { event ->
        if (needClick) {
            val pitch = 90f - Random.nextFloat() * 0.09f - 0.01f
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
            } else {
                delayPackets = false
                flushDelayedPackets()
                needClick = false
            }
        }

        if (waitForBlockUpdate) {
            event.cancelEvent()
        }

        shouldSkipNextInteract = false
    }

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
    }
}
