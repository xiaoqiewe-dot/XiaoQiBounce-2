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
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.ModuleFly
import net.minecraft.util.math.Vec3d

/**
 * FlyDashFlight - Enables creative flight and performs a dash on disable.
 * Mimics the behavior described: fly while enabled, dash when disabled.
 */
internal object FlyDashFlight : Choice("DashFlight") {

    override val parent: ChoiceConfigurable<*>
        get() = ModuleFly.modes

    private var wasFlying = false

    override fun enable() {
        val player = mc.player ?: return
        wasFlying = player.abilities.allowFlying
        player.abilities.allowFlying = true
        player.abilities.flying = true // Start flying immediately
        player.sendAbilitiesUpdate()
    }

    override fun disable() {
        val player = mc.player ?: return
        player.abilities.allowFlying = wasFlying
        player.abilities.flying = wasFlying && player.isOnGround // Reset flying state appropriately
        player.sendAbilitiesUpdate()

        // Perform Dash on disable
        val yawRad = Math.toRadians(player.yaw.toDouble())
        val pitchRad = Math.toRadians(player.pitch.toDouble())
        val dashPower = 2.0 // Configurable dash power
        val dashX = -Math.sin(yawRad) * Math.cos(pitchRad) * dashPower
        val dashY = -Math.sin(pitchRad) * dashPower
        val dashZ = Math.cos(yawRad) * Math.cos(pitchRad) * dashPower

        player.setVelocity(dashX, dashY, dashZ)
    }

    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> {
        // Keep abilities updated if needed, though usually not necessary
        // unless another module interferes.
    }
}
