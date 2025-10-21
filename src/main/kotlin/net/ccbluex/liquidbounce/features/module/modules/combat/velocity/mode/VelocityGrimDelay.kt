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
package net.ccbluex.liquidbounce.features.module.modules.combat.velocity.mode

import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.combat.velocity.ModuleVelocity
import net.minecraft.network.listener.ClientPlayPacketListener
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * VelocityGrimDelay - Queues incoming velocity packets and replays them
 * after a configurable delay to mimic legitimate network latency,
 * preventing GrimAC from flagging sudden knockback suppression.
 */
internal object VelocityGrimDelay : VelocityMode("GrimDelay") {

    private val delayTicks by intRange("DelayTicks", 3..5, 0..20, "ticks")
    private val maxQueue by int("MaxQueue", 6, 1..12)
    private val flushOnDisable by boolean("FlushOnDisable", true)

    private data class QueuedPacket(
        val packet: Packet<ClientPlayPacketListener>,
        val executeAt: Long
    )

    private val queue = ConcurrentLinkedQueue<QueuedPacket>()

    override fun enable() {
        queue.clear()
    }

    override fun disable() {
        if (flushOnDisable) {
            flushQueue()
        } else {
            queue.clear()
        }
    }

    private fun flushQueue() {
        while (true) {
            val queued = queue.poll() ?: break
            dispatchPacket(queued.packet)
        }
    }

    private fun dispatchPacket(packet: Packet<ClientPlayPacketListener>) {
        val packetEvent = PacketEvent(TransferOrigin.INCOMING, packet, false,)
        EventManager.callEvent(packetEvent)
        if (!packetEvent.isCancelled) {
            (packetEvent.packet as Packet<ClientPlayPacketListener>).apply(network)
        }
    }

    private fun schedule(packet: Packet<ClientPlayPacketListener>) {
        while (queue.size >= maxQueue && queue.poll() != null) {
            // Drop oldest packets to keep delay believable
        }

        val ticks = delayTicks.random()
        val executeAt = System.currentTimeMillis() + (ticks * 50L)
        queue.add(QueuedPacket(packet, executeAt))
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        if (!event.original) {
            return@handler
        }

        when (val packet = event.packet) {
            is EntityVelocityUpdateS2CPacket -> {
                if (packet.entityId != player.id) {
                    return@handler
                }

                event.cancelEvent()
                schedule(packet as Packet<ClientPlayPacketListener>)
                ModuleVelocity.pause = 0
            }

            is ExplosionS2CPacket -> {
                event.cancelEvent()
                schedule(packet as Packet<ClientPlayPacketListener>)
            }
        }
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        val now = System.currentTimeMillis()

        while (true) {
            val queued = queue.peek() ?: break
            if (queued.executeAt > now) {
                break
            }

            queue.poll()?.let { dispatchPacket(it.packet) }
        }
    }
}
