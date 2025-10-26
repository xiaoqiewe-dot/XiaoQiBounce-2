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
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.player.nofall.modes.NoFallBlink
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * VelocityGrimReverse - Mirrors knockback vectors to pull the player
 * back into the fight while adding subtle randomisation to stay within
 * GrimAC's motion heuristics.
 */
internal object VelocityGrimReverse : VelocityMode("GrimReverse") {

    private val scale by float("Scale", 1.0f, 0.3f..2.0f)
    private val invertVertical by boolean("InvertVertical", true)
    private val noise by float("Noise", 0.05f, 0.0f..0.3f, "%")

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        when (val packet = event.packet) {
            is EntityVelocityUpdateS2CPacket -> {
                if (packet.entityId != player.id) {
                    return@handler
                }

                val modifier = scale.toDouble()
                val horizontalNoise = randomNoise()

                packet.velocityX = (-packet.velocityX.toDouble() * (modifier + horizontalNoise)).roundToInt()
                packet.velocityZ = (-packet.velocityZ.toDouble() * (modifier - horizontalNoise)).roundToInt()

                if (invertVertical) {
                    val verticalNoise = randomNoise(divider = 1.5)
                    packet.velocityY = (-packet.velocityY.toDouble() * (modifier + verticalNoise)).roundToInt()
                }

                NoFallBlink.waitUntilGround = true
            }

            is ExplosionS2CPacket -> {
                packet.playerKnockback.ifPresent { knockback ->
                    val modifier = scale.toDouble()
                    val noiseX = randomNoise()
                    val noiseZ = randomNoise()

                    knockback.x = -(knockback.x * (modifier + noiseX))
                    knockback.z = -(knockback.z * (modifier + noiseZ))

                    if (invertVertical) {
                        val noiseY = randomNoise(divider = 1.5)
                        knockback.y = -(knockback.y * (modifier + noiseY))
                    }

                    NoFallBlink.waitUntilGround = true
                }
            }
        }
    }

    private fun randomNoise(divider: Double = 1.0): Double {
        if (noise <= 0f) {
            return 0.0
        }

        val amplitude = noise / 100.0 / divider
        return Random.nextDouble(-amplitude, amplitude)
    }
}
