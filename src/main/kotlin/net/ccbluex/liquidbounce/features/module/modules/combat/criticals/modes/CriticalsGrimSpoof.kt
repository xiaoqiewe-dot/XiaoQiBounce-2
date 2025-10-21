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
package net.ccbluex.liquidbounce.features.module.modules.combat.criticals.modes

import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.AttackEntityEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.combat.criticals.ModuleCriticals
import net.ccbluex.liquidbounce.features.module.modules.combat.criticals.ModuleCriticals.VisualsConfigurable.showCriticals
import net.ccbluex.liquidbounce.features.module.modules.combat.criticals.ModuleCriticals.canDoCriticalHit
import net.ccbluex.liquidbounce.features.module.modules.combat.criticals.ModuleCriticals.modes
import net.ccbluex.liquidbounce.utils.client.MovePacketType
import net.minecraft.entity.LivingEntity
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import kotlin.random.Random

/**
 * CriticalsGrimSpoof - GrimAC friendly packet spoof for critical hits.
 * Sends a tiny pair of position nudges wrapped in legitimate packets
 * to simulate airborne state without breaking motion consistency.
 */
internal object CriticalsGrimSpoof : Choice("GrimSpoof") {

    private val jitter by float("Jitter", 0.00035f, 0.0f..0.0015f, "blocks")
    private val spoofGround by boolean("SpoofGround", true)
    private val resetFallDistance by boolean("ResetFallDistance", true)

    override val parent: ChoiceConfigurable<Choice>
        get() = modes

    private fun sendOffset(offset: Double, onGround: Boolean) {
        network.sendPacket(
            MovePacketType.FULL.generatePacket().apply {
                y += offset
                this.onGround = onGround
            }
        )
    }

    @Suppress("unused")
    private val attackHandler = handler<AttackEntityEvent> { event ->
        val entity = event.entity
        if (event.isCancelled || entity !is LivingEntity) {
            return@handler
        }

        val ignoreSprinting = ModuleCriticals.WhenSprinting.shouldAttemptCritWhileSprinting()
        if (!canDoCriticalHit(true, ignoreSprinting)) {
            return@handler
        }

        val jitterValue = if (jitter > 0f) Random.nextDouble(-jitter.toDouble(), jitter.toDouble()) else 0.0
        val primaryOffset = (0.00125 + jitterValue).coerceIn(3.5E-4, 0.0025)
        val secondaryOffset = (0.00012 + jitterValue / 2.0).coerceIn(1.0E-5, 5.0E-4)

        sendOffset(primaryOffset, false)
        sendOffset(secondaryOffset, false)

        if (spoofGround) {
            network.sendPacket(PlayerMoveC2SPacket.OnGroundOnly(true, player.horizontalCollision))
        }

        if (resetFallDistance) {
            player.fallDistance = 0f
        }

        showCriticals(entity)
    }
}
