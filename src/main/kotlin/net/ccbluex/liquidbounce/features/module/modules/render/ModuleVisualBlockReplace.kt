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
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.config.types.NamedChoice
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.client.Chronometer
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import java.util.concurrent.ConcurrentHashMap

/**
 * ModuleVisualBlockReplace - Replaces the block under the player's feet with a visual effect.
 * Allows cycling through multiple blocks for a dynamic appearance.
 * Does not send any packets to the server.
 * Only generates on solid-enough blocks, not in the air, and restores original blocks when disappearing.
 */
object ModuleVisualBlockReplace : ClientModule("VisualBlockReplace", Category.RENDER) {

    // --- CONFIGURABLES ---
    /** List of block identifiers to cycle through (e.g., "minecraft:stone", "minecraft:dirt") */
    private val blockList by textList(
        "BlockList",
        mutableListOf(
            "minecraft:stone",
            "minecraft:dirt",
            "minecraft:grass_block",
            "minecraft:cobblestone",
            "minecraft:glass" // Example: Add glass to the list
        )
    )

    /** List of block identifiers that should NOT be replaced (e.g., "minecraft:water", "minecraft:lava") */
    private val noReplaceList by textList(
        "NoReplaceList",
        mutableListOf(
            "minecraft:water",
            "minecraft:lava",
            "minecraft:air",
            "minecraft:void_air",
            "minecraft:cave_air"
            // Add more blocks you don't want to be replaced
        )
    )

    /** Delay between block changes in ticks */
    private val cycleDelay by int("CycleDelay", 10, 1..100, "ticks")

    /** Whether to enable the visual replacement */
    private val visualEnabled by boolean("VisualEnabled", true)

    /** Maximum distance from the placed block to the player before it disappears */
    private val disappearDistance by float("DisappearDistance", 10f, 1f..100f, "blocks")
    // --- END CONFIGURABLES ---

    /** Tracks the real block state under the player */
    // --- FIX 1: Use a map to store original states for multiple blocks ---
    private val originalBlockStates = ConcurrentHashMap<BlockPos, BlockState>()

    /** Tracks the fake block state currently being rendered */
    private var fakeBlockState: BlockState? = null

    /** Position of the block under the player's feet */
    private var targetPos: BlockPos? = null

    /** Timer to control block cycling */
    private val cycleTimer = Chronometer()

    /** Index of the current block in the list */
    private var currentIndex = 0

    /** Cache for block state lookups to improve performance */
    private val blockStateCache = ConcurrentHashMap<String, BlockState>()

    /** Set of currently placed fake blocks to manage removal */
    // --- FIX 2: Track placed blocks for removal ---
    private val placedBlocks = mutableSetOf<BlockPos>()

    override fun enable() {
        reset()
    }

    override fun disable() {
        reset()
        restoreAllBlocks()
    }

    private fun reset() {
        originalBlockStates.clear()
        fakeBlockState = null
        targetPos = null
        currentIndex = 0
        cycleTimer.reset()
        blockStateCache.clear()
        placedBlocks.clear()
    }

    /**
     * Restores all real block states in the client world
     */
    private fun restoreAllBlocks() {
        // --- FIX 3: Restore original block states instead of air ---
        originalBlockStates.forEach { (pos, originalState) ->
            val world = mc.world ?: return@forEach
            world.setBlockState(pos, originalState, 3) // Flags 3: Update neighbors and render
        }
        originalBlockStates.clear()
        placedBlocks.clear()
    }

    /**
     * Removes fake blocks that are too far from the player
     */
    private fun removeDistantBlocks() {
        val player = mc.player ?: return
        val world = mc.world ?: return
        val maxDistSq = (disappearDistance * disappearDistance).toDouble()

        val iterator = placedBlocks.iterator()
        while (iterator.hasNext()) {
            val pos = iterator.next()
            val dx = player.x - (pos.x + 0.5)
            val dy = player.y - (pos.y + 0.5)
            val dz = player.z - (pos.z + 0.5)
            val distSq = dx * dx + dy * dy + dz * dz

            if (distSq > maxDistSq) {
                // --- FIX 3: Restore original block state ---
                val originalState = originalBlockStates[pos] ?: Blocks.AIR.defaultState
                world.setBlockState(pos, originalState, 3)
                originalBlockStates.remove(pos)
                iterator.remove()
            }
        }
    }

