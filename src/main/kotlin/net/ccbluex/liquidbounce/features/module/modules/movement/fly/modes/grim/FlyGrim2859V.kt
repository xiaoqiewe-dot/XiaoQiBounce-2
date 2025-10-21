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
 *
 *
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
import net.minecraft.util.math.Vec3d
import kotlin.math.sin
import kotlin.random.Random

/**
 * @anticheat Grim
 * @anticheatVersion 2.3.59+ (timer-free version)
 * @testedOn eu.loyisa.cn
 * @note Timer-free version to avoid time-based detections
 */
internal object FlyGrim2859V : Choice("Grim2859-V") {

    private val toggle by int("Toggle", 40, 0..200)
    private val offsetRange by int("OffsetRange", 512, 256..1024)
    private val useRandomOffset by boolean("RandomOffset", true)
    private val maxFlightTicks by int("MaxFlightTicks", 60, 20..120)
    private val verticalSpeed by float("VerticalSpeed", 0.1f, 0.05f..0.2f)
    private val horizontalSpeed by float("HorizontalSpeed", 0.2f, 0.1f..0.4f)
    private val motionPattern by int("MotionPattern", 0, 0..2)

    override val parent: ChoiceConfigurable<*>
        get() = modes

    private var ticks = 0
    private var originalPos: Vec3d? = null
    private var currentOffsetX = 0
    private var currentOffsetZ = 0
    private var motionPhase = 0
    private var lastPositionChange = 0

    override fun enable() {
        ticks = 0
        originalPos = null
        motionPhase = 0
        lastPositionChange = 0
        generateNewOffset()
    }

    override fun disable() {
        // 不需要重置计时器，因为我们不使用它
    }

    private fun generateNewOffset() {
        val baseOffset = offsetRange
        if (useRandomOffset) {
            currentOffsetX = if (Random.nextBoolean()) baseOffset else -baseOffset
            currentOffsetZ = if (Random.nextBoolean()) baseOffset else -baseOffset
        } else {
            currentOffsetX = baseOffset
            currentOffsetZ = baseOffset
        }
    }

    private fun shouldDisable(): Boolean {
        return (toggle != 0 && ticks >= toggle) || ticks >= maxFlightTicks
    }

    private fun calculateMotion(): Vec3d {
        motionPhase++
        if (motionPhase > 40) motionPhase = 0

        val baseVertical = verticalSpeed
        horizontalSpeed

        return when (motionPattern) {
            1 -> {
                // 模式1: 波浪形运动
                val wave = sin(motionPhase * 0.1).toFloat()
                Vec3d(
                    player.velocity.x * 0.9,
                    (baseVertical * wave).toDouble(),
                    player.velocity.z * 0.9
                )
            }
            2 -> {
                // 模式2: 脉冲式运动
                val pulse = if (motionPhase % 15 < 5) baseVertical * 1.5f else baseVertical * 0.5f
                Vec3d(
                    player.velocity.x * 0.85,
                    pulse.toDouble(),
                    player.velocity.z * 0.85
                )
            }
            else -> {
                // 模式0: 平滑运动
                val smooth = when {
                    motionPhase < 10 -> baseVertical * 0.7f
                    motionPhase < 25 -> baseVertical
                    motionPhase < 35 -> baseVertical * 0.5f
                    else -> baseVertical * 0.3f
                }
                Vec3d(
                    player.velocity.x * 0.9,
                    smooth.toDouble(),
                    player.velocity.z * 0.9
                )
            }
        }
    }

    val tickHandler = handler<PlayerTickEvent> { event ->
        when {
            ticks == 0 -> {
                // 初始跳跃
                player.jump()
            }
            ticks % 12 == 0 -> {
                // 定期改变偏移
                generateNewOffset()
            }
            ticks - lastPositionChange > 5 -> {
                // 每5刻改变一次位置偏移
                generateNewOffset()
                lastPositionChange = ticks
            }
        }

        // 应用运动
        if (ticks > 1) {
            val motion = calculateMotion()
            player.setVelocity(motion.x, motion.y, motion.z)
        }

        // 检查是否应该禁用
        if (shouldDisable()) {
            ModuleFly.enabled = false
            return@handler
        }

        ticks++
    }

    @Suppress("unused")
    val movementPacketsPre = handler<PlayerNetworkMovementTickEvent> { event ->
        // 只在特定时刻使用位置偏移，而不是每一刻都使用
        if (ticks >= 2 && ticks % 3 == 0) { // 每3刻使用一次偏移
            if (event.state == EventState.PRE) {
                // 存储原始位置并移动到偏移位置
                originalPos = player.pos
                player.setPosition(
                    player.pos.x + currentOffsetX,
                    player.pos.y,
                    player.pos.z + currentOffsetZ
                )
            } else {
                // 恢复原始位置
                originalPos?.let {
                    player.setPosition(it)
                    originalPos = null
                }
            }
        }
    }
}
