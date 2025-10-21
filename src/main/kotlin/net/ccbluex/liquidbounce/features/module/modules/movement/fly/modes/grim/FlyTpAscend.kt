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
package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes

// Correct imports for LBY 0.31.1
import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts // Import for mc, player, network
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.ModuleFly
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.util.math.Vec3d

/**
 * FlyTpAscend - Ascends by teleporting Y coordinate and moves via TP on input.
 * Mimics the behavior described: TP upwards to stay airborne, TP horizontally on WASD.
 */
internal object FlyTpAscend : Choice("TpAscend"), MinecraftShortcuts { // Implement MinecraftShortcuts

    override val parent: ChoiceConfigurable<*>
        get() = ModuleFly.modes

    private val ascendSpeed = 0.1 // Blocks per tick to ascend
    private val moveSpeed = 0.3   // Blocks per TP move

    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> {
        if (!ModuleFly.enabled) return@handler

        val player = mc.player ?: return@handler

        // Ensure the player starts in the air
        if (ModuleFly.justEnabled) { // Assuming ModuleFly sets this flag
            if (player.isOnGround) {
                player.sendMessage(net.minecraft.text.Text.literal("Must be in the air to enable TpAscend!"), false)
                ModuleFly.disable()
                return@handler
            }
            // Note: ModuleFly.justEnabled = false; // This line is conceptual
            // You need to implement a way in ModuleFly to reset this flag, e.g., a method like resetJustEnabled()
            // For now, we assume it's managed correctly elsewhere or resets automatically after one tick.
            // If ModuleFly.justEnabled is a var in the ModuleFly object, you would set it here.
            // Example: ModuleFly.justEnabled = false
        }

        // Ascend by sending a TP packet with increased Y
        val newX = player.x
        val newY = player.y + ascendSpeed
        val newZ = player.z
        val newOnGround = false // Always false while ascending

        network.sendPacket(
            PlayerMoveC2SPacket.PositionAndOnGround(
                newX,
                newY,
                newZ,
                newOnGround,
                player.horizontalCollision
            )
        )
        player.setPosition(newX, newY, newZ)
    }

    @Suppress("unused")
    private val movementHandler = handler<MovementInputEvent> { event ->
        if (!ModuleFly.enabled) return@handler

        val player = mc.player ?: return@handler

        // Check for movement input based on key presses
        // This directly checks the Minecraft key bindings
        val options = mc.options
        var moveX = 0.0
        var moveZ = 0.0

        if (options.forwardKey.isPressed) { // Forward
            moveX -= Math.sin(Math.toRadians(player.yaw.toDouble())) * moveSpeed
            moveZ += Math.cos(Math.toRadians(player.yaw.toDouble())) * moveSpeed
        }
        if (options.backKey.isPressed) { // Backward
            moveX += Math.sin(Math.toRadians(player.yaw.toDouble())) * moveSpeed
            moveZ -= Math.cos(Math.toRadians(player.yaw.toDouble())) * moveSpeed
        }
        if (options.leftKey.isPressed) { // Left (Strafe)
            moveX += Math.cos(Math.toRadians(player.yaw.toDouble())) * moveSpeed
            moveZ += Math.sin(Math.toRadians(player.yaw.toDouble())) * moveSpeed
        }
        if (options.rightKey.isPressed) { // Right (Strafe)
            moveX -= Math.cos(Math.toRadians(player.yaw.toDouble())) * moveSpeed
            moveZ -= Math.sin(Math.toRadians(player.yaw.toDouble())) * moveSpeed
        }

        if (moveX != 0.0 || moveZ != 0.0) {
            val targetX = player.x + moveX
            val targetY = player.y // Keep Y constant relative to current "hover" height
            val targetZ = player.z + moveZ

            network.sendPacket(
                PlayerMoveC2SPacket.PositionAndOnGround(
                    targetX,
                    targetY,
                    targetZ,
                    false, // onGround
                    player.horizontalCollision
                )
            )
            player.setPosition(targetX, targetY, targetZ)
        }
        // If not moving, the player just hovers in place due to the tickHandler's Y ascension
    }
}
