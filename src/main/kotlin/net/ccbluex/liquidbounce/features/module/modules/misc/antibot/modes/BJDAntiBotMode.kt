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
package net.ccbluex.liquidbounce.features.module.modules.misc.antibot.modes

import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.misc.antibot.ModuleAntiBot
import net.ccbluex.liquidbounce.features.module.modules.misc.antibot.ModuleAntiBot.isADuplicate
import net.ccbluex.liquidbounce.features.module.modules.misc.antibot.ModuleAntiBot.isGameProfileUnique
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ArmorItem
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket
import net.minecraft.util.math.Vec3d
import kotlin.math.abs
import java.util.*

/**
 * BJDAntiBotMode - Enhanced detection for BJD-style bots.
 * Adds options to remove bots from client and mark nearby players as bots.
 */
object BJDAntiBotMode : Choice("BJD"), ModuleAntiBot.IAntiBotMode {

    override val parent: ChoiceConfigurable<*>
        get() = ModuleAntiBot.modes

    // --- CONFIGURABLES ---
    /** Configuration options - 更严格的检测 */
    private val strictMode by boolean("StrictMode", true)
    private val detectLowLatency by boolean("DetectLowLatency", true)
    private val detectEmptyProfile by boolean("DetectEmptyProfile", true)
    private val detectArmorPattern by boolean("DetectArmorPattern", true)
    private val detectMovementPattern by boolean("DetectMovement", true)

    /** Whether to remove bots from the client world */
    private val removeBotsFromClient by boolean("RemoveBotsFromClient", true)

    /** Whether to mark players within 1 block as bots */
    private val markNearbyAsBots by boolean("MarkNearbyAsBots", true)
    private val nearbyRange by float("NearbyRange", 1.0f, 0.1f..5.0f, "blocks")
    // --- END CONFIGURABLES ---

    private val suspectList = hashSetOf<UUID>()
    private val botList = hashSetOf<UUID>()

    // Enhanced tracking
    private val playerPositions = hashMapOf<UUID, MutableList<Vec3d>>()
    private val playerJoinTimes = hashMapOf<UUID, Long>()
    private val playerMovementCount = hashMapOf<UUID, Int>()

    override fun enable() {
        reset()
    }

    override fun disable() {
        reset()
    }

    override fun reset() {
        suspectList.clear()
        botList.clear()
        playerPositions.clear()
        playerJoinTimes.clear()
        playerMovementCount.clear()
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        val packet = event.packet

        if (packet is PlayerListS2CPacket) {
            for (entry in packet.playerAdditionEntries) {
                val profile = entry.profile ?: continue
                val currentTime = System.currentTimeMillis()

                // 低延迟检测 - 更严格的阈值
                if (detectLowLatency && entry.latency < 5) {
                    botList.add(entry.profileId)
                    if (removeBotsFromClient) {
                        // Schedule removal from client world
                        scheduleBotRemoval(entry.profileId)
                    }
                    continue
                }

                // 空资料属性检测
                if (detectEmptyProfile && profile.properties.isEmpty) {
                    botList.add(entry.profileId)
                    if (removeBotsFromClient) {
                        scheduleBotRemoval(entry.profileId)
                    }
                    continue
                }

                // 重复资料检测
                if (isADuplicate(profile)) {
                    botList.add(entry.profileId)
                    if (removeBotsFromClient) {
                        scheduleBotRemoval(entry.profileId)
                    }
                    continue
                }

                // 游戏资料唯一性检测
                if (!isGameProfileUnique(profile)) {
                    suspectList.add(entry.profileId)
                    playerJoinTimes[entry.profileId] = currentTime
                    playerPositions[entry.profileId] = mutableListOf()
                    playerMovementCount[entry.profileId] = 0
                    continue
                }

                // 严格模式下的额外检测
                if (strictMode) {
                    // 名字模式检测（常见假人命名模式）
                    if (hasSuspiciousName(profile.name)) {
                        suspectList.add(entry.profileId)
                        playerJoinTimes[entry.profileId] = currentTime
                        continue
                    }
                }
            }
        } else if (packet is PlayerRemoveS2CPacket) {
            for (uuid in packet.profileIds) {
                cleanupPlayerData(uuid)
            }
        }
    }

    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> { event ->
        if (!ModuleAntiBot.enabled) return@handler

        val player = mc.player ?: return@handler
        val world = mc.world ?: return@handler

        // 清理旧数据
        cleanupOldData()

        for (entity in world.players) {
            val uuid = entity.uuid

            // 跳过自己和已确认的假人
            if (entity == player /*|| botList.contains(uuid)*/) { // Check for nearby bots even if already marked
                continue
            }

            // --- NEW: MARK NEARBY PLAYERS AS BOTS ---
            if (markNearbyAsBots) {
                val distance = player.distanceTo(entity)
                if (distance <= nearbyRange) {
                    if (!botList.contains(uuid)) { // Only add if not already marked
                        botList.add(uuid)
                        if (removeBotsFromClient) {
                            scheduleBotRemoval(uuid)
                        }
                    }
                }
            }
            // --- END NEW ---

            // 如果已经被标记为bot，跳过后续检测
            if (botList.contains(uuid)) {
                if (removeBotsFromClient) {
                    scheduleBotRemoval(uuid)
                }
                continue
            }

            // 更新位置跟踪
            updatePositionTracking(entity)

            // 多重检测方法
            if (detectArmorPattern && detectSuspiciousArmor(entity)) {
                botList.add(uuid)
                if (removeBotsFromClient) {
                    scheduleBotRemoval(uuid)
                }
                continue
            }

            if (detectMovementPattern && detectSuspiciousMovement(entity)) {
                botList.add(uuid)
                if (removeBotsFromClient) {
                    scheduleBotRemoval(uuid)
                }
                continue
            }

            // 处理怀疑列表中的玩家
            if (suspectList.contains(uuid)) {
                if (processSuspect(entity)) {
                    botList.add(uuid)
                    suspectList.remove(uuid)
                    if (removeBotsFromClient) {
                        scheduleBotRemoval(uuid)
                    }
                }
            }

            // 严格模式下的额外检测
            if (strictMode) {
                if (detectNoInteraction(entity)) {
                    botList.add(uuid)
                    if (removeBotsFromClient) {
                        scheduleBotRemoval(uuid)
                    }
                    continue
                }
            }
        }
    }

