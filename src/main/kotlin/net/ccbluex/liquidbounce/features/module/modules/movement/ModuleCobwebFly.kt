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
package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.block.collideBlockIntersects
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.FIRST_PRIORITY
import net.minecraft.block.CobwebBlock
import kotlin.math.cos
import kotlin.math.sin

/**
 * CobwebFly
 *
 * Allows controlled flight while inside cobwebs only.
 * Has higher priority than regular flight modules and supports adjustable speeds.
 */
object ModuleCobwebFly : ClientModule("CobwebFly", Category.MOVEMENT) {

    private val horizontalSpeed by float("HorizontalSpeed", 0.2f, 0.01f..0.64f)
    private val verticalSpeed by float("VerticalSpeed", 0.1f, 0.01f..1.1f)
    private val onlyInCobweb by boolean("OnlyInCobweb", true)

    private fun isInCobweb(): Boolean {
        val box = player.boundingBox
        return box.collideBlockIntersects(checkCollisionShape = false) { it is CobwebBlock }
    }

    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent>(priority = FIRST_PRIORITY) {
        val inWeb = isInCobweb()
        if (onlyInCobweb && !inWeb) return@handler

        // Compute desired horizontal motion from movement input
        val forward = player.input.movementForward.toDouble()
        val strafe = player.input.movementSideways.toDouble()

        val yawRad = Math.toRadians(player.yaw.toDouble())
        var motionX = 0.0
        var motionZ = 0.0

        if (forward != 0.0 || strafe != 0.0) {
            val norm = kotlin.math.hypot(forward, strafe)
            val f = forward / norm
            val s = strafe / norm

            // Transform input into world-relative motion by yaw
            motionX = (f * -sin(yawRad) + s * cos(yawRad)) * horizontalSpeed
            motionZ = (f * cos(yawRad) - s * -sin(yawRad)) * horizontalSpeed
        }

        var motionY = 0.0
        if (mc.options.jumpKey.isPressed) motionY += verticalSpeed
        if (mc.options.sneakKey.isPressed) motionY -= verticalSpeed

        // If not moving and not ascending/descending, keep current vertical velocity (web glide)
        val vel = player.velocity
        val newVelX = motionX
        val newVelY = if (motionY != 0.0 || inWeb) motionY else vel.y
        val newVelZ = motionZ
        player.setVelocity(newVelX, newVelY, newVelZ)

        // Reduce server-side slow effects while in webs by marking on-ground false
        if (inWeb) player.setOnGround(false)
    }
}
