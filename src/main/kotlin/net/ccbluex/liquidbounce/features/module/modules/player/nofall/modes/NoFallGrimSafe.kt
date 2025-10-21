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
package net.ccbluex.liquidbounce.features.module.modules.player.nofall.modes

import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.features.module.modules.player.nofall.ModuleNoFall
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d

/**
 * NoFallGrimSafe - A more robust GrimAC bypass using legitimate interaction spoofing.
 * Detects when the player is about to land and sends a block interaction packet
 * to trigger Grim's legitimate exemption.
 * This is a safer alternative to faking onGround or velocity.
 */
internal object NoFallGrimSafe : Choice("GrimSafe"), MinecraftShortcuts {

    override val parent: net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable<*>
        get() = ModuleNoFall.modes

    // --- NO CONFIGURABLES ---

    private var fallDistanceThreshold = 2.0f // Fixed threshold for fall detection
    private var isFalling = false // Tracks if the player is currently falling
    private var shouldSpoof = false // Tracks if we should spoof an interaction on landing

    override fun enable() {
        reset()
    }

    override fun disable() {
        reset()
    }

    private fun reset() {
        isFalling = false
        shouldSpoof = false
        fallDistanceThreshold = 2.0f
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        if (!ModuleNoFall.enabled) return@handler

        val packet = event.packet

        // --- ARM ON DAMAGE OR VELOCITY ---
        // When the player takes damage or receives velocity (e.g., from falling), arm the spoof.
        if (packet is EntityDamageS2CPacket && packet.entityId == player.id) {
            // Optionally check if the damage is from falling (you could inspect packet.damageSource)
            // For simplicity, we assume any damage from self is potentially from fall.
            isFalling = true
            shouldSpoof = false // Reset spoof state if a new fall begins
        } else if (packet is EntityVelocityUpdateS2CPacket && packet.entityId == player.id) {
            // Check if the velocity is significant (indicating a fall)
            val velocityX = packet.velocityX / 8000.0
            val velocityY = packet.velocityY / 8000.0
            val velocityZ = packet.velocityZ / 8000.0
            val velocityMagnitude = kotlin.math.sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ)

            // If velocity magnitude is significant, assume player is falling
            if (velocityMagnitude > 0.5) {
                isFalling = true
                shouldSpoof = false // Reset spoof state
            }
        }
        // --- END ARM ON DAMAGE OR VELOCITY ---
    }

    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> { event ->
        if (!ModuleNoFall.enabled) return@handler

        // --- DETECT LANDING ---
        // Check if the player has just landed (was falling, is now on ground)
        if (isFalling && player.isOnGround) {
            // Check if the fall distance was significant
            // We check fallDistance here as well, but it might have been reset by the server
            // So we rely on the fact that we were falling and are now on ground.
            if (player.fallDistance > fallDistanceThreshold || isFalling) {
                // --- SPOOF INTERACTION ---
                // Send a block interaction packet to simulate the player interacting with the ground
                // This is what Grim considers a legitimate reason for not taking fall damage.
                shouldSpoof = true
                isFalling = false // Reset falling state

                // --- FIND GROUND BLOCK ---
                // Get the block position directly below the player's feet
                val blockPos = BlockPos(player.x.toInt(), player.y.toInt() - 1, player.z.toInt())
                val blockState = world.getBlockState(blockPos)

                // Check if it's a solid block we can interact with
                if (!blockState.isAir && blockState.isSolidBlock(world, blockPos)) {
                    // Calculate a hit result on the top face of the block
                    // We aim for the center of the block's top face
                    val hitPos = Vec3d.of(blockPos).add(0.5, 1.0, 0.5) // Center-top of the block
                    val hitResult = BlockHitResult(hitPos, Direction.UP, blockPos, false)

                    // Send the interaction packet
                    network.sendPacket(
                        PlayerInteractBlockC2SPacket(
                            Hand.MAIN_HAND,
                            hitResult,
                            0 // Sequence (0 is often acceptable for simple interactions)
                        )
                    )

                    // Swing hand to complete the interaction (optional but more realistic)
                    player.swingHand(Hand.MAIN_HAND)
                }
                // --- END FIND GROUND BLOCK ---
            } else {
                // Fall distance was not significant, reset state
                isFalling = false
            }
        }
        // --- END DETECT LANDING ---
    }
}
