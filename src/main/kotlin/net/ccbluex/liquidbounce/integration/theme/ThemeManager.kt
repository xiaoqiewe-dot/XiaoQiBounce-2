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
 *
 */
package net.ccbluex.liquidbounce.integration.theme

import com.google.gson.JsonArray
import com.google.gson.annotations.SerializedName
import com.mojang.blaze3d.systems.RenderSystem
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.config.gson.util.decode
import net.ccbluex.liquidbounce.config.types.nesting.Configurable
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleClickGui
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleHud
import net.ccbluex.liquidbounce.integration.IntegrationListener
import net.ccbluex.liquidbounce.integration.VirtualScreenType
import net.ccbluex.liquidbounce.integration.backend.BrowserBackendManager
import net.ccbluex.liquidbounce.integration.backend.browser.Browser
import net.ccbluex.liquidbounce.integration.backend.browser.BrowserSettings
import net.ccbluex.liquidbounce.integration.backend.input.InputAcceptor
import net.ccbluex.liquidbounce.integration.interop.ClientInteropServer
import net.ccbluex.liquidbounce.integration.theme.component.Component
import net.ccbluex.liquidbounce.integration.theme.component.ComponentOverlay
import net.ccbluex.liquidbounce.integration.theme.component.ComponentType
import net.ccbluex.liquidbounce.render.shader.CanvasShader
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.io.extractZip
import net.ccbluex.liquidbounce.utils.io.resource
import net.ccbluex.liquidbounce.utils.io.resourceToString
import net.ccbluex.liquidbounce.utils.math.Vec2i
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ChatScreen
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.util.Identifier
import java.io.Closeable
import java.io.File

object ThemeManager : Configurable("theme") {

    internal val themesFolder = File(ConfigSystem.rootFolder, "themes")
    internal val defaultTheme = Theme.defaults()

    var shaderEnabled by boolean("Shader", false)
        .onChange { enabled ->
            if (enabled) {
                RenderSystem.recordRenderCall {
                    activeTheme.compileShader()
                    defaultTheme.compileShader()
                }
            }

            return@onChange enabled
        }

    var activeTheme = defaultTheme
        set(value) {
            if (!value.exists) {
                logger.warn("Unable to set theme to ${value.name}, theme does not exist")
                return
            }

            if (field != defaultTheme) {
                activeTheme.close()
            }

            field = value

            // Update components
            ComponentOverlay.insertDefaultComponents()

            // Update integration browser
            IntegrationListener.update()
            ModuleHud.reopen()
            ModuleClickGui.reload(true)
        }

    private val takesInputHandler = InputAcceptor { mc.currentScreen != null && mc.currentScreen !is ChatScreen }

    init {
        ConfigSystem.root(this)
    }

    /**
     * Open [Browser] with the given [VirtualScreenType] and mark as static if [markAsStatic] is true.
     * This tab will be locked to 60 FPS since it is not input aware.
     */
    fun openImmediate(
        virtualScreenType: VirtualScreenType? = null,
        markAsStatic: Boolean = false,
        settings: BrowserSettings
    ): Browser =
        BrowserBackendManager.browserBackend.createBrowser(
            route(virtualScreenType, markAsStatic).url,
            settings = settings
        )

    /**
     * Open [Browser] with the given [VirtualScreenType] and mark as static if [markAsStatic] is true.
     * This tab will be locked to the highest refresh rate since it is input aware.
     */
    fun openInputAwareImmediate(
        virtualScreenType: VirtualScreenType? = null,
        markAsStatic: Boolean = false,
        settings: BrowserSettings,
        priority: Short = 10,
        inputAcceptor: InputAcceptor = takesInputHandler
    ): Browser = BrowserBackendManager.browserBackend.createBrowser(
        route(virtualScreenType, markAsStatic).url,
        settings = settings,
        priority = priority,
        inputAcceptor = inputAcceptor
    )

    fun updateImmediate(
        browser: Browser?,
        virtualScreenType: VirtualScreenType? = null,
        markAsStatic: Boolean = false
    ) {
        browser?.url = route(virtualScreenType, markAsStatic).url
    }

    fun route(virtualScreenType: VirtualScreenType? = null, markAsStatic: Boolean = false): Route {
        val theme = if (virtualScreenType == null || activeTheme.doesAccept(virtualScreenType.routeName)) {
            activeTheme
        } else if (defaultTheme.doesAccept(virtualScreenType.routeName)) {
            defaultTheme
        } else {
            error("No theme supports the route ${virtualScreenType.routeName}")
        }

        return Route(
            theme,
            theme.getUrl(virtualScreenType?.routeName, markAsStatic)
        )
    }

    fun initializeBackground() {
        // Load background image of active theme and fallback to default theme if not available
        if (!activeTheme.loadBackgroundImage()) {
            defaultTheme.loadBackgroundImage()
        }

        // Compile shader of active theme and fallback to default theme if not available
        if (shaderEnabled && !activeTheme.compileShader()) {
            defaultTheme.compileShader()
        }
    }

