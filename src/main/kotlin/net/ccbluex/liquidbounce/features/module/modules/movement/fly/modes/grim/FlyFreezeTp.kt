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
 * FlyFreezeTP - Alternates between Freeze and Teleport to create a fly-like experience.
 * When enabled, it automatically toggles Freeze and performs teleports in the player's facing direction.
 * This creates a flying motion through repeated freeze/unfreeze cycles with teleports.
 */
internal object FlyFreezeTP : Choice("FreezeTP"), MinecraftShortcuts {

    override val parent: ChoiceConfigurable<*>
        get() = ModuleFly.modes

    // Distance to teleport each time freeze is toggled (0.0001 to 0.1 blocks)
    private val tpDistance by float("TPDistance", 0.05f, 0.0001f..0.1f, "blocks")

    // Timer for managing freeze state changes
    private var cycleTimer = 0
    // Tracks the initial freeze state
    private var initialFreezeToggled = false

    override fun enable() {
        cycleTimer = 0
        initialFreezeToggled = false

        // Toggle freeze to enabled state
        if (!net.ccbluex.liquidbounce.features.module.modules.movement.ModuleFreeze.enabled) {
            net.ccbluex.liquidbounce.features.module.modules.movement.ModuleFreeze.enable()
            initialFreezeToggled = true
        }
    }

    override fun disable() {
        // Ensure freeze is disabled when this mode is disabled
        if (net.ccbluex.liquidbounce.features.module.modules.movement.ModuleFreeze.enabled && initialFreezeToggled) {
            net.ccbluex.liquidbounce.features.module.modules.movement.ModuleFreeze.disable()
        }
        cycleTimer = 0
        initialFreezeToggled = false
    }

    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> { event ->
        if (!ModuleFly.enabled) return@handler

        val player = mc.player ?: return@handler

        // Cycle through: Freeze ON -> TP -> Freeze OFF -> TP -> repeat
        cycleTimer++

        when {
            cycleTimer == 1 -> {
                // First tick: Enable freeze and perform first teleport
                if (!net.ccbluex.liquidbounce.features.module.modules.movement.ModuleFreeze.enabled) {
                    net.ccbluex.liquidbounce.features.module.modules.movement.ModuleFreeze.enable()
                }
                performTeleport(player)
            }
            cycleTimer == 2 -> {
                // Second tick: Disable freeze
                if (net.ccbluex.liquidbounce.features.module.modules.movement.ModuleFreeze.enabled) {
                    net.ccbluex.liquidbounce.features.module.modules.movement.ModuleFreeze.disable()
                }
            }
            cycleTimer == 3 -> {
                // Third tick: Perform teleport while unfrozen
                performTeleport(player)
            }
            cycleTimer == 4 -> {
                // Fourth tick: Reset for next cycle
                cycleTimer = 0
            }
        }
    }

    private fun performTeleport(player: net.minecraft.entity.player.PlayerEntity) {
        // Calculate teleport direction based on player's yaw
        val yawRad = Math.toRadians(player.yaw.toDouble())
        val deltaX = -sin(yawRad) * tpDistance
        val deltaZ = cos(yawRad) * tpDistance

        // Calculate new position
        val newX = player.x + deltaX
        val newZ = player.z + deltaZ

        // Send teleport packet
        network.sendPacket(
            PlayerMoveC2SPacket.PositionAndOnGround(
                newX,
                player.y,
                newZ,
                false,
                player.horizontalCollision
            )
        )

        // Update player position locally
        player.setPosition(newX, player.y, newZ)
    }
}
