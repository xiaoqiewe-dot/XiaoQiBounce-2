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
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.ModuleFly
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import kotlin.math.cos
import kotlin.math.sin

/**
 * FlyFreezeTp - Combines Freeze and Teleport to create a fly-like experience.
 * When enabled, it freezes the player (prevents movement) and continuously teleports
 * the player forward in the direction they are facing.
 * This allows for a form of "freezing" followed by "teleporting" flight.
 */
internal object FlyFreezeTp : Choice("FreezeTp"), MinecraftShortcuts {

    override val parent: ChoiceConfigurable<*>
        get() = ModuleFly.modes

    // --- CONFIGURABLES ---
    /** Distance to teleport each tick (0.0001 to 0.1 blocks) */
    private val tpDistance by float("TPDistance", 0.05f, 0.0001f..0.1f, "blocks")

    /** Speed of teleportation in ticks (0.01 to 10 ticks per teleport) */
    private val tpSpeed by float("TPSpeed", 1.0f, 0.01f..10f, "ticks")
    // --- END CONFIGURABLES ---

    private var teleportTimer = 0f
    private var wasFrozen = false

    override fun enable() {
        // Ensure freeze is enabled
        if (!net.ccbluex.liquidbounce.features.module.modules.movement.ModuleFreeze.enabled) {
            net.ccbluex.liquidbounce.features.module.modules.movement.ModuleFreeze.enable()
        }
        wasFrozen = true
        teleportTimer = 0f
    }

    override fun disable() {
        // Optionally disable freeze if it was enabled by this mode
        if (wasFrozen && net.ccbluex.liquidbounce.features.module.modules.movement.ModuleFreeze.enabled) {
            net.ccbluex.liquidbounce.features.module.modules.movement.ModuleFreeze.disable()
        }
        wasFrozen = false
        teleportTimer = 0f
    }

    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> { event ->
        if (!ModuleFly.enabled) return@handler

        val player = mc.player ?: return@handler

        // --- FREEZE LOGIC ---
        // Cancel the tick event to freeze the player
        // This is handled by the ModuleFreeze itself, but we ensure it's active
        if (!net.ccbluex.liquidbounce.features.module.modules.movement.ModuleFreeze.enabled) {
            net.ccbluex.liquidbounce.features.module.modules.movement.ModuleFreeze.enable()
        }
        // --- END FREEZE LOGIC ---

        // --- TELEPORT LOGIC ---
        // Increment the teleport timer
        teleportTimer += 1.0f // Each tick

        // Check if it's time to teleport based on the configured speed
        if (teleportTimer >= tpSpeed) {
            // Reset the timer
            teleportTimer = 0f

            // Calculate the teleport direction based on player's yaw
            val yawRad = Math.toRadians(player.yaw.toDouble())
            val deltaX = -sin(yawRad) * tpDistance
            val deltaZ = cos(yawRad) * tpDistance

            // Calculate the new position
            val newX = player.x + deltaX
            val newZ = player.z + deltaZ

            // Send a packet to teleport the player
            // We'll send a full packet to ensure the server knows the new position
            // This is a simplified version; in practice, you might want to use
            // a more precise packet type or consider the implications of sending
            // too many packets (e.g., anti-cheat detection).
            network.sendPacket(
                PlayerMoveC2SPacket.Full(
                    newX,
                    player.y,
                    newZ,
                    player.yaw,
                    player.pitch,
                    player.isOnGround, // Keep the original onGround state
                    player.horizontalCollision // Keep the original horizontalCollision state
                )
            )

            // Optionally, also update the player's position locally
            // This can help prevent desync, but it's not strictly necessary if the server
            // handles the packet correctly.
            // player.setPosition(newX, player.y, newZ)

            // --- END TELEPORT LOGIC ---
        }
    }
}