    fun drawBackground(context: DrawContext, width: Int, height: Int, mousePos: Vec2i, delta: Float): Boolean {
        if (shaderEnabled) {
            val shader = activeTheme.compiledShaderBackground ?: defaultTheme.compiledShaderBackground

            if (shader != null) {
                shader.draw(mousePos.x, mousePos.y, delta)
                return true
            }
        }

        val image = activeTheme.loadedBackgroundImage ?: defaultTheme.loadedBackgroundImage
        if (image != null) {
            context.drawTexture(
                RenderLayer::getGuiTextured,
                image,
                0,
                0,
                0f,
                0f,
                width,
                height,
                width,
                height
            )
            return true
        }

        return false
    }

    fun chooseTheme(name: String) {
        activeTheme = Theme(name)
    }

    fun themes() = themesFolder.listFiles()?.filter { it.isDirectory }?.mapNotNull { it.name } ?: emptyList()

    data class Route(val theme: Theme, val url: String)

}

class Theme(val name: String) : Closeable {

    private val folder = File(ThemeManager.themesFolder, name)

    init {
        if (!exists) {
            error("Theme $name does not exist")
        }
    }

    private val metadata: ThemeMetadata = run {
        val metadataFile = File(folder, "metadata.json")
        if (!metadataFile.exists()) {
            error("Theme $name does not contain a metadata file")
        }

        decode<ThemeMetadata>(metadataFile.inputStream())
    }

    val exists: Boolean
        get() = folder.exists()

    private val url: String
        get() = "${ClientInteropServer.url}/$name/#/"

    private val backgroundShader: File
        get() = File(folder, "background.frag")
    private val backgroundImage: File
        get() = File(folder, "background.png")
    var compiledShaderBackground: CanvasShader? = null
        private set
    var loadedBackgroundImage: Identifier? = null
        private set

    fun compileShader(): Boolean {
        if (compiledShaderBackground != null) {
            return true
        }

        readShaderBackground()?.let { shaderBackground ->
            compiledShaderBackground = CanvasShader(resourceToString("/resources/liquidbounce/shaders/vertex.vert"),
                shaderBackground)
            logger.info("Compiled background shader for theme $name")
            return true
        }
        return false
    }

    private fun readShaderBackground() = backgroundShader.takeIf { it.exists() }?.readText()
    private fun readBackgroundImage() = backgroundImage.takeIf { it.exists() }
        ?.inputStream()?.use { NativeImage.read(it) }

    fun loadBackgroundImage(): Boolean {
        if (loadedBackgroundImage != null) {
            return true
        }

        val image = NativeImageBackedTexture(readBackgroundImage() ?: return false)
        loadedBackgroundImage = Identifier.of("liquidbounce", "theme-bg-${name.lowercase()}")
        mc.textureManager.registerTexture(loadedBackgroundImage, image)
        logger.info("Loaded background image for theme $name")
        return true
    }

    /**
     * Get the URL to the given page name in the theme.
     */
    fun getUrl(name: String? = null, markAsStatic: Boolean = false) = "$url${name.orEmpty()}".let {
        if (markAsStatic) {
            "$it?static"
        } else {
            it
        }
    }

    fun doesAccept(name: String?) = doesSupport(name) || doesOverlay(name)

    fun doesSupport(name: String?) = name != null && metadata.supports.contains(name)

    fun doesOverlay(name: String?) = name != null && metadata.overlays.contains(name)

    fun parseComponents(): MutableList<Component> {
        val themeComponent = metadata.rawComponents
            .map { it.asJsonObject }
            .associateBy { it["name"].asString!! }

        val componentList = mutableListOf<Component>()

        for ((name, obj) in themeComponent) {
            runCatching {
                val componentType = ComponentType.byName(name) ?: error("Unknown component type: $name")
                val component = componentType.createComponent()

                runCatching {
                    ConfigSystem.deserializeConfigurable(component, obj)
                }.onFailure {
                    logger.error("Failed to deserialize component $name", it)
                }

                componentList.add(component)
            }.onFailure {
                logger.error("Failed to create component $name", it)
            }
        }

        return componentList
    }

    override fun close() {
        mc.textureManager.destroyTexture(loadedBackgroundImage)
    }

    companion object {

        fun defaults() = runCatching {
            val folder = ThemeManager.themesFolder.resolve("default")

            // Try to load from resources if available
            runCatching {
                val stream = resource("/resources/liquidbounce/default_theme.zip")
                if (folder.exists()) {
                    folder.deleteRecursively()
                }
                extractZip(stream, folder)
                folder.deleteOnExit()
            }.onFailure {
                logger.warn("Default theme resource not found, creating minimal theme")
                // Create minimal theme structure if resource is missing
                if (!folder.exists()) {
                    folder.mkdirs()
                    // Create minimal metadata.json
                    File(folder, "metadata.json").writeText("""
                        {
                            "name": "default",
                            "author": "LiquidBounce",
                            "version": "1.0.0",
                            "supports": [],
                            "overlays": [],
                            "components": []
                        }
                    """.trimIndent())
                }
            }

            Theme("default")
        }.onFailure {
            logger.error("Unable to create default theme", it)
        }.onSuccess {
            logger.info("Successfully loaded default theme")
        }.getOrThrow()

    }

}

data class ThemeMetadata(
    val name: String,
    val author: String,
    val version: String,
    val supports: List<String>,
    val overlays: List<String>,
    @SerializedName("components")
    val rawComponents: JsonArray
)
