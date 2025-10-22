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
@file:OptIn(FlowPreview::class)

package net.ccbluex.liquidbounce.features.module.modules.render

import com.mojang.authlib.GameProfile
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import net.ccbluex.liquidbounce.api.core.withScope
import net.ccbluex.liquidbounce.authlib.utils.generateOfflinePlayerUuid
import net.ccbluex.liquidbounce.authlib.yggdrasil.GameProfileRepository
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.minecraft.client.network.PlayerListEntry
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.client.util.SkinTextures
import net.minecraft.util.Identifier
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier
import kotlin.time.Duration.Companion.seconds

object ModuleSkinChanger : ClientModule("SkinChanger", Category.RENDER) {

    // Existing: change own skin by Mojang username
    private val username = text("Username", "LiquidBounce")
        .apply(::tagBy)

    // New: apply to target (self or other) and optionally from local file
    private val targetUsername = text("TargetUsername", "")
    private val useLocalFile = boolean("UseLocalFile", false)
    private val skinFileName = text("SkinFile", "")

    private val skinsDir: File by lazy {
        File(ConfigSystem.rootFolder, "skins").apply { if (!exists()) mkdirs() }
    }

    // Per-player overrides. Keyed by UUID.
    private val overrides: MutableMap<UUID, Supplier<SkinTextures>> = ConcurrentHashMap()

    init {
        withScope {
            // Keep existing behavior for own Mojang-username driven skin
            username.asStateFlow().debounce { 2.seconds }.collectLatest { username ->
                while (mc.player == null) { delay(1.seconds) }
                skinTextures = textureSupplier(username)
            }
        }
        withScope {
            // React to local file changes or target changes
            suspend fun applyOverride() {
                while (mc.player == null) { delay(1.seconds) }
                val target = targetUsername.get().trim()
                val useFile = useLocalFile.get()
                val fileName = skinFileName.get().trim()

                if (useFile && fileName.isNotEmpty()) {
                    val file = File(skinsDir, fileName)
                    createSupplierFromFile(file)?.let { supplier ->
                        val uuid = resolveUuid(target) ?: mc.player!!.uuid
                        overrides[uuid] = supplier
                    }
                }
            }

            // Debounce all three inputs
            targetUsername.asStateFlow().debounce { 2.seconds }.collectLatest { _ -> applyOverride() }
        }
        withScope {
            useLocalFile.asStateFlow().debounce { 1.seconds }.collectLatest { useFile ->
                // On toggle, attempt to apply current selection
                while (mc.player == null) { delay(1.seconds) }
                val target = targetUsername.get().trim()
                val fileName = skinFileName.get().trim()
                if (useFile && fileName.isNotEmpty()) {
                    val file = File(skinsDir, fileName)
                    createSupplierFromFile(file)?.let { supplier ->
                        val uuid = resolveUuid(target) ?: mc.player!!.uuid
                        overrides[uuid] = supplier
                    }
                }
            }
        }
        withScope {
            skinFileName.asStateFlow().debounce { 2.seconds }.collectLatest { fileName ->
                // Update mapping when filename changes
                while (mc.player == null) { delay(1.seconds) }
                if (!useLocalFile.get()) return@collectLatest
                val target = targetUsername.get().trim()
                val file = File(skinsDir, fileName.trim())
                createSupplierFromFile(file)?.let { supplier ->
                    val uuid = resolveUuid(target) ?: mc.player!!.uuid
                    overrides[uuid] = supplier
                }
            }
        }
    }

    @Volatile
    var skinTextures: Supplier<SkinTextures>? = null
        private set

    /**
     * Returns an override supplier for the given UUID, if one is configured.
     * If the UUID matches the local player, returns [skinTextures] as fallback.
     */
    fun getSkinTexturesFor(uuid: UUID): Supplier<SkinTextures>? {
        overrides[uuid]?.let { return it }
        return if (mc.player?.uuid == uuid) skinTextures else null
    }

    private fun textureSupplier(username: String): Supplier<SkinTextures> {
        val uuid = GameProfileRepository().fetchUuidByUsername(username)
            ?: generateOfflinePlayerUuid(username)
        val profile = mc.sessionService.fetchProfile(uuid, false)?.profile
            ?: GameProfile(uuid, username)

        return PlayerListEntry.texturesSupplier(profile)
    }

    /**
     * Create a SkinTextures supplier from a local skin image (PNG).
     */
    private fun createSupplierFromFile(file: File): Supplier<SkinTextures>? {
        if (!file.exists() || !file.isFile) return null
        return runCatching {
            val image = file.inputStream().use { NativeImage.read(it) }
            val id = Identifier.of("liquidbounce", "custom-skin-" + System.currentTimeMillis().toString(36))
            mc.textureManager.registerTexture(id, NativeImageBackedTexture(image))
            Supplier {
                // Use WIDE as a sane default; most skins will render fine. Advanced detection is out-of-scope here.
                SkinTextures(id, "", null, null, SkinTextures.Model.WIDE, true)
            }
        }.getOrNull()
    }

    private fun resolveUuid(name: String): UUID? {
        if (name.isBlank()) return null
        // Prefer an online player match
        world.players.firstOrNull { it.gameProfile.name.equals(name, true) }?.let { return it.uuid }
        // Try Mojang lookup, otherwise offline UUID
        return GameProfileRepository().fetchUuidByUsername(name) ?: generateOfflinePlayerUuid(name)
    }
}
