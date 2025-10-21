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

/**
 * CriticalsGrimBypass - A criticals mode specifically designed to bypass GrimAC.
 * This mode attempts to mimic natural critical hit behavior to avoid detection.
 */
object CriticalsGrimBypass : Choice("GrimBypass") {

    // --- NO CONFIGURABLES ---

    override val parent: ChoiceConfigurable<Choice>
        get() = modes

    @Suppress("unused")
    private val attackHandler = handler<AttackEntityEvent> { event ->
        if (event.isCancelled || event.entity !is LivingEntity) {
            return@handler
        }

        val ignoreSprinting = ModuleCriticals.WhenSprinting.shouldAttemptCritWhileSprinting()

        if (!canDoCriticalHit(true, ignoreSprinting)) {
            return@handler
        }

        // --- GRIM-BYPASS CRITICAL LOGIC ---
        // GrimAC primarily detects criticals based on:
        // 1. Player's `onGround` status at the moment of attack.
        // 2. The exact movement packet sequence that leads to a critical.
        // 3. Consistency with the player's physics state.

        // To bypass Grim:
        // - Ensure the player is in a valid state for a crit (onGround, not falling too fast).
        // - Send a minimal, realistic-looking packet sequence.
        // - Avoid sending multiple large Y-offsets that would look suspicious.
        // - Send a packet that makes the player appear to be in a state where a crit can occur.

        // Approach: Send a very small, precise movement packet that makes the server think
        // the player just fell a tiny bit and hit the ground, which is a valid condition for a crit.
        // This mimics a "falling crit" but with minimal visible change.

        // Send a very small downward movement packet to simulate a slight fall
        // This is less suspicious than large offsets
        val packet = MovePacketType.FULL.generatePacket().apply {
            this.y += 0.001 // Very small downward shift
            // Keep onGround = false to indicate the player was in the air
            // This is key to making Grim think a crit happened due to falling
            this.onGround = false
        }
        network.sendPacket(packet)

        // Show critical particles
        showCriticals(event.entity)

        // --- END GRIM-BYPASS CRITICAL LOGIC ---
    }

    // --- NO EXTRA FUNCTIONS NEEDED ---
}
