/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce  )
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
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/  >.
 */
package net.ccbluex.liquidbounce.features.command.commands.module.teleport

import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.CommandException
import net.ccbluex.liquidbounce.features.command.CommandFactory
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder
import net.ccbluex.liquidbounce.features.module.modules.movement.ModuleTeleport
import net.ccbluex.liquidbounce.utils.client.player
import net.minecraft.util.math.Vec3d
import kotlin.math.*

/**
 * Teleport Command
 *
 * Allows you to teleport using absolute, relative (~), and local (^) coordinates.
 *
 * Module: [ModuleTeleport]
 */
object CommandTeleport : CommandFactory {

    /**
     * Parses a coordinate string which can be absolute, relative (~), or local (^).
     *
     * @param input The string representation of the coordinate (e.g., "100", "~", "~5", "^-2").
     * @param current The player's current position on the respective axis.
     * @param yaw The player's yaw angle (in degrees).
     * @param pitch The player's pitch angle (in degrees).
     * @param index The index of the coordinate (0=x, 1=y, 2=z). Used for ^ calculations.
     * @return The parsed absolute coordinate value, or null if parsing fails.
     */
    private fun parseCoordinate(input: String, current: Double, yaw: Float, pitch: Float, index: Int): Double? {
        return if (input.startsWith("~")) {
            // --- RELATIVE COORDINATE (~) ---
            val offsetStr = input.substring(1)
            val offset = if (offsetStr.isEmpty()) 0.0 else offsetStr.toDoubleOrNull() ?: return null
            current + offset
        } else if (input.startsWith("^")) {
            // --- LOCAL COORDINATE (^) ---
            val offsetStr = input.substring(1)
            val offset = if (offsetStr.isEmpty()) 0.0 else offsetStr.toDoubleOrNull() ?: return null

            // --- CALCULATE LOCAL COORDINATE BASIS VECTORS ---
            // Convert angles to radians
            val yawRad = Math.toRadians(yaw.toDouble())
            val pitchRad = Math.toRadians(pitch.toDouble())

            // 1. Forward Vector (where the player is looking)
            val forwardX = -sin(yawRad) * cos(pitchRad)
            val forwardY = -sin(pitchRad)
            val forwardZ = cos(yawRad) * cos(pitchRad)
            val forwardVec = Vec3d(forwardX, forwardY, forwardZ).normalize()

            // 2. Right Vector (perpendicular to forward on the horizontal plane)
            // For a flat world, right is perpendicular to forward and up (0,1,0).
            // We use cross product: right = forward x (0, 1, 0)
            val rightVec = forwardVec.crossProduct(Vec3d(0.0, 1.0, 0.0)).normalize()

            // 3. Up Vector (perpendicular to both forward and right)
            // up = right x forward
            val upVec = rightVec.crossProduct(forwardVec).normalize()

            // --- SELECT THE CORRECT BASIS VECTOR BASED ON AXIS INDEX ---
            // Minecraft's ^ convention (based on common interpretation and testing):
            // ^ on X component -> Right vector
            // ^ on Y component -> Up vector
            // ^ on Z component -> Forward vector (Note: This pushes in the direction of view)
            val basisVector = when (index) {
                0 -> rightVec   // ^ on X -> Right
                1 -> upVec      // ^ on Y -> Up
                2 -> forwardVec // ^ on Z -> Forward
                else -> Vec3d.ZERO
            }

            // --- CALCULATE FINAL POSITION ---
            // Offset the basis vector by the specified amount
            val offsetVector = basisVector.multiply(offset)
            // Add the offset vector to the current position
            val finalPos = Vec3d(current, current, current).add(offsetVector)
            // Return the component corresponding to the axis index
            when (index) {
                0 -> finalPos.x
                1 -> finalPos.y
                2 -> finalPos.z
                else -> current
            }
        } else {
            // --- ABSOLUTE COORDINATE ---
            input.toDoubleOrNull()
        }
    }


    override fun createCommand(): Command {
        return CommandBuilder
            .begin("teleport")
            .alias("tp")
            .requiresIngame()
            .parameter(
                ParameterBuilder
                    .begin<String>("x")
                    .required()
                    .build(),
            )
            .parameter(
                ParameterBuilder
                    .begin<String>("y")
                    .required()
                    .build()
            )
            .parameter(
                ParameterBuilder
                    .begin<String>("z")
                    .required()
                    .build()
            )
            .handler { command, args ->
                val player = player ?: throw CommandException(command.result("notIngame"))

                val xStr = args[0] as String
                val yStr = args[1] as String
                val zStr = args[2] as String

                val yaw = player.yaw
                val pitch = player.pitch
                val posX = player.x
                val posY = player.y
                val posZ = player.z

                // --- PARSE EACH COORDINATE ---
                val x = parseCoordinate(xStr, posX, yaw, pitch, 0)
                if (x == null) {
                    throw CommandException(command.result("invalidCoordinates"))
                }

                val y = parseCoordinate(yStr, posY, yaw, pitch, 1)
                if (y == null) {
                    throw CommandException(command.result("invalidCoordinates"))
                }

                val z = parseCoordinate(zStr, posZ, yaw, pitch, 2)
                if (z == null) {
                    throw CommandException(command.result("invalidCoordinates"))
                }
                // --- END PARSE ---

                // Indicate teleport to the ModuleTeleport
                ModuleTeleport.indicateTeleport(x, y, z)
            }
            .build()
    }
}
