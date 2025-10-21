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
package net.ccbluex.liquidbounce.features.module.modules.player

import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.events.QueuePacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.client.PacketQueueManager
import net.ccbluex.liquidbounce.utils.client.PacketQueueManager.Action
import net.ccbluex.liquidbounce.utils.client.PacketQueueManager.positions
import net.ccbluex.liquidbounce.utils.client.notification
import net.ccbluex.liquidbounce.event.tickHandler

/**
 * BlinkGrim
 *
 * A GrimAC-friendly Blink implementation as separate feature.
 * Queues outgoing packets and releases them on disable or after a configurable threshold.
 */
object ModuleBlinkGrim : ClientModule("BlinkGrim", Category.PLAYER) {

    private val autoRelease by boolean("AutoRelease", true)
    private val maxQueued by int("MaxQueued", 120, 20..1000, "packets")

    override fun disable() {
        // Flush queued outgoing packets when the module is disabled
        PacketQueueManager.flush { snapshot -> snapshot.origin == net.ccbluex.liquidbounce.event.events.TransferOrigin.OUTGOING }
    }

    @Suppress("unused")
    private val queueHandler = handler<QueuePacketEvent> { event ->
        if (event.origin == net.ccbluex.liquidbounce.event.events.TransferOrigin.OUTGOING) {
            event.action = Action.QUEUE
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (autoRelease && positions.count() >= maxQueued) {
            notification("BlinkGrim", "Auto releasing queued packets", NotificationEvent.Severity.INFO)
            PacketQueueManager.flush { snapshot -> snapshot.origin == net.ccbluex.liquidbounce.event.events.TransferOrigin.OUTGOING }
        }
    }
}
