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

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import net.ccbluex.liquidbounce.api.core.withScope
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * ModuleItemImageReplace
 *
 * Lets you visually replace item icons in GUI (hotbar/inventory) with custom images.
 * Images are loaded from a folder on disk and mapped to item identifiers.
 *
 * Notes
 * - This replacement is client-side and visual only.
 * - To keep it safe for GrimAC and others, we do not alter models or network state.
 */
@OptIn(FlowPreview::class)
object ModuleItemImageReplace : ClientModule("ItemImageReplace", Category.RENDER) {

    // Create images directory under the config root
    private val imagesDir: File by lazy {
        File(ConfigSystem.rootFolder, "item-replacements").apply { if (!exists()) mkdirs() }
    }

    // List of mappings written as: "minecraft:diamond_sword=my_sword.png"
    private val mappingsValue = textList("Mappings", mutableListOf())
    private val mappings by mappingsValue

    // Cache: Item -> texture id
    private val itemTextureMap: MutableMap<Item, Identifier> = ConcurrentHashMap()

    // Cache: file path -> identifier to avoid re-registers
    private val fileIdCache: MutableMap<File, Identifier> = ConcurrentHashMap()

    /**
    * Returns Identifier of a custom texture for the provided stack, or null if none mapped.
    */
    fun findCustomTexture(stack: ItemStack?): Identifier? {
        if (!enabled || stack == null || stack.isEmpty) return null
        return itemTextureMap[stack.item]
    }

    override fun enable() {
        // On enable, build mapping from config
        rebuildMappings()
    }

    override fun disable() {
        // Keep textures registered; turning module off simply stops using overrides
        itemTextureMap.clear()
    }

    init {
        // Rebuild mappings when config changes
        withScope {
            mappingsValue.asStateFlow().debounce { 2.seconds }.collectLatest {
                // Wait for client init
                while (mc.player == null) delay(1.seconds)
                rebuildMappings()
            }
        }
    }

    private fun rebuildMappings() {
        itemTextureMap.clear()
        val list = mappings.toList()
        list.forEach { line ->
            val parts = line.split('=')
            if (parts.size != 2) return@forEach
            val itemIdStr = parts[0].trim()
            val fileName = parts[1].trim()

            val itemId = Identifier.tryParse(itemIdStr) ?: return@forEach
            val item = Registries.ITEM.get(itemId)
            if (item.defaultStack.isEmpty) {
                return@forEach // skip invalid lookup
            }

            val file = File(imagesDir, fileName)
            val tex = registerOrGet(file) ?: return@forEach
            itemTextureMap[item] = tex
        }
    }

    private fun registerOrGet(file: File): Identifier? {
        if (!file.exists() || !file.isFile) return null
        fileIdCache[file]?.let { return it }
        return runCatching {
            val image = file.inputStream().use { NativeImage.read(it) }
            val id = Identifier.of("liquidbounce", "custom-item-" + file.nameWithoutExtension.lowercase().replace("[^a-z0-9_\\-]".toRegex(), "_") + "-" + System.currentTimeMillis().toString(36))
            mc.textureManager.registerTexture(id, NativeImageBackedTexture(image))
            fileIdCache[file] = id
            id
        }.getOrNull()
    }
}
