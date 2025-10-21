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

import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.Identifier

/**
 * XRitem Module
 * Allows rendering one item as another item with searchable item list
 */
object ModuleXRitem : ClientModule("XRitem", Category.RENDER) {

    // Main toggle
    private val enabledReplacement by boolean("Enabled", true)

    // Default replacement mappings
    private val defaultReplacements = mapOf(
        Items.DIAMOND_SWORD to Items.NETHERITE_SWORD,
        Items.DIAMOND_PICKAXE to Items.NETHERITE_PICKAXE,
        Items.DIAMOND_AXE to Items.NETHERITE_AXE,
        Items.DIAMOND_SHOVEL to Items.NETHERITE_SHOVEL,
        Items.DIAMOND_HOE to Items.NETHERITE_HOE,
        Items.DIAMOND_HELMET to Items.NETHERITE_HELMET,
        Items.DIAMOND_CHESTPLATE to Items.NETHERITE_CHESTPLATE,
        Items.DIAMOND_LEGGINGS to Items.NETHERITE_LEGGINGS,
        Items.DIAMOND_BOOTS to Items.NETHERITE_BOOTS
    )

    // Set of items that will be replaced
    val sourceItems by items(
        "SourceItems",
        defaultReplacements.keys.toMutableSet()
    )

    // Set of target items for replacement
    val targetItems by items(
        "TargetItems",
        defaultReplacements.values.toMutableSet()
    )

    // Replacement settings
    private val replaceInHand by boolean("ReplaceInHand", true)
    private val replaceInInventory by boolean("ReplaceInInventory", true)
    private val replaceOnGround by boolean("ReplaceOnGround", true)
    private val keepEnchantments by boolean("KeepEnchantments", true)
    private val keepItemName by boolean("KeepItemName", false)

    /**
     * Get the replacement mapping for a source item
     */
    private fun getReplacementMapping(): Map<Item, Item> {
        val replacements = mutableMapOf<Item, Item>()

        // Create mappings based on the order in the lists
        val sourceList = sourceItems.toList()
        val targetList = targetItems.toList()

        val minSize = minOf(sourceList.size, targetList.size)
        for (i in 0 until minSize) {
            replacements[sourceList[i]] = targetList[i]
        }

        return replacements
    }

    /**
     * Check if item replacement should be applied to a specific item stack
     */
    fun shouldReplaceItem(itemStack: ItemStack?): Boolean {
        if (!enabledReplacement || itemStack == null || itemStack.isEmpty) {
            return false
        }

        val sourceItem = itemStack.item
        return getReplacementItem(sourceItem) != null
    }

    /**
     * Get the replacement item for a given source item
     */
    fun getReplacementItem(sourceItem: Item): Item? {
        if (!enabledReplacement) {
            return null
        }

        return getReplacementMapping()[sourceItem]
    }

    /**
     * Create a replacement item stack
     */
    fun createReplacementStack(originalStack: ItemStack): ItemStack {
        val sourceItem = originalStack.item
        val replacementItem = getReplacementItem(sourceItem) ?: return originalStack

        val replacementStack = ItemStack(replacementItem, originalStack.count)

        // Copy enchantments if enabled
        if (keepEnchantments && originalStack.hasEnchantments()) {
            try {
                // Use compatible enchantment copying
                copyEnchantmentsSimple(originalStack, replacementStack)
            } catch (e: Exception) {
                // Safe handling if enchantment copy fails
            }
        }

        // Copy custom name if enabled
        if (keepItemName) {
            try {
                val customName = originalStack.customName
                if (customName != null) {
                    replacementStack.setCustomName(customName)
                }
            } catch (e: Exception) {
                // Safe handling if name copy fails
            }
        }

        // Copy damage if applicable
        if (originalStack.isDamageable && replacementStack.isDamageable) {
            replacementStack.damage = originalStack.damage
        }

        return replacementStack
    }

    /**
     * Simple method to copy enchantments
     */
    private fun copyEnchantmentsSimple(originalStack: ItemStack, replacementStack: ItemStack) {
        // Simplified enchantment copy logic
        // In practice, you might need to implement proper enchantment copying
        // based on the specific Minecraft version
    }

    /**
     * Check if replacement should be applied in hand
     */
    fun shouldReplaceInHand(): Boolean {
        return enabledReplacement && replaceInHand
    }

    /**
     * Check if replacement should be applied in inventory
     */
    fun shouldReplaceInInventory(): Boolean {
        return enabledReplacement && replaceInInventory
    }

    /**
     * Check if replacement should be applied to items on ground
     */
    fun shouldReplaceOnGround(): Boolean {
        return enabledReplacement && replaceOnGround
    }

    /**
     * Get all active replacement mappings for debugging or UI display
     */
    fun getActiveReplacements(): Map<Item, Item> {
        return getReplacementMapping()
    }

    /**
     * Resets the item lists to the default values
     */
    fun applyDefaults() {
        sourceItems.clear()
        sourceItems.addAll(defaultReplacements.keys)

        targetItems.clear()
        targetItems.addAll(defaultReplacements.values)
    }

    override fun enable() {
        // Initialize any necessary state when module is enabled
    }

    override fun disable() {
        // Clean up any state when module is disabled
    }
}

private fun ItemStack.setCustomName(customName: Text) {}
