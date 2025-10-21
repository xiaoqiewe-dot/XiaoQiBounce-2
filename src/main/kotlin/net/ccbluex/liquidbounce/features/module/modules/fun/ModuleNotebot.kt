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
package net.ccbluex.liquidbounce.features.module.modules.`fun`

import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.client.chat
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents

/**
 * Notebot
 *
 * Simple notebot utility that can play a repeating scale locally.
 * This is a minimal implementation to provide the feature entry point.
 */
object ModuleNotebot : ClientModule("Notebot", Category.FUN) {

    private val enabledScale by boolean("PlayScale", false)
    private val tempo by int("Tempo", 6, 1..20, "ticks")

    private var tickCounter = 0
    private var noteIndex = 0
    private val scalePitches = floatArrayOf(
        0.5f, 0.53f, 0.56f, 0.6f, 0.63f, 0.67f, 0.7f, 0.75f
    )

    override fun enable() {
        tickCounter = 0
        noteIndex = 0
        chat("Notebot enabled")
    }

    override fun disable() {
        chat("Notebot disabled")
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        if (!enabledScale) return@handler
        if (++tickCounter % tempo != 0) return@handler

        val pitch = scalePitches[noteIndex % scalePitches.size]
        world.playSound(player, player.blockPos, SoundEvents.BLOCK_NOTE_BLOCK_HARP.value(), SoundCategory.PLAYERS, 1.0f, pitch)
        noteIndex++
    }
}
