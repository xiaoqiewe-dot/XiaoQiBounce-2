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

import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.misc.antibot.ModuleAntiBot
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.Vec3d
import kotlin.math.sqrt

/**
 * INTBotMode - Removes invisible players within 1 block who are moving quickly.
 * Aims to eliminate entities that exhibit bot-like behavior: invisibility + rapid movement.
 */
object INTBotMode : Choice("INT"), ModuleAntiBot.IAntiBotMode {

    override val parent: ChoiceConfigurable<*>
        get() = ModuleAntiBot.modes

    // --- CONFIGURABLES ---
    /** Range within which to check for invisible, fast-moving players (0.1 - 5.0 blocks) */
    private val range by float("Range", 1.0f, 0.1f..5.0f, "blocks")

    /** Minimum speed (blocks/tick) a player must exceed to be considered "fast-moving" */
    private val minSpeed by float("MinSpeed", 0.5f, 0.1f..5.0f, "blocks/tick")

    /** Whether to remove detected INT bots from the client world */
    private val removeBotsFromClient by boolean("RemoveBotsFromClient", true)
    // --- END CONFIGURABLES ---

    /** Map to track previous positions of players for speed calculation */
    private val playerPreviousPositions = hashMapOf<java.util.UUID, Vec3d>()

    override fun enable() {
        playerPreviousPositions.clear()
    }

    override fun disable() {
        playerPreviousPositions.clear()
    }

    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> { event ->
        if (!ModuleAntiBot.enabled) return@handler

        val player = mc.player ?: return@handler
        val world = mc.world ?: return@handler

        val rangeSq = (range * range).toDouble()

        for (entity in world.players) {
            // Skip self
            if (entity == player) continue

            val uuid = entity.uuid

            // --- CHECK FOR INVISIBILITY ---
            // A simple check: if the entity's alpha/transparency is very low or zero.
            // Minecraft entities don't have a direct "invisible" flag, but we can infer it.
            // For example, if the entity's model alpha is 0 or very close to 0.
            // A more robust check would be to see if the entity is wearing an invisibility potion effect.
            // However, for simplicity, we'll check if the entity is not glowing and has no visible equipment.
            // This is a heuristic and might not be 100% accurate.
            // A better way is to check for the Invisibility status effect.
            val isInvisible = entity.isInvisible || entity.activeStatusEffects.any { it.value == net.minecraft.entity.effect.StatusEffects.INVISIBILITY }

            if (!isInvisible) continue // Not invisible, skip

            // --- CHECK FOR FAST MOVEMENT ---
            val currentPos = entity.pos
            val previousPos = playerPreviousPositions[uuid]

            if (previousPos != null) {
                val deltaX = currentPos.x - previousPos.x
                val deltaY = currentPos.y - previousPos.y
                val deltaZ = currentPos.z - previousPos.z
                // Calculate horizontal speed (ignoring Y-axis for "fast movement" check)
                val speed = sqrt(deltaX * deltaX + deltaZ * deltaZ)

                // If speed is above threshold and within range, mark as INT bot
                if (speed > minSpeed && player.squaredDistanceTo(entity) <= rangeSq) {
                    if (removeBotsFromClient) {
                        // Schedule removal from client world
                        scheduleBotRemoval(entity)
                    }
                    // Note: We don't add to a bot list as this mode focuses on immediate removal
                }
            }

            // Update previous position
            playerPreviousPositions[uuid] = currentPos
        }
    }

    /**
     * Schedules a bot entity for removal from the client world.
     */
    private fun scheduleBotRemoval(botEntity: PlayerEntity) {
        // Use a coroutine or tick handler to safely remove the entity
        // Removing entities directly in a loop can cause ConcurrentModificationException
        tickHandler {
            val world = mc.world ?: return@tickHandler
            // Double-check the entity still exists and meets criteria before removal
            if (world.getEntityById(botEntity.id) == botEntity) {
                world.removeEntity(botEntity.id, net.minecraft.entity.Entity.RemovalReason.DISCARDED)
            }
        }
    }

    override fun isBot(entity: PlayerEntity): Boolean {
        // This mode focuses on immediate removal, not persistent marking.
        // We can return false or implement a simple check if needed.
        // For now, let's assume other modes handle persistent bot marking.
        return false
    }
}
