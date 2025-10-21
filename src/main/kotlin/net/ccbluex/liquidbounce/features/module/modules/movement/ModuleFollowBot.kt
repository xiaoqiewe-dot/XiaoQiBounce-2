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
package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.config.types.nesting.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.clickBlockWithSlot
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.entity.moving
import net.ccbluex.liquidbounce.utils.navigation.NavigationBaseConfigurable
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.BlockItem
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

/**
 * A follow bot that automatically follows a specified player.
 * Designed to avoid detection by anti-cheat systems like GrimAC.
 * Uses MovementInputEvent to control movement, similar to KillAuraFightBot.
 */
object ModuleFollowBot : ClientModule("FollowBot", Category.MOVEMENT) {

    /**
     * Configuration for the follow bot functionality
     */
    internal object FollowConfig : ToggleableConfigurable(this, "Follow", false) {
        /** Name of the player to follow */
        internal val username by text("Username", "")

        /** Distance to maintain from the target player (0.0 to 10.0 blocks) */
        internal val distance by float("Distance", 3f, 0f..10f, "blocks")
    }

    init {
        tree(FollowConfig)
    }

    /**
     * Context for the follow bot navigation
 * 用于跟随机器人导航的上下文数据类
     */
    data class FollowContext(  // 定义一个数据类，用于存储跟随机器人的相关信息
        val playerPosition: Vec3d,  // 玩家的三维位置坐标
        val targetPlayer: PlayerEntity?,  // 目标玩家实体，可能为null
        val distance: Float  // 与目标玩家的距离
    )

    /**
     * A follow bot that handles movement automatically
     */
    internal object FollowBot : NavigationBaseConfigurable<FollowContext>(ModuleFollowBot, "FollowBot", false) {

        private val avoidVoid by boolean("AvoidVoid", true)
        private val voidCheckDepth by int("VoidCheckDepth", 12, 3..32)
        private val autoBridge by boolean("AutoBridge", true)
        private val bridgeDelayMs by int("BridgeDelay", 120, 0..1000, "ms")

        private var lastBridgeMs = 0L

        /**
         * Calculates the desired position to move towards
         *
         * @return Target position as Vec3d
         */
        override fun calculateGoalPosition(context: FollowContext): Vec3d? {
            if (!FollowConfig.enabled) return null

            val target = context.targetPlayer ?: return null
            val playerPos = context.playerPosition

            val radius = context.distance.toDouble().coerceAtLeast(0.5)

            // Probe around the target in 45° steps and pick the closest safe point
            val best = (-180..180 step 45)
                .mapNotNull { yaw ->
                    val rot = Rotation(yaw = yaw.toFloat(), pitch = 0.0f)
                    val pos = target.pos.add(rot.directionVector.multiply(radius))

                    // Skip unsafe points and unsafe straight-line paths
                    if (!isPositionSafe(pos) || !hasSafeLinePath(playerPos, pos)) return@mapNotNull null

                    // Debug candidates
                    ModuleDebug.debugGeometry(this, "FollowCandidate $yaw", ModuleDebug.DebuggedPoint(pos, Color4b.CYAN))
                    pos
                }
                .minByOrNull { it.squaredDistanceTo(playerPos) }

            return best ?: target.pos
        }

        /**
         * Handles additional movement mechanics like jumping
         *
         * @param event Movement input event to modify
         */
        override fun handleMovementAssist(event: MovementInputEvent, context: FollowContext) {
            super.handleMovementAssist(event, context)

            val goal = calculateGoalPosition(context) ?: return

            // Small jump assist if we lag behind
            val target = context.targetPlayer
            if (target != null) {
                val currentDistance = context.playerPosition.distanceTo(target.pos)
                if (currentDistance > context.distance + 1.0 && player.moving) {
                    event.jump = true
                }
            }

            // Try to bridge safely when path goes over void
            attemptAutoBridgeTowards(goal)
        }

        // --- Helpers: safety & bridging ---
        private fun canStandOn(pos: BlockPos): Boolean {
            val state = world.getBlockState(pos)
            return state.isSideSolid(world, pos, Direction.UP)
        }