    /**
     * Gets a BlockState from a block identifier string.
     */
    private fun getBlockStateFromIdentifier(identifier: String): BlockState? {
        return blockStateCache.getOrPut(identifier) {
            val id = Identifier.tryParse(identifier) ?: return@getOrPut Blocks.AIR.defaultState
            try {
                val block = Registries.BLOCK.get(id)
                block.defaultState
            } catch (_: Exception) {
                Blocks.AIR.defaultState
            }
        }
    }

    /**
     * Checks if a block state is suitable for placing a fake block on top of.
     * It should be solid enough to support a player or a block.
     */
    // --- FIX 4: Loosen the check to allow glass and similar blocks ---
    private fun isSolidEnough(state: BlockState): Boolean {
        // A simple and effective check is to see if the block has a collision shape
        // that is not empty. This includes glass, ice, etc.
        return !state.isAir && !state.getCollisionShape(mc.world, BlockPos.ORIGIN).isEmpty
        // Alternatively, you can check material or specific block properties.
        // For example, exclude fluids:
        // return !state.isAir && !state.isLiquid && state.material.isSolid()
    }

    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> { event ->
        if (!enabled || !visualEnabled) return@handler

        val player = mc.player ?: return@handler
        val world = mc.world ?: return@handler

        // --- REMOVE DISTANT BLOCKS ---
        removeDistantBlocks()

        // --- CALCULATE TARGET POSITION ---
        val playerPos = player.blockPos
        val blockUnderFeet = playerPos.down()

        // --- CHECK IF TARGET IS VALID ---
        val blockUnderFeetState = world.getBlockState(blockUnderFeet)
        val blockBelowTarget = blockUnderFeet.down()
        val blockBelowTargetState = world.getBlockState(blockBelowTarget)

        // 1. Must be on a solid-enough block (not air/fluid)
        // 2. Must not be in the air (block below target must be solid)
        // 3. Must not be in the no-replace list
        val isBlockInNoReplaceList = noReplaceList.any { identifier ->
            val noReplaceState = getBlockStateFromIdentifier(identifier)
            noReplaceState != null && blockUnderFeetState.block == noReplaceState.block
        }

        if (!isSolidEnough(blockUnderFeetState) ||
            blockBelowTargetState.isAir ||
            isBlockInNoReplaceList
        ) {
            // Target is invalid, do not place fake block
            targetPos = null
            return@handler
        }

        targetPos = blockUnderFeet

        // --- STORE ORIGINAL BLOCK STATE (if not already stored) ---
        // --- FIX 5: Store original state only once ---
        if (!originalBlockStates.containsKey(targetPos)) {
            originalBlockStates[targetPos!!] = blockUnderFeetState
        }

        // --- CYCLE FAKE BLOCK STATE ---
        if (cycleTimer.hasElapsed((cycleDelay * 50L))) { // Convert ticks to milliseconds
            if (blockList.isNotEmpty()) {
                val blockIdentifier = blockList[currentIndex % blockList.size]
                fakeBlockState = getBlockStateFromIdentifier(blockIdentifier)
                currentIndex = (currentIndex + 1) % blockList.size
            }
            cycleTimer.reset()
        }

        // --- APPLY FAKE BLOCK STATE ---
        val pos = targetPos ?: return@handler
        val fakeState = fakeBlockState ?: return@handler

        // Temporarily set the fake block state in the client world
        // This will only affect rendering, not physics or server state
        world.setBlockState(pos, fakeState, 3) // Flags 3: Update neighbors and render
        placedBlocks.add(pos) // Track the placed block
    }

    /**
     * Enum for block choices (used for config UI if needed)
     */
    private enum class BlockChoice(override val choiceName: String) : NamedChoice {
        STONE("Stone"),
        DIRT("Dirt"),
        GRASS_BLOCK("Grass Block"),
        COBBLESTONE("Cobblestone"),
        GLASS("Glass"),
        // Add more as needed
    }
}
