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

import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.event.events.PlayerMoveEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.minecraft.util.math.Vec3d
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * tpSaZX - On enable, continuously moves the player in the direction
 * they are currently facing, with configurable distance, axis enable/disable, delay, and count.
 * Uses movement input simulation to avoid direct packet sending and reduce detection risk.
 */
object ModuleTpSaZX : ClientModule("TpSaZX", Category.MOVEMENT) {

    private val enableX by boolean("X", true)
    private val enableY by boolean("Y", true)
    private val enableZ by boolean("Z", true)
    private val delay by int("Delay", 5, 0..30, "ticks") // Increased default delay
    private val tpDistance by float("TpDistance", 0.01f, 0.001f..0.01f, "blocks")
    private val simultaneousTpCount by int("SimultaneousTpCount", 1, 1..20, "tps")

    private var currentDelay = 0
    private var shouldMove = false

    override fun enable() {
        currentDelay = 0
        shouldMove = true
    }

    override fun disable() {
        currentDelay = 0
        shouldMove = false
    }

    @Suppress("unused")
    private val moveHandler = handler<PlayerMoveEvent> { event ->
        if (!enabled || !shouldMove) return@handler

        // Handle delay
        if (currentDelay > 0) {
            currentDelay--
            return@handler
        }

        val player = mc.player ?: return@handler

        // Get player's facing direction based on yaw and pitch
        val yaw = Math.toRadians(player.yaw.toDouble())
        val pitch = Math.toRadians(player.pitch.toDouble())

        // Calculate the direction vector components
        val dirX = -sin(yaw) * cos(pitch)
        val dirY = -sin(pitch)
        val dirZ = cos(yaw) * cos(pitch)

        // Normalize and scale by tpDistance and simultaneousTpCount
        val direction = Vec3d(dirX, dirY, dirZ).normalize()
        val totalOffset = direction.multiply((tpDistance * simultaneousTpCount).toDouble())

        // Apply movement input only to enabled axes
        if (enableX) event.movement.x += totalOffset.x
        if (enableY) event.movement.y += totalOffset.y
        if (enableZ) event.movement.z += totalOffset.z

        // Reset delay counter
        currentDelay = delay + Random.nextInt(0, 3) // Add random jitter to delay
    }
}