        private fun isOverVoid(position: Vec3d): Boolean {
            if (!avoidVoid) return false
            var checkPos = BlockPos(position.x.toInt(), (position.y - 1.0).toInt(), position.z.toInt())
            repeat(voidCheckDepth) {
                if (canStandOn(checkPos)) return false
                checkPos = checkPos.down()
            }
            return true
        }

        private fun isPositionSafe(position: Vec3d): Boolean {
            if (player.doesCollideAt(position)) return false
            if (avoidVoid && isOverVoid(position)) return false
            return true
        }

        private fun hasSafeLinePath(start: Vec3d, end: Vec3d): Boolean {
            if (!avoidVoid) return true
            val distance = start.distanceTo(end)
            val steps = kotlin.math.max(1, (distance / 0.75).toInt())
            val delta = end.subtract(start)
            for (i in 1..steps) {
                val t = i.toDouble() / steps.toDouble()
                val pos = start.add(delta.multiply(t))
                if (isOverVoid(pos)) return false
            }
            return true
        }

        private fun cardinalDirectionTowards(goal: Vec3d): Direction {
            val dx = goal.x - player.x
            val dz = goal.z - player.z
            return if (kotlin.math.abs(dx) > kotlin.math.abs(dz)) {
                if (dx > 0) Direction.EAST else Direction.WEST
            } else {
                if (dz > 0) Direction.SOUTH else Direction.NORTH
            }
        }

        private fun attemptAutoBridgeTowards(goal: Vec3d) {
            if (!autoBridge) return
            val now = System.currentTimeMillis()
            if (now - lastBridgeMs < bridgeDelayMs) return

            // Only consider bridging if upcoming step is over void
            if (!isOverVoid(goal)) return

            val dir = cardinalDirectionTowards(goal)
            val supportPos = player.blockPos.down()
            val placePos = supportPos.offset(dir)

            // Already has ground there
            if (canStandOn(placePos)) return

            // Need a support block to click on
            val supportState = world.getBlockState(supportPos)
            if (supportState.isAir) return

            // Find a block in hotbar
            val slot = (0..8).firstOrNull { player.inventory.getStack(it).item is BlockItem } ?: return

            val center = supportPos.toCenterPos()
            val hitVec = center.add(dir.offsetX * 0.5, dir.offsetY * 0.5, dir.offsetZ * 0.5)
            val hitResult = net.minecraft.util.hit.BlockHitResult(hitVec, dir, supportPos, false)

            clickBlockWithSlot(
                player,
                hitResult,
                slot,
                SwingMode.DO_NOT_HIDE,
                net.ccbluex.liquidbounce.features.module.modules.combat.crystalaura.SwitchMode.SILENT,
                false
            )

            lastBridgeMs = now
        }

        /**
         * Gets rotation based on movement
         *
         * @return Movement rotation or null if no target
         */
        override fun getMovementRotation(): net.ccbluex.liquidbounce.utils.aiming.data.Rotation {
            val movementRotation = super.getMovementRotation()
            return movementRotation
        }

        /**
         * Creates follow context
         */
        public override fun createNavigationContext(): FollowContext {
            val playerPos = player.pos
            val targetName = FollowConfig.username

            val target = if (targetName.isNotEmpty()) {
                world.players.find { it.gameProfile.name == targetName }
            } else {
                null
            }

            return FollowContext(
                playerPos,
                target,
                FollowConfig.distance
            )
        }
    }

    init {
        tree(FollowBot)
    }

    /**
     * Handles movement input to ensure smooth following
     * This is where we can directly influence how the player moves
     * to avoid detection by anti-cheat systems.
     */
    // Legacy movement handler removed in favor of NavigationBaseConfigurable-driven input and safe auto-bridge.

    override fun enable() {
        // Ensure the module is enabled if the config is enabled
        if (!FollowConfig.enabled) {
            FollowConfig.enabled = true
        }
    }

    override fun disable() {
        // Nothing special to do on disable
    }
}
