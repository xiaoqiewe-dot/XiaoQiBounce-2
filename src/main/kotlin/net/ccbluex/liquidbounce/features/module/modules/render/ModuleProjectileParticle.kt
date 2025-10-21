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
import net.ccbluex.liquidbounce.utils.client.logger
import net.minecraft.entity.Entity
import net.minecraft.entity.projectile.*
import net.minecraft.entity.projectile.thrown.EggEntity
import net.minecraft.entity.projectile.thrown.EnderPearlEntity
import net.minecraft.entity.projectile.thrown.SnowballEntity
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleType
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * ModuleProjectileParticle - Displays particle effects when projectile entities are spawned.
 * Allows cycling through multiple particles or spawning all at once for various projectile types.
 */
object ModuleProjectileParticle : ClientModule("ProjectileParticle", Category.RENDER) {

    // --- CONFIGURABLES ---
    /** List of particles to spawn (e.g., "minecraft:crit", "minecraft:damage_indicator") */
    private val particleList by textList( // --- FIX: Use textList for string list ---
        "ParticleList",
        mutableListOf(
            "minecraft:crit",
            "minecraft:damage_indicator",
            "minecraft:heart",
            "minecraft:happy_villager"
        )
    )

    /** Spawn mode: Cycle through particles or spawn all at once */
    private val spawnMode by enumChoice("SpawnMode", SpawnMode.CYCLE)

    /** Number of particles to spawn per projectile spawn */
    private val particleCount by int("ParticleCount", 5, 1..50, "particles")

    /** Particle speed multiplier */
    private val speedMultiplier by float("SpeedMultiplier", 0.1f, 0.01f..1.0f, "x")

    /** Particle spread (random offset) */
    private val spread by float("Spread", 0.5f, 0.0f..2.0f, "blocks")

    /** Whether to enable particles for arrows */
    private val enableArrows by boolean("Arrows", true)

    /** Whether to enable particles for eggs */
    private val enableEggs by boolean("Eggs", true)

    /** Whether to enable particles for snowballs */
    private val enableSnowballs by boolean("Snowballs", true)

    /** Whether to enable particles for ender pearls */
    private val enableEnderPearls by boolean("EnderPearls", true)

    /** Whether to enable particles for fireballs (ghast/firecharge) */
    private val enableFireballs by boolean("Fireballs", true)

    /** Whether to enable particles for fishing bobbers */
    private val enableBobbers by boolean("Bobbers", true)

    /** Whether to enable particles for all other projectiles */
    private val enableOthers by boolean("Others", true)
    // --- END CONFIGURABLES ---

    /** Index for cycling particles */
    private var currentIndex = 0

    /** Cache for particle types to improve performance */
    private val particleTypeCache = ConcurrentHashMap<String, ParticleEffect?>()

    /** Set of processed entity IDs to prevent duplicate particle spawning */
    private val processedEntities = mutableSetOf<Int>()

    override fun enable() {
        currentIndex = 0
        processedEntities.clear()
        particleTypeCache.clear()
    }

    override fun disable() {
        currentIndex = 0
        processedEntities.clear()
        particleTypeCache.clear()
    }

    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> {
        if (!enabled) return@handler

        val world = mc.world ?: return@handler
        val player = mc.player ?: return@handler

        val searchRadius = 64.0
        val box = player.boundingBox.expand(searchRadius)
        val projectiles = world.getNonSpectatingEntities(ProjectileEntity::class.java, box)

        val currentIds = HashSet<Int>(projectiles.size)
        for (entity in projectiles) {
            currentIds.add(entity.id)
            if (entity.id !in processedEntities && isProjectileTypeEnabled(entity)) {
                spawnParticlesAtEntity(entity)
                processedEntities.add(entity.id)
            }
        }

        processedEntities.retainAll(currentIds)
    }