    /**
     * Schedules a bot entity for removal from the client world.
     */
    private fun scheduleBotRemoval(botUuid: UUID) {
        // Use a coroutine or tick handler to safely remove the entity
        // Removing entities directly in a loop can cause ConcurrentModificationException
        tickHandler {
            val world = mc.world ?: return@tickHandler
            val botEntity = world.players.find { it.uuid == botUuid }
            if (botEntity != null) {
                world.removeEntity(botEntity.id, net.minecraft.entity.Entity.RemovalReason.DISCARDED)
            }
        }
    }

    /**
     * 检测可疑名字模式
     */
    private fun hasSuspiciousName(name: String): Boolean {
        // 检测常见假人命名模式
        val suspiciousPatterns = listOf(
            "NPC", "Bot", "假人", "Dummy", "Target", "Practice",
            "Training", "Combat", "PvP", "Arena"
        )

        // 检测纯数字名字
        if (name.matches(Regex("\\d+"))) {
            return true
        }

        // 检测包含可疑关键词
        for (pattern in suspiciousPatterns) {
            if (name.contains(pattern, ignoreCase = true)) {
                return true
            }
        }

        return false
    }

    /**
     * 检测可疑护甲模式
     */
    private fun detectSuspiciousArmor(entity: PlayerEntity): Boolean {
        // 检测全附魔护甲（常见假人装备）
        var enchantedArmorCount = 0

        for (i in 0..3) {
            val stack = entity.inventory.getArmorStack(i)
            if (stack.item is ArmorItem) {
                // 检测附魔
                if (stack.hasEnchantments()) {
                    enchantedArmorCount++
                }
            }
        }

        // 如果穿着全附魔护甲，很可能是假人
        return enchantedArmorCount == 4
    }

    /**
     * 检测可疑移动模式
     */
    private fun detectSuspiciousMovement(entity: PlayerEntity): Boolean {
        val positions = playerPositions[entity.uuid] ?: return false

        if (positions.size < 10) {
            return false
        }

        // 检测完美直线移动
        if (hasPerfectStraightMovement(positions)) {
            return true
        }

        // 检测重复移动模式
        if (hasRepetitiveMovement(positions)) {
            return true
        }

        // 检测不自然停顿
        if (hasUnnaturalPauses(positions)) {
            return true
        }

        return false
    }

