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
package net.ccbluex.liquidbounce.features.module.modules.player.nofall.modes

import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.features.module.modules.player.nofall.ModuleNoFall
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.util.hit.HitResult
import net.minecraft.world.RaycastContext
import kotlin.random.Random

/**
 * NoFallGrimBypass - Bypass logic tailored for GrimAC fall detection.
 * Traces the ground ahead of impact and spoofs a gentle landing packet
 * sequence that keeps motion believable while cancelling damage server-side.
 */
internal object NoFallGrimBypass : Choice("GrimBypass"), MinecraftShortcuts {

    private val triggerDistance by float("Trigger", 2.8f, 1.0f..5.0f, "blocks")
    private val rayTraceDistance by float("RayTrace", 3.0f, 1.5f..6.0f, "blocks")
    private val spoofOffset by float("SpoofOffset", 0.0625f, 0.0005f..0.2f, "blocks")
    private val jitter by float("Jitter", 0.002f, 0.0f..0.01f, "blocks")
    private val cooldownTicks by int("Cooldown", 4, 0..20, "ticks")

    override val parent: ChoiceConfigurable<*>
        get() = ModuleNoFall.modes

    private var armed = false
    private var cooldown = 0

    override fun enable() {
        armed = false
        cooldown = 0
    }

    override fun disable() {
        armed = false
        cooldown = 0
    }

    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> {
        if (!ModuleNoFall.enabled) return@handler

        if (cooldown > 0) {
            cooldown--
        }

        if (player.isOnGround || player.isClimbing || player.isTouchingWater || player.hasVehicle()) {
            armed = false
            return@handler
        }

        if (armed) {
            if (player.isOnGround || player.fallDistance <= 0.2f) {
                armed = false
            }
            return@handler
        }

        if (cooldown > 0) {
            return@handler
        }

        if (player.fallDistance < triggerDistance || player.velocity.y >= -0.08) {
            return@handler
        }

        if (!hasGroundBelow(rayTraceDistance.toDouble())) {
            return@handler
        }

        spoofLanding()
        armed = true
        cooldown = cooldownTicks
    }

    private fun spoofLanding() {
        val x = player.x
        val y = player.y
        val z = player.z
        val horizontal = player.horizontalCollision

        val jitterValue = if (jitter > 0f) Random.nextDouble(-jitter.toDouble(), jitter.toDouble()) else 0.0
        val offset = (spoofOffset.toDouble() + jitterValue).coerceIn(0.0005, 0.25)

        network.sendPacket(
            PlayerMoveC2SPacket.PositionAndOnGround(x, y + offset, z, false, horizontal)
        )
        network.sendPacket(
            PlayerMoveC2SPacket.PositionAndOnGround(x, y + offset * 0.35, z, false, horizontal)
        )
        network.sendPacket(PlayerMoveC2SPacket.OnGroundOnly(true, horizontal))

        player.onLanding()
        player.fallDistance = 0f
    }

    private fun hasGroundBelow(distance: Double): Boolean {
        val start = player.pos
        val end = start.subtract(0.0, distance, 0.0)

        val result = world.raycast(
            RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
            )
        )

        return result.type == HitResult.Type.BLOCK
    }
}
