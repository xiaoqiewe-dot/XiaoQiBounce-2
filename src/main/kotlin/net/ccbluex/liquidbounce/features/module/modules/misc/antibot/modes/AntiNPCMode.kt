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
package net.ccbluex.liquidbounce.features.module.modules.misc.antibot.modes

import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.misc.antibot.ModuleAntiBot
import net.ccbluex.liquidbounce.utils.client.chat
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import kotlin.math.abs
import kotlin.math.atan2

/**
 * AntiNPCMode
 *
 * Detects and optionally hides fast-orbiting NPC bots that usually surround the player.
 * Includes an option to check for invisibility.
 */
object AntiNPCMode : Choice("AntiNPC"), ModuleAntiBot.IAntiBotMode {

    override val parent: ChoiceConfigurable<*>
        get() = ModuleAntiBot.modes

    private val checkInvisible by boolean("CheckInvisible", true)
    private val clientRemove by boolean("ClientRemove", true)
    private val announce by boolean("AnnounceRemoval", true)
    private val radius by float("Radius", 3.0f, 1.0f..6.0f)
    private val minAngularVelocity by float("MinAngularSpeed", 90.0f, 30.0f..720.0f, "deg/s")

    private val lastAngles = HashMap<Int, Pair<Double, Long>>()
    private val removed = IntOpenHashSet()

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        lastAngles.clear()
        removed.clear()
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        // Cleanup stale entries
        val now = System.currentTimeMillis()
        lastAngles.entries.removeIf { now - it.value.second > 2_000 }
    }

    override fun reset() {
        lastAngles.clear()
        removed.clear()
    }

    override fun isBot(entity: PlayerEntity): Boolean {
        // Basic proximity filter
        if (player.squaredDistanceTo(entity) > (radius * radius)) {
            return false
        }

        // Optional invisibility check
        if (checkInvisible && entity.isInvisible) {
            handleRemoval(entity)
            return true
        }

        // Compute angular velocity around local player
        val dx = entity.x - player.x
        val dz = entity.z - player.z
        val angle = Math.toDegrees(atan2(dz, dx))
        val now = System.currentTimeMillis()

        val last = lastAngles[entity.id]
        if (last != null) {
            val dAngle = smallestAngleDiff(angle, last.first)
            val dt = (now - last.second).coerceAtLeast(1)
            val angularSpeed = abs(dAngle) * 1000.0 / dt // deg/s
            lastAngles[entity.id] = angle to now

            if (angularSpeed >= minAngularVelocity) {
                handleRemoval(entity)
                return true
            }
        } else {
            lastAngles[entity.id] = angle to now
        }

        return false
    }

    private fun smallestAngleDiff(a: Double, b: Double): Double {
        var diff = (a - b) % 360.0
        if (diff < -180.0) diff += 360.0
        if (diff >= 180.0) diff -= 360.0
        return diff
    }

    private fun handleRemoval(entity: Entity) {
        if (!clientRemove || removed.contains(entity.id)) return

        world.removeEntity(entity.id, Entity.RemovalReason.DISCARDED)
        removed.add(entity.id)
        if (announce) chat("Removed suspected NPC: ${entity.name.string}")
    }
}
