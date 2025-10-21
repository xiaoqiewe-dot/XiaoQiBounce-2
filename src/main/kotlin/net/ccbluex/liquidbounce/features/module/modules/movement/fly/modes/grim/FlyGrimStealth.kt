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
package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.grim

import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.events.PlayerNetworkMovementTickEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.ModuleFly
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.ModuleFly.modes
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.util.math.Vec3d
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * @anticheat Grim
 * @anticheatVersion 2.3.59+ (stealth version)
 * @note Improved version with smooth disabling
 *       to avoid simulation flags
 */
@Suppress("LongParameterList", "FunctionNaming", "MaxLineLength")
internal object FlyGrimStealth : Choice("GrimStealth") {

    private val toggle by int("Toggle", 0, 0..200)
    private val maxFlightTicks by int("MaxFlightTicks", 80, 20..200)
    private val baseSpeed by float("BaseSpeed", 0.08f, 0.01f..0.2f)
    private val verticalFactor by float("VerticalFactor", 0.04f, 0.01f..0.1f)
    private val horizontalFactor by float("HorizontalFactor", 0.02f, 0.01f..0.1f)
    private val packetDelay by int("PacketDelay", 2, 1..5)
    private val motionType by int("MotionType", 0, 0..3)
    private val disableTicks by int("DisableTicks", 3, 1..10)

    override val parent: ChoiceConfigurable<*>
        get() = modes

    private var ticks = 0
    private var packetCounter = 0
    private var motionPhase = 0.0
    private var lastPosition: Vec3d? = null
    private var startPosition: Vec3d? = null
    private var randomSeed = 0L
    private var disabling = false
    private var disableProgress = 0

    override fun enable() {
        ticks = 0
        packetCounter = 0
        motionPhase = 0.0
        lastPosition = null
        startPosition = player.pos
        randomSeed = System.currentTimeMillis()
        disabling = false
        disableProgress = 0
        player.jump()
    }

    override fun disable() {
        if (ticks > 10) {
            disabling = true
            disableProgress = 0
        }
    }

    private fun shouldDisable(): Boolean {
        return (toggle != 0 && ticks >= toggle) ||
            ticks >= maxFlightTicks ||
            (disabling && disableProgress >= disableTicks)
    }

    private fun calculateMotion(): Vec3d {
        motionPhase += 0.1
        if (motionPhase > 6.283) motionPhase = 0.0

        val currentBaseSpeed = baseSpeed
        val currentVerticalFactor = verticalFactor
        val currentHorizontalFactor = horizontalFactor

        val disableFactor = if (disabling) {
            1.0f - (disableProgress.toFloat() / disableTicks.toFloat())
        } else {
            1.0f
        }

        return when (motionType) {
            0 -> {
                val radius = 0.1 * disableFactor
                Vec3d(
                    radius * cos(motionPhase),
                    (currentBaseSpeed + currentVerticalFactor *
                        sin(motionPhase * 0.5)) * disableFactor,
                    radius * sin(motionPhase)
                )
            }
            1 -> {
                Vec3d(
                    currentHorizontalFactor * sin(motionPhase) * disableFactor,
                    (currentBaseSpeed + currentVerticalFactor *
                        sin(motionPhase * 2)) * disableFactor,
                    currentHorizontalFactor * cos(motionPhase) * disableFactor
                )
            }
            2 -> {
                val random = Random(randomSeed + ticks)
                Vec3d(
                    (random.nextDouble() - 0.5) * currentHorizontalFactor *
                        0.1 * disableFactor,
                    (currentBaseSpeed + (random.nextDouble() - 0.5) *
                        currentVerticalFactor * 0.1) * disableFactor,
                    (random.nextDouble() - 0.5) * currentHorizontalFactor *
                        0.1 * disableFactor
                )
            }
            3 -> {
                val random = Random(randomSeed + ticks)
                Vec3d(
                    (0.05 * cos(motionPhase) +
                        (random.nextDouble() - 0.5) * 0.02) * disableFactor,
                    (currentBaseSpeed + 0.03 * sin(motionPhase * 2) +
                        (random.nextDouble() - 0.5) * 0.01) * disableFactor,
                    (0.05 * sin(motionPhase) +
                        (random.nextDouble() - 0.5) * 0.02) * disableFactor
                )
            }
            else -> {
                val radius = 0.1 * disableFactor
                Vec3d(
                    radius * cos(motionPhase),
                    (currentBaseSpeed + currentVerticalFactor *
                        sin(motionPhase * 0.5)) * disableFactor,
                    radius * sin(motionPhase)
                )
            }
        }
    }

    val tickHandler = handler<PlayerTickEvent> { event ->
        if (disabling) {
            disableProgress++

            if (disableProgress >= disableTicks) {
                player.setVelocity(0.0, -0.1, 0.0)
                ModuleFly.enabled = false
                return@handler
            }
        }

        if (ticks > 0) {
            val motion = calculateMotion()

            player.setVelocity(
                player.velocity.x * 0.9 + motion.x,
                motion.y,
                player.velocity.z * 0.9 + motion.z
            )
        }

        if (shouldDisable() && !disabling) {
            ModuleFly.enabled = false
            return@handler
        }

        ticks++
    }

    @Suppress("unused")
    val movementPacketsPre = handler<PlayerNetworkMovementTickEvent> { event ->
        packetCounter++

        val effectivePacketDelay = if (disabling) {
            packetDelay + 2
        } else {
            packetDelay
        }

        if (packetCounter % effectivePacketDelay == 0) {
            if (event.state == EventState.PRE) {
                lastPosition = player.pos

                val offsetMultiplier = if (disabling) {
                    1.0f - (disableProgress.toFloat() / disableTicks.toFloat())
                } else {
                    1.0f
                }

                val offsetX = (if (Random.nextBoolean()) 0.05 else -0.05) *
                    offsetMultiplier
                val offsetZ = (if (Random.nextBoolean()) 0.05 else -0.05) *
                    offsetMultiplier

                player.setPosition(
                    player.pos.x + offsetX,
                    player.pos.y,
                    player.pos.z + offsetZ
                )
            } else {
                lastPosition?.let {
                    player.setPosition(it)
                    lastPosition = null
                }
            }
        }

        if (!disabling && packetCounter % 10 == 0 && ticks > 10) {
            sendConfusionPacket()
        }
    }

    private fun sendConfusionPacket() {
        try {
            // 尝试使用 OnGroundOnly 构造函数，提供所有必需参数
            val packet = PlayerMoveC2SPacket.OnGroundOnly(
                Random.nextBoolean(), // onGround
                false // horizontalCollision
            )
            network.sendPacket(packet)
        } catch (e: Exception) {
            try {
                // 尝试使用 PositionAndOnGround 构造函数，提供所有必需参数
                val packet = PlayerMoveC2SPacket.PositionAndOnGround(
                    player.x,
                    player.y,
                    player.z,
                    Random.nextBoolean(), // onGround
                    false // horizontalCollision
                )
                network.sendPacket(packet)
            } catch (e2: Exception) {
                // 如果所有方法都失败，就不发送混淆数据包
                // 这不会影响主要功能
            }
        }
    }
}
