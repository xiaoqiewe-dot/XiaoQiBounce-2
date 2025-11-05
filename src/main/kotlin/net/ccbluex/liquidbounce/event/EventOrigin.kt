package net.ccbluex.liquidbounce.event

import net.ccbluex.liquidbounce.event.events.PacketEvent

@Suppress("EmptyClassBlock", "UnusedPrivateProperty")
class EventOrigin(private val handler: (net.ccbluex.liquidbounce.event.events.PacketEvent) -> Unit)
