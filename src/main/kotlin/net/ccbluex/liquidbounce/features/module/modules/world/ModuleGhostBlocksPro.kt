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
package net.ccbluex.liquidbounce.features.module.modules.world

import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.client.Chronometer
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import kotlin.random.Random

/**
 * ModuleGhostBlocksPro - Sends periodic, randomized interaction packets to simulate
 * environmental interaction without modifying the client world or sending block change packets.
 * Aims to be less detectable by Grim's Simulation/Timer/PacketOrder checks.
 * NOTE: This does not create actual "fake blocks" you can stand on.
 * It only sends interaction packets to potentially confuse anti-cheat heuristics.
 */
object ModuleGhostBlocksPro : ClientModule("GhostBlocksPro", Category.WORLD) {

    // --- CONFIGURABLES ---
    /** Base delay between interaction packets in ticks. */
    private val baseDelay by int("BaseDelay", 20, 1..100, "ticks")

    /** Random delay variance in ticks (added to base delay). */
    private val delayVariance by int("DelayVariance", 10, 0..50, "ticks")

    /** Chance (0-100%) to send an interaction packet each eligible tick. */
    private val sendChance by int("SendChance", 30, 0..100, "%")

    /** Swing hand when sending an interaction packet. */
    private val swingHand by boolean("SwingHand", true)
    // --- END CONFIGURABLES ---

    private val chronometer = Chronometer()
    private var nextDelay = 0

    override fun enable() {
        chronometer.reset()
        // Calculate initial delay
        nextDelay = baseDelay + Random.nextInt(-delayVariance, delayVariance + 1)
        nextDelay = nextDelay.coerceAtLeast(1) // Ensure delay is at least 1 tick
    }

    override fun disable() {
        chronometer.reset()
    }

    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> { event ->
        if (!enabled) return@handler

        val player = mc.player ?: return@handler
        val network = mc.networkHandler ?: return@handler

        // Check if it's time to send the next packet
        if (chronometer.hasElapsed((nextDelay * 50L))) { // Convert ticks to milliseconds (assuming 20 TPS)
            // Random chance to send
            if (Random.nextInt(100) < sendChance) {

                // --- TARGET CALCULATION ---
                // Target a position slightly in front and below the player
                val yawRad = Math.toRadians(player.yaw.toDouble())
                val pitchRad = Math.toRadians(player.pitch.toDouble())

                // Calculate forward direction vector
                val dirX = -kotlin.math.sin(yawRad) * kotlin.math.cos(pitchRad)
                val dirY = -kotlin.math.sin(pitchRad)
                val dirZ = kotlin.math.cos(yawRad) * kotlin.math.cos(pitchRad)

                // Target position: 1 block in front and 0.5 blocks below
                val targetX = player.x + dirX
                val targetY = player.y + dirY - 0.5
                val targetZ = player.z + dirZ

                val targetPos = BlockPos(targetX.toInt(), targetY.toInt(), targetZ.toInt())
                val hitPos = Vec3d(targetX, targetY, targetZ)
                // --- END TARGET CALCULATION ---

                // --- SEND INTERACTION PACKET ---
                // Create a BlockHitResult at the target position
                // We use UP direction as a default, it might not matter for air interaction
                val hitResult = BlockHitResult(hitPos, Direction.UP, targetPos, false)

                // Send the PlayerInteractBlockC2SPacket
                network.sendPacket(
                    PlayerInteractBlockC2SPacket(
                        Hand.MAIN_HAND, // Use main hand
                        hitResult,
                        0 // Sequence, not critical for simple spoofing
                    )
                )

                // Optional: Swing hand
                if (swingHand) {
                    player.swingHand(Hand.MAIN_HAND)
                }
                // --- END SEND INTERACTION PACKET ---
            }

            // Reset chronometer and calculate next delay
            chronometer.reset()
            nextDelay = baseDelay + Random.nextInt(-delayVariance, delayVariance + 1)
            nextDelay = nextDelay.coerceAtLeast(1) // Ensure delay is at least 1 tick
        }
    }
}