    /**
     * 检测完美直线移动
     */
    private fun hasPerfectStraightMovement(positions: List<Vec3d>): Boolean {
        if (positions.size < 3) return false

        var straightCount = 0
        for (i in 2 until positions.size) {
            val dx1 = positions[i].x - positions[i - 1].x
            val dz1 = positions[i].z - positions[i - 1].z
            val dx2 = positions[i - 1].x - positions[i - 2].x
            val dz2 = positions[i - 1].z - positions[i - 2].z

            // 计算方向变化
            val directionChange = abs(dx1 - dx2) + abs(dz1 - dz2)
            if (directionChange < 0.01) { // 几乎没有方向变化
                straightCount++
            }
        }

        // 如果大部分移动都是直线，很可能是假人
        return straightCount > positions.size * 0.7
    }

    /**
     * 检测重复移动模式
     */
    private fun hasRepetitiveMovement(positions: List<Vec3d>): Boolean {
        if (positions.size < 6) return false

        // 检查移动距离是否过于规律
        val distances = mutableListOf<Double>()
        for (i in 1 until positions.size) {
            distances.add(positions[i].distanceTo(positions[i - 1]))
        }

        val avgDistance = distances.average()
        val variance = distances.map { abs(it - avgDistance) }.average()

        // 移动距离变化极小，可能是重复模式
        return variance < 0.05
    }

    /**
     * 检测不自然停顿
     */
    private fun hasUnnaturalPauses(positions: List<Vec3d>): Boolean {
        if (positions.size < 5) return false

        // 检查是否有长时间完全静止
        var stationaryCount = 0
        for (i in 1 until positions.size) {
            if (positions[i].distanceTo(positions[i - 1]) < 0.001) {
                stationaryCount++
            }
        }

        // 如果大部分时间都静止不动，可能是假人
        return stationaryCount > positions.size * 0.8
    }

    /**
     * 检测无交互行为
     */
    private fun detectNoInteraction(entity: PlayerEntity): Boolean {
        val joinTime = playerJoinTimes[entity.uuid] ?: return false
        val currentTime = System.currentTimeMillis()

        // 如果玩家加入超过30秒但没有明显移动或交互
        if (currentTime - joinTime > 30000) {
            val movementCount = playerMovementCount[entity.uuid] ?: 0
            // 几乎没有移动
            if (movementCount < 10) {
                return true
            }
        }

        return false
    }

    /**
     * 处理怀疑列表中的玩家
     */
    private fun processSuspect(entity: PlayerEntity): Boolean {
        var armor: MutableIterable<ItemStack>? = null

        if (!isFullyArmored(entity)) {
            armor = entity.armorItems
        }

        // 如果护甲异常且资料为空，判定为假人
        if ((isFullyArmored(entity) || updatesArmor(entity, armor)) &&
            entity.gameProfile.properties.isEmpty
        ) {
            return true
        }

        return false
    }

    private fun isFullyArmored(entity: PlayerEntity): Boolean {
        return (0..3).all {
            val stack = entity.inventory.getArmorStack(it)
            stack.item is ArmorItem && stack.hasEnchantments()
        }
    }

    private fun updatesArmor(entity: PlayerEntity, prevArmor: MutableIterable<ItemStack>?): Boolean {
        return prevArmor != entity.armorItems
    }

    /**
     * 更新位置跟踪
     */
    private fun updatePositionTracking(entity: PlayerEntity) {
        val uuid = entity.uuid
        val currentPos = entity.pos

        if (!playerPositions.containsKey(uuid)) {
            playerPositions[uuid] = mutableListOf()
        }

        val positions = playerPositions[uuid]!!
        positions.add(currentPos)

        // 限制位置历史大小
        if (positions.size > 30) {
            positions.removeAt(0)
        }

        // 更新移动计数
        if (positions.size > 1) {
            val lastPos = positions[positions.size - 2]
            if (currentPos.distanceTo(lastPos) > 0.1) {
                playerMovementCount[uuid] = playerMovementCount.getOrDefault(uuid, 0) + 1
            }
        }
    }

    /**
     * 清理玩家数据
     */
    private fun cleanupPlayerData(uuid: UUID) {
        suspectList.remove(uuid)
        botList.remove(uuid) // Also remove from bot list
        playerPositions.remove(uuid)
        playerJoinTimes.remove(uuid)
        playerMovementCount.remove(uuid)
    }

    /**
     * 清理旧数据
     */
    private fun cleanupOldData() {
        val currentTime = System.currentTimeMillis()
        val removeThreshold = currentTime - 60000 // 60秒

        val iterator = playerJoinTimes.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value < removeThreshold) {
                cleanupPlayerData(entry.key)
                iterator.remove()
            }
        }
    }

    override fun isBot(entity: PlayerEntity): Boolean {
        return botList.contains(entity.uuid)
    }
}
