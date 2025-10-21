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

import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.ModuleFly
import net.minecraft.util.math.Vec3d

/**
 * FlyHeypixelStyle - Aims to mimic the smooth, hovering flight seen in clients like Heypixel/Rise.
 * Focuses on counteracting gravity for hovering and applying smooth velocity for movement.
 * Avoids obvious teleportation packets to reduce detection risk.
 */
internal object FlyHeypixelStyle : Choice("HeypixelStyle"), MinecraftShortcuts {

    override val parent: ChoiceConfigurable<*>
        get() = ModuleFly.modes

    // --- CONFIGURABLES ---
    // These can be fine-tuned to match the desired "feel"
    private val hoverStrength by float("HoverStrength", 0.1f, 0.0f..0.5f, "blocks/tick²")
    private val moveSpeed by float("MoveSpeed", 0.2f, 0.05f..0.5f, "blocks/tick")
    private val acceleration by float("Acceleration", 0.1f, 0.01f..0.3f, "factor/tick")
    // --- END CONFIGURABLES ---

    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> {
        if (!ModuleFly.enabled) return@handler

        val player = mc.player ?: return@handler

        // --- HOVER LOGIC ---
        // Counteract gravity to achieve a stable hover.
        // This is done every tick to maintain the effect.
        if (!player.isOnGround) {
            // Apply an upward force to counteract gravity (which is ~0.08 blocks/tick²)
            // The `hoverStrength` determines how strongly we push up.
            // A value slightly less than gravity can create a slow sink/rise effect if desired.
            player.setVelocity(player.velocity.x, (player.velocity.y + hoverStrength).coerceAtMost(0.5), player.velocity.z)
        } else {
            // If somehow on ground while enabled, prevent falling through blocks instantly
            // by not applying a large positive Y velocity. Let natural jump handle it if needed.
            // Or, simply do nothing to Y velocity if on ground.
        }

        // Optional: Very slight vertical control for fine-tuning hover height
        // This can be linked to sneak/spacebar if needed, but omitted for simplicity.
    }

    @Suppress("unused")
    private val movementHandler = handler<MovementInputEvent> { event ->
        if (!ModuleFly.enabled) return@handler

        val player = mc.player ?: return@handler

        // --- MOVEMENT LOGIC ---
        // Calculate desired movement direction based on player's yaw and input.
        // Use event.directionalInput to check state
        val directionalInput = event.directionalInput
        var forward = 0.0
        var strafe = 0.0

        if (directionalInput.forwards) forward++
        if (directionalInput.backwards) forward--
        if (directionalInput.left) strafe++ // Note: LEFT is positive strafe for LB
        if (directionalInput.right) strafe-- // Note: RIGHT is negative strafe for LB

        // If no input, the player just hovers due to tickHandler.
        if (forward == 0.0 && strafe == 0.0) {
            return@handler
        }

        // Normalize diagonal movement speed (so moving forward+left isn't faster)
        val moveSpeedNormalized = if (forward != 0.0 && strafe != 0.0) moveSpeed * 0.91f else moveSpeed
        val yawRad = Math.toRadians(player.yaw.toDouble())

        // Calculate the target velocity vector based on input and orientation.
        val calcMoveX = (-Math.sin(yawRad) * forward + Math.cos(yawRad) * strafe) * moveSpeedNormalized
        val calcMoveZ = (Math.cos(yawRad) * forward + Math.sin(yawRad) * strafe) * moveSpeedNormalized

        val targetVelocity = Vec3d(calcMoveX, player.velocity.y, calcMoveZ)

        // --- SMOOTH VELOCITY APPLICATION ---
        // Instead of instantly setting velocity, lerp (linear interpolate) towards the target.
        // This creates a more "accelerated" feel, similar to the video.
        // `acceleration` factor controls how quickly velocity adjusts to the target.
        val lerpedVelocity = player.velocity.lerp(targetVelocity, acceleration.toDouble())

        // Apply the smoothly adjusted velocity.
        player.setVelocity(lerpedVelocity.x, lerpedVelocity.y, lerpedVelocity.z)

        // Consume the movement input to prevent default walking physics from interfering.
        // This makes the movement purely controlled by our fly logic.
        // Modify the directionalInput object directly.
        event.directionalInput = event.directionalInput.copy(
            forwards = false,
            backwards = false,
            left = false,
            right = false
        )
    }
}
