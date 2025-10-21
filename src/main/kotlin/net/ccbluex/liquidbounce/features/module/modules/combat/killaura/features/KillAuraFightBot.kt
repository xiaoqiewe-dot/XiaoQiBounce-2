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
package net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features

import net.ccbluex.liquidbounce.config.types.nesting.Configurable
import net.ccbluex.liquidbounce.config.types.nesting.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.features.module.modules.combat.crystalaura.SwitchMode
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura.clickScheduler
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura.targetTracker
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.Chronometer
import net.ccbluex.liquidbounce.utils.client.clickBlockWithSlot
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.entity.*
import net.ccbluex.liquidbounce.utils.math.times
import net.ccbluex.liquidbounce.utils.navigation.NavigationBaseConfigurable
import net.minecraft.block.SideShapeType
import net.minecraft.entity.Entity
import net.minecraft.item.BlockItem
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import kotlin.math.min
import kotlin.math.pow

/**
 * Data class holding combat-related context
 */
data class CombatContext(
    val playerPosition: Vec3d,
    val combatTarget: CombatTarget?
)

data class CombatTarget(
    val entity: Entity,
    val distance: Double,
    val range: Float,
    val outOfDistance: Boolean,
    val targetRotation: Rotation,
    val requiredTargetRotation: Rotation,
    val outOfDanger: Boolean
)

/**
 * A fight bot that handles combat and movement automatically
 */
object KillAuraFightBot : NavigationBaseConfigurable<CombatContext>(ModuleKillAura, "FightBot", false) {

    private val opponentRange by float("OpponentRange", 3f, 0.1f..10f)
    private val dangerousYawDiff by float("DangerousYaw", 55f, 0f..90f, suffix = "°")
    private val runawayOnCooldown by boolean("RunawayOnCooldown", true)

    // Pathing and safety
    private val avoidVoid by boolean("AvoidVoid", true)
    private val voidCheckDepth by int("VoidCheckDepth", 12, 3..32)

    // Auto-bridge support
    private val autoBridge by boolean("AutoBridge", true)
    private val bridgeDelayMs by int("BridgeDelay", 120, 0..1000, "ms")

    private val bridgeChronometer = Chronometer()

    internal object TargetFilter : Configurable("TargetFilter") {
        internal var range by float("Range", 50f, 10f..100f)
        internal var visibleOnly by boolean("VisibleOnly", true)
        internal var notWhenVoid by boolean("NotWhenVoid", true)
    }

    /**
     * Configuration for leader following functionality
     */
    internal object LeaderFollower : ToggleableConfigurable(this, "Leader", false) {
        internal val username by text("Username", "")
        internal val radius by float("Radius", 5f, 2f..10f)
    }

    init {
        tree(TargetFilter)
        tree(LeaderFollower)
    }

    fun updateTarget() {
        targetTracker.select { entity ->
            if (player.squaredBoxedDistanceTo(entity) > TargetFilter.range.pow(2)) {
                return@select null
            }

            if (TargetFilter.visibleOnly && !player.canSee(entity)) {
                return@select null
            }

            if (TargetFilter.notWhenVoid && entity.doesNotCollideBelow()) {
                return@select null
            }

            entity
        }
    }

    /**
     * Creates combat context
     */
    override fun createNavigationContext(): CombatContext {
        val playerPosition = player.pos

        val combatTarget = targetTracker.target?.let { entity ->
            val distance = playerPosition.distanceTo(entity.pos)
            val range = min(ModuleKillAura.range, distance.toFloat())
            val outOfDistance = distance > opponentRange

            val targetRotation = entity.rotation.copy(pitch = 0.0f)
            val requiredTargetRotation = Rotation.lookingAt(playerPosition, entity.eyePos).copy(pitch = 0.0f)
            val outOfDanger = targetRotation.angleTo(requiredTargetRotation) > dangerousYawDiff

            CombatTarget(entity, distance, range, outOfDistance, targetRotation, requiredTargetRotation, outOfDanger)
        }

        return CombatContext(
            playerPosition,
            combatTarget
        )
    }

    /**
     * Calculates the desired position to move towards
     *
     * @return Target position as Vec3d
     */
    override fun calculateGoalPosition(context: CombatContext): Vec3d? {
        // Try to follow leader first
        if (LeaderFollower.running && LeaderFollower.username.isNotEmpty()) {
            val leader = world.players.find { it.gameProfile.name == LeaderFollower.username }
            if (leader != null) {
                return calculateLeaderGoalPosition(leader.pos, context.playerPosition)
            }
        }

        // Otherwise handle combat movement
        val combatTarget = context.combatTarget ?: return null
        return if (runawayOnCooldown && !clickScheduler.willClickAt()) {
            calculateRunawayPosition(context, combatTarget)
        } else {
            calculateAttackPosition(context, combatTarget)
        }
    }

