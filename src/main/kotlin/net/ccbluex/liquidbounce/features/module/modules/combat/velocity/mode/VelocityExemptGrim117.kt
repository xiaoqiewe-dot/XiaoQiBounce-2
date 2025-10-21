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

import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.sequenceHandler
import net.ccbluex.liquidbounce.utils.client.Timer
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket
import net.minecraft.util.math.Vec3d
import kotlin.random.Random

/**
 * Optimized Grim Velocity Bypass
 * Enhanced stealth version with reduced detection footprint
 */
internal object VelocityExemptGrim117 : VelocityMode("ExemptGrim117") {

    private var alternativeBypass by boolean("AlternativeBypass", true)
    private var packetDelay by int("PacketDelay", 1, 0..3)
    private var useRandomOffset by boolean("UseRandomOffset", true)
    private var motionReduction by float("MotionReduction", 0.85f, 0.5f..1.0f)
    private var maxAttempts by int("MaxAttempts", 2, 1..5)
    private var cooldownTicks by int("CooldownTicks", 40, 20..100)

    private var canCancel = false
    private var attemptCount = 0
    private var lastCancelTime = 0L
    private var lastPosition: Vec3d? = null

    override fun enable() {
        canCancel = false
        attemptCount = 0
        lastCancelTime = 0L
        lastPosition = null
    }

    @Suppress("unused")
    private val packetHandler = sequenceHandler<PacketEvent> { event ->
        val packet = event.packet
        val currentTime = System.currentTimeMillis()

        // Reset attempt counter if enough time has passed
        if (currentTime - lastCancelTime > cooldownTicks * 50L) {
            attemptCount = 0
        }

        // Check for damage to activate cancellation
        if (packet is EntityDamageS2CPacket && packet.entityId == player.id) {
            if (attemptCount < maxAttempts) {
                canCancel = true
                attemptCount++
                lastCancelTime = currentTime
            }
        }

        // Handle velocity cancellation with enhanced stealth
        if ((packet is EntityVelocityUpdateS2CPacket && packet.entityId == player.id ||
                packet is ExplosionS2CPacket) && canCancel) {

            event.cancelEvent()

            // Apply partial motion reduction instead of complete cancellation
            player.setVelocity(
                player.velocity.x * motionReduction,
                player.velocity.y * motionReduction,
                player.velocity.z * motionReduction
            )

            // Variable delay to avoid patterns
            val actualDelay = if (packetDelay > 0) {
                packetDelay + Random.nextInt(0, 2)
            } else {
                1
            }

            waitTicks(actualDelay)

            // Send movement packets with random variations
            val repeatCount = if (alternativeBypass) 3 + Random.nextInt(0, 2) else 1

            repeat(repeatCount) {
                // Add microscopic random offsets to break patterns
                val offsetX = if (useRandomOffset && Random.nextFloat() > 0.3f)
                    (Random.nextDouble() - 0.5) * 0.001 else 0.0
                val offsetY = if (useRandomOffset && Random.nextFloat() > 0.3f)
                    (Random.nextDouble() - 0.5) * 0.001 else 0.0
                val offsetZ = if (useRandomOffset && Random.nextFloat() > 0.3f)
                    (Random.nextDouble() - 0.5) * 0.001 else 0.0

                // Add tiny random look variations to avoid AimDuplicateLook
                val lookYaw = if (useRandomOffset)
                    player.yaw + (Random.nextFloat() - 0.5f) * 0.5f else player.yaw
                val lookPitch = if (useRandomOffset)
                    player.pitch + (Random.nextFloat() - 0.5f) * 0.3f else player.pitch

                network.sendPacket(
                    PlayerMoveC2SPacket.Full(
                        player.x + offsetX,
                        player.y + offsetY,
                        player.z + offsetZ,
                        lookYaw,
                        lookPitch,
                        player.isOnGround,
                        player.horizontalCollision
                    )
                )
            }

            // Send mining action with probability to avoid patterns
            if (Random.nextFloat() > 0.2f) { // 80% chance to send
                network.sendPacket(
                    PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                        player.blockPos,
                        player.horizontalFacing.opposite
                    )
                )
            }

            canCancel = false

            // Enforce cooldown after max attempts
            if (attemptCount >= maxAttempts) {
                waitTicks(cooldownTicks)
            }
        }
    }
}
