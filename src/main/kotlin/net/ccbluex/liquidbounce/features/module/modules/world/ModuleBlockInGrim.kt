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
package net.ccbluex.liquidbounce.features.module.modules.world

import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.minecraft.block.SideShapeType
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.minecraft.block.Blocks
import net.minecraft.item.BlockItem
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext

/**
 * BlockInGrim - Automatically builds a block shell around the player to avoid GrimAC detection.
 * Places blocks with a delay and covers a 1x1 or 3x3 area depending on the 'expand' setting.
 * Disables itself after completion.
 */
object ModuleBlockInGrim : ClientModule("BlockInGrim", Category.WORLD) {

    // No configurable options as requested
    private const val delay = 5 // Fixed delay between placements
    private const val expand = true // Fixed to cover a 3x3 area

    private var placeTimer = 0
    private var placedCount = 0
    private var totalBlocksToPlace = 0
    private var targetPositions = mutableListOf<BlockPos>()

/**
 * 启用功能时的初始化方法
 * 重置计时器、计数器和目标位置列表
 * 根据扩展设置计算需要放置方块的目标位置
 */
    override fun enable() {
        super.enable()  // 调用父类的启用方法
        // 初始化计时器和计数器
        placeTimer = 0
        placedCount = 0
        // 清空目标位置列表
        targetPositions.clear()

        // Define target positions based on 'expand' setting
        val centerPos = player.blockPos
        val yLevel = centerPos.y + 1 // Place blocks one level above player's feet

        if (expand) {
            // Cover a 3x3 area around the player (centerPos.x-1 to centerPos.x+1, centerPos.z-1 to centerPos.z+1)
            // Bottom layer (yLevel)
            for (x in -1..1) {
                for (z in -1..1) {
                    targetPositions.add(BlockPos(centerPos.x + x, yLevel, centerPos.z + z))
                }
            }
            // Middle layer (yLevel + 1)
            for (x in -1..1) {
                for (z in -1..1) {
                    targetPositions.add(BlockPos(centerPos.x + x, yLevel + 1, centerPos.z + z))
                }
            }
            // Top layer (yLevel + 2)
            for (x in -1..1) {
                for (z in -1..1) {
                    targetPositions.add(BlockPos(centerPos.x + x, yLevel + 2, centerPos.z + z))
                }
            }
        } else {
            // Cover only the player's current block (centerPos.x, centerPos.z)
            targetPositions.add(BlockPos(centerPos.x, yLevel, centerPos.z))
            targetPositions.add(BlockPos(centerPos.x, yLevel + 1, centerPos.z))
            targetPositions.add(BlockPos(centerPos.x, yLevel + 2, centerPos.z))
        }

        totalBlocksToPlace = targetPositions.size
    }

    /**
     * Finds the first slot in the hotbar containing a block item.
     * @return The slot index (0-8) or -1 if no block is found.
     */
    private fun findBlockInHotbar(): Int {
        for (i in 0..8) {
            val stack = player.inventory.getStack(i)
            if (stack.item is BlockItem) {
                return i
            }
        }
        return -1
    }

    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> {
        if (!enabled) return@handler

        if (placeTimer > 0) {
            placeTimer--
            return@handler
        }

        if (placedCount >= totalBlocksToPlace) {
            // All blocks placed, disable the module
            disable()
            return@handler
        }

        val targetPos = targetPositions[placedCount]

        // Check if target block is already placed
        if (world.getBlockState(targetPos).block != Blocks.AIR) {
            placedCount++
            placeTimer = delay
            return@handler
        }

        // Find a block item in hotbar
        val slot = findBlockInHotbar()
        if (slot == -1) {
            // mc.inGameHud?.chatHud?.addMessage(net.minecraft.text.Text.literal("No blocks found in hotbar!")) // Alternative chat
            player.sendMessage(net.minecraft.text.Text.literal("No blocks found in hotbar!"), false) // Use player.sendMessage
            disable() // Disable if no blocks available
            return@handler
        }

        // Check if player has line of sight to the target block face
        // Using player.getCameraPosVec instead of player.eyesPos
        val raycastResult = world.raycast(
            RaycastContext(
                player.getCameraPosVec(1.0F), // Use getCameraPosVec
                Vec3d.of(targetPos).add(0.5, 0.5, 0.5), // Aim for center of target block
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
            )
        )

        // Prefer placing on the top face of the block below
        val blockBelow = targetPos.down()
        val blockStateBelow = world.getBlockState(blockBelow)

        if (blockStateBelow.isSideSolid(world, blockBelow, Direction.UP, SideShapeType.FULL)) {
            // Place on top of the block below
            val hitResult = BlockHitResult(
                Vec3d.of(targetPos).add(0.5, 1.0, 0.5),
                Direction.UP,
                blockBelow,
                false
            )

            // Switch to the block slot using player.inventory.selectedSlot
            player.inventory.selectedSlot = slot

            // Send the interact packet
            network.sendPacket(PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hitResult, 0))

            // Simulate a small movement packet to mimic natural behavior after placing
            // Ensure horizontalCollision is provided
            network.sendPacket(
                PlayerMoveC2SPacket.Full(
                    player.x,
                    player.y,
                    player.z,
                    player.yaw,
                    player.pitch,
                    player.isOnGround,
                    player.horizontalCollision // Provide horizontalCollision
                )
            )

            placedCount++
            placeTimer = delay
        } else {
            // If no solid block below, try to find a nearby solid face (e.g., side of an adjacent block)
            var placed = false
            for (dir in Direction.entries) {
                if (dir == Direction.UP || dir == Direction.DOWN) continue // Avoid placing on top/bottom if possible

                val neighborPos = targetPos.offset(dir.opposite)
                val neighborState = world.getBlockState(neighborPos)

                if (neighborState.isSideSolid(world, neighborPos, dir, SideShapeType.FULL)) {
                    val hitVec = Vec3d.of(neighborPos).add(0.5, 0.5, 0.5)
                        .add(Vec3d.of(dir.opposite.vector).multiply(0.5)) // Hit point on the face
                    val hitResult = BlockHitResult(
                        hitVec,
                        dir,
                        neighborPos,
                        false
                    )

                    // Switch to the block slot
                    player.inventory.selectedSlot = slot

                    // Send the interact packet
                    network.sendPacket(PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hitResult, 0))

                    // Simulate a small movement packet
                    network.sendPacket(
                        PlayerMoveC2SPacket.Full(
                            player.x,
                            player.y,
                            player.z,
                            player.yaw,
                            player.pitch,
                            player.isOnGround,
                            player.horizontalCollision // Provide horizontalCollision
                        )
                    )

                    placedCount++
                    placeTimer = delay
                    placed = true
                    break
                }
            }
            if (!placed) {
                // If no suitable face is found, skip this block (might happen if surrounded by air)
                placedCount++
                placeTimer = delay // Still increment timer to avoid infinite loop on this block
                player.sendMessage(net.minecraft.text.Text.literal("Skipping block at $targetPos, no solid face found."), false)
            }
        }
    }}
