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
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.sequenceHandler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.client.isOlderThanOrEqual1_8
import net.ccbluex.liquidbounce.utils.client.network
import net.minecraft.client.network.AbstractClientPlayerEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.SwordItem
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.util.Hand

/**
 * Sword Block Module
 * Provides blocking with sword and item scaling when blocking
 */
object ModuleSwordBlock : ClientModule("SwordBlock", Category.COMBAT, aliases = arrayOf("OldBlocking")) {

    // Basic settings - Only these two are visible
    val onlyVisual by boolean("OnlyVisual", false)
    private val hideOffhand by boolean("Hidden", true)

    // Hidden settings - 移除了额外参数
    private val enableSneakAction by boolean("EnableSneak", true)
    private val sneakDuration by int("SneakDuration", 10, 1..40)
    private val clientShowSneak by boolean("ClientShowSneak", true)
    private val enableItemScale by boolean("EnableItemScale", true)
    private val itemScaleSize by float("ItemScaleSize", 1.5f, 1.0f..3.0f)

    // ... (其余代码保持不变，例如内部状态、tickHandler、packetHandler、方法等)
    // Internal state
    private var sneakCounter = 0
    private var lastBlockTime = 0L
    private var isSneaking = false
    private var originalSneakState = false
    private var isBlocking = false
    private var blockStartTime = 0L

    // Player tick handler - handle sneak logic
    @Suppress("UNUSED")
    private val tickHandler = handler<PlayerTickEvent> { event ->
        if (!enableSneakAction) return@handler

        val currentTime = System.currentTimeMillis()

        // Check sneak trigger conditions - fixed to duration mode only
        val shouldSneak = currentTime - lastBlockTime < sneakDuration * 50L

        if (shouldSneak && sneakCounter < sneakDuration) {
            // Start or continue sneaking
            if (!isSneaking) {
                startSneak()
            }
            sneakCounter++
        } else if (isSneaking && (!shouldSneak || sneakCounter >= sneakDuration)) {
            // Stop sneaking
            stopSneak()
        }
    }

    // Packet handler - handle blocking logic
    @Suppress("UNUSED")
    private val packetHandler = sequenceHandler<PacketEvent> {
        val packet = it.packet

        if (packet is PlayerInteractItemC2SPacket) {
            val hand = packet.hand
            val itemInHand = player.getStackInHand(hand) // 此处player引用现在应该正常了

            if (hand == Hand.MAIN_HAND && itemInHand.item is SwordItem) {
                lastBlockTime = System.currentTimeMillis()
                isBlocking = true
                blockStartTime = System.currentTimeMillis()

                if (onlyVisual) {
                    // Visual only mode: cancel original packet
                    it.cancelEvent()
                } else {
                    // Normal mode: send OFF_HAND packet for sword blocking
                    if (!isOlderThanOrEqual1_8) {
                        it.cancelEvent()
                        network.sendPacket(PlayerInteractItemC2SPacket(Hand.OFF_HAND, packet.sequence, player.yaw, player.pitch))
                    }
                }
            }
        }

        // Detect right-click release
        if (packet is PlayerMoveC2SPacket) {
            // Check if we should stop blocking
            if (isBlocking && System.currentTimeMillis() - blockStartTime > 100) {
                // Check if player is no longer using item
                if (!player.isUsingItem) {
                    isBlocking = false
                    if (isSneaking) {
                        stopSneak()
                    }
                }
            }
        }
    }

    private fun startSneak() {
        isSneaking = true

        // Save original sneak state
        originalSneakState = player.isSneaking

        // Send sneak packet to server
        try {
            network.sendPacket(PlayerMoveC2SPacket.OnGroundOnly(true, false))
        } catch (e: Exception) {
            // Handle network exceptions
            e.printStackTrace()
        }

        // If client should show sneak, set local sneak state
        if (clientShowSneak) {
            player.isSneaking = true
        }
    }

    private fun stopSneak() {
        isSneaking = false
        sneakCounter = 0

        // Send stand up packet to server
        try {
            network.sendPacket(PlayerMoveC2SPacket.OnGroundOnly(false, false))
        } catch (e: Exception) {
            // Handle network exceptions
            e.printStackTrace()
        }

        // Restore original sneak state
        player.isSneaking = originalSneakState
    }

    /**
     * Check if item scaling should be applied
     */
    fun shouldScaleItem(): Boolean {
        return enableItemScale && isBlocking && enabled
    }

    /**
     * Get item scale factor
     */
    fun getCurrentItemScale(): Float {
        return if (shouldScaleItem()) itemScaleSize else 1.0f
    }

    /**
     * Check if offhand should be hidden
     * Used by other Mixin classes
     */
    fun shouldHideOffhand(): Boolean {
        return hideOffhand && isBlocking && enabled
    }

    /**
     * Check if specific player and item should hide offhand
     * Used by other Mixin classes
     */
    fun shouldHideOffhand(player: AbstractClientPlayerEntity, item: Item): Boolean {
        return hideOffhand && isBlocking && enabled && player == this.player
    }

    /**
     * Check if specific player and item should hide offhand
     * Used by other Mixin classes
     */
    fun shouldHideOffhand(player: PlayerEntity, item: Item): Boolean {
        return hideOffhand && isBlocking && enabled && player == this.player
    }

    override fun enable() {
        sneakCounter = 0
        isSneaking = false
        lastBlockTime = 0L
        isBlocking = false
        originalSneakState = player.isSneaking
    }

    override fun disable() {
        // Ensure we stop sneaking when module is disabled
        if (isSneaking) {
            stopSneak()
        }
        isBlocking = false
    }
}