    /**
     * Checks if the given entity is a projectile and if its specific type is enabled.
     *
     * @param entity The entity to check.
     * @return True if the entity is a projectile and its type is enabled, false otherwise.
     */
    private fun isProjectileTypeEnabled(entity: Entity): Boolean {
        return when (entity) {
            is ArrowEntity -> enableArrows
            is EggEntity -> enableEggs
            is SnowballEntity -> enableSnowballs
            is EnderPearlEntity -> enableEnderPearls
            is FireballEntity -> enableFireballs // Includes Ghast fireballs and Blaze fire charges
            is FishingBobberEntity -> enableBobbers
            is ProjectileEntity -> enableOthers // Catch-all for other ProjectileEntity subclasses
            else -> false // Not a projectile entity
        }
    }

    /**
     * Spawns particles at the given entity's position based on the spawn mode.
     *
     * @param entity The entity at whose position to spawn particles.
     */
    private fun spawnParticlesAtEntity(entity: Entity) {
        val pos = entity.pos

        when (spawnMode) {
            SpawnMode.CYCLE -> {
                // Cycle through particles
                if (particleList.isNotEmpty()) {
                    val particleIdentifier = particleList[currentIndex % particleList.size]
                    spawnSingleParticleType(pos, particleIdentifier)
                    currentIndex = (currentIndex + 1) % particleList.size
                }
            }
            SpawnMode.ALL -> {
                // Spawn all particles at once
                for (identifier in particleList) {
                    spawnSingleParticleType(pos, identifier)
                }
            }
        }
    }

    /**
     * Spawns a specified number of particles of a single type at the given position.
     *
     * @param pos The position to spawn the particles at.
     * @param particleIdentifier The identifier of the particle type (e.g., "minecraft:crit").
     */
    private fun spawnSingleParticleType(pos: Vec3d, particleIdentifier: String) {
        val world = mc.world ?: return
        val particleEffect = getParticleEffectFromIdentifier(particleIdentifier) ?: return

        repeat(particleCount) {
            // Calculate random offset for spread
            val offsetX = (Random.nextFloat() - 0.5f) * spread * 2.0f
            val offsetY = (Random.nextFloat() - 0.5f) * spread * 2.0f
            val offsetZ = (Random.nextFloat() - 0.5f) * spread * 2.0f

            // Calculate random velocity
            val velX = (Random.nextFloat() - 0.5f) * speedMultiplier
            val velY = (Random.nextFloat() - 0.5f) * speedMultiplier
            val velZ = (Random.nextFloat() - 0.5f) * speedMultiplier

            // --- FIX 4: Use correct method signature for addParticle ---
            // The correct signature is addParticle(ParticleEffect, x, y, z, velX, velY, velZ)
            world.addParticle(
                particleEffect, // --- FIX 7: Use ParticleEffect type ---
                pos.x + offsetX,
                pos.y + offsetY,
                pos.z + offsetZ,
                velX.toDouble(),
                velY.toDouble(),
                velZ.toDouble()
            )
        }
    }

    /**
     * Gets a ParticleEffect from a particle identifier string, using a cache for performance.
     *
     * @param identifier The particle identifier (e.g., "minecraft:crit").
     * @return The corresponding ParticleEffect, or null if not found or invalid.
     */
    private fun getParticleEffectFromIdentifier(identifier: String): ParticleEffect? {
        return particleTypeCache.getOrPut(identifier) {
            try {
                val id = Identifier.tryParse(identifier) ?: return@getOrPut null
                val particleType: ParticleType<*>? = Registries.PARTICLE_TYPE.get(id)
                val effect = particleType as? ParticleEffect
                if (effect == null) {
                    logger.warn("Unsupported particle type for identifier: $identifier")
                }
                effect
            } catch (e: Exception) {
                logger.warn("Failed to get particle effect for identifier: $identifier", e)
                null
            }
        }
    }

    /**
     * Enum for spawn modes
     */
    private enum class SpawnMode(override val choiceName: String) : NamedChoice {
        CYCLE("Cycle"),
        ALL("All")
    }
}
