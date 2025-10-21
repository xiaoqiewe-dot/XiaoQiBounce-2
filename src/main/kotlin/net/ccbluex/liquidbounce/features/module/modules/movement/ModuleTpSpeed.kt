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
import net.ccbluex.liquidbounce.utils.client.Chronometer
import net.ccbluex.liquidbounce.features.command.CommandManager // Import CommandManager

/**
 * ModuleTpSpeed - Continuously executes .tp commands internally.
 * Executes .tp <X> <Y> <Z> based on the player's current position.
 * Useful for triggering internal .tp command functionality.
 */
object ModuleTpSpeed : ClientModule("TpSpeed", Category.MISC) {

    // --- CONFIGURABLES ---
    /** Delay between executing .tp commands in ticks. */
    private val delay by int("Delay", 20, 0..200, "ticks")

    /** Enable or disable the execution of .tp commands. */
    private val executeTp by boolean("ExecuteTp", true)
    // --- END CONFIGURABLES ---

    private val chronometer = Chronometer()

    override fun enable() {
        chronometer.reset()
    }

    override fun disable() {
        // Optional: Notify player on disable
        // player?.sendMessage(net.minecraft.text.Text.literal("TpSpeed disabled."), false)
    }

    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> { event ->
        if (!enabled) return@handler

        val player = mc.player ?: return@handler

        // Check if it's time to execute the next .tp command
        if (chronometer.hasElapsed((delay * 50L))) { // Convert ticks to milliseconds (1 tick = 50ms)
            if (executeTp) {
                // Get player's current position
                val x = player.x
                val y = player.y
                val z = player.z

                // Format the .tp command string
                val tpCommand = ".tp %.3f %.3f %.3f".format(x, y, z)

                // --- KEY CHANGE: Execute command internally ---
                // Instead of sending as chat message, execute it as a command
                try {
                    // Use CommandManager to parse and execute the command
                    // This mimics typing the command in chat and pressing enter
                    CommandManager.execute(tpCommand)
                } catch (e: Exception) {
                    // Handle potential errors in command execution
                    // e.printStackTrace() // Optional: Print stack trace for debugging
                    // player.sendMessage(net.minecraft.text.Text.literal("Error executing command: $tpCommand"), false)
                }
                // --- END KEY CHANGE ---
            }

            // Reset the chronometer for the next cycle
            chronometer.reset()
        }
    }
}