    /**
     * Handles additional movement mechanics like swimming and jumping
     *
     * @param event Movement input event to modify
     */
    override fun handleMovementAssist(event: MovementInputEvent, context: CombatContext) {
        super.handleMovementAssist(event, context)

        val contextAllowsJump = context.combatTarget != null && context.combatTarget.outOfDistance
            && !context.combatTarget.outOfDanger
        val goal = calculateGoalPosition(context)
        val leaderAllowsJump = LeaderFollower.running && player.pos.distanceTo(goal) > LeaderFollower.radius

        if (contextAllowsJump || leaderAllowsJump) {
            event.jump = true
        }

        // Auto bridge if needed towards our goal
        if (goal != null) {
            attemptAutoBridgeTowards(goal)
        }
    }

    /**
     * Gets rotation based on movement and target
     *
     * @return Movement rotation or null if no target
     */
    override fun getMovementRotation(): Rotation {
        val movementRotation = super.getMovementRotation()
        val movementPitch = targetTracker.target?.let { entity ->
            Rotation.lookingAt(point = entity.box.center, from = player.eyePos).pitch
        } ?: return movementRotation

        return movementRotation.copy(pitch = movementPitch)
    }

    // --- Helpers: safety & bridging ---
    private fun canStandOn(pos: BlockPos): Boolean {
        val state = world.getBlockState(pos)
        return state.isSideSolid(world, pos, Direction.UP, SideShapeType.CENTER)
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
            val pos = start.add(delta * t)
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
        if (!bridgeChronometer.hasAtLeastElapsed(bridgeDelayMs.toLong())) return

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
        val hitResult = BlockHitResult(hitVec, dir, supportPos, false)

        clickBlockWithSlot(
            player,
            hitResult,
            slot,
            SwingMode.DO_NOT_HIDE,
            SwitchMode.SILENT,
            false
        )

        bridgeChronometer.reset()
    }

    private fun calculateLeaderGoalPosition(leaderPosition: Vec3d, playerPosition: Vec3d): Vec3d {
        return (-180..180 step 45)
            .mapNotNull { yaw ->
                val rotation = Rotation(yaw = yaw.toFloat(), pitch = 0.0F)
                val position = leaderPosition.add(rotation.directionVector * LeaderFollower.radius.toDouble())

                if (!isPositionSafe(position) || !hasSafeLinePath(playerPosition, position)) {
                    return@mapNotNull null
                }

                ModuleDebug.debugGeometry(
                    this,
                    "Possible Position $yaw",
                    ModuleDebug.DebuggedPoint(position, Color4b.MAGENTA)
                )
                position
            }
            .minByOrNull { it.squaredDistanceTo(playerPosition) } ?: leaderPosition
    }

    private fun calculateRunawayPosition(context: CombatContext, combatTarget: CombatTarget): Vec3d {
        val base = context.playerPosition
        val desired = base.add(
            combatTarget.requiredTargetRotation.directionVector * combatTarget.range.toDouble()
        )

        if (isPositionSafe(desired) && hasSafeLinePath(base, desired)) {
            return desired
        }

        val baseYaw = combatTarget.requiredTargetRotation.yaw
        val offsets = intArrayOf(15, -15, 30, -30, 45, -45, 60, -60, 75, -75, 90, -90)
        for (off in offsets) {
            val rot = Rotation(yaw = (baseYaw + off).coerceIn(-180f, 180f), pitch = 0.0f)
            val pos = base.add(rot.directionVector * combatTarget.range.toDouble())
            if (isPositionSafe(pos) && hasSafeLinePath(base, pos)) {
                return pos
            }
        }

        return base
    }

    private fun calculateAttackPosition(context: CombatContext, combatTarget: CombatTarget): Vec3d {
        val target = combatTarget.entity
        val targetLookPosition = target.pos.add(
            combatTarget.targetRotation.directionVector * combatTarget.range.toDouble()
        )

        val best = (-180..180 step 10)
            .mapNotNull { yaw ->
                val rotation = Rotation(yaw = yaw.toFloat(), pitch = 0.0F)
                val position = target.pos.add(rotation.directionVector * combatTarget.range.toDouble())

                // Check if this point collides with a block
                if (player.doesCollideAt(position)) {
                    return@mapNotNull null
                }

                // Avoid dangerous angle
                val isInAngle = rotation.angleTo(combatTarget.targetRotation) <= dangerousYawDiff
                if (isInAngle) {
                    ModuleDebug.debugGeometry(
                        this,
                        "Possible Position $yaw",
                        ModuleDebug.DebuggedPoint(position, Color4b.RED)
                    )
                    return@mapNotNull null
                }

                // Avoid void and unsafe paths
                if (!isPositionSafe(position) || !hasSafeLinePath(context.playerPosition, position)) {
                    return@mapNotNull null
                }

                ModuleDebug.debugGeometry(
                    this,
                    "Possible Position $yaw",
                    ModuleDebug.DebuggedPoint(position, Color4b.GREEN)
                )

                position
            }
            .sortedBy { pos -> pos.squaredDistanceTo(targetLookPosition) }
            .minByOrNull { pos -> pos.squaredDistanceTo(context.playerPosition) }

        if (best != null) return best

        // Fallback to direct look position if it is safe, otherwise stay where we are
        return if (isPositionSafe(targetLookPosition) && hasSafeLinePath(context.playerPosition, targetLookPosition)) {
            targetLookPosition
        } else {
            context.playerPosition
        }
    }

}
