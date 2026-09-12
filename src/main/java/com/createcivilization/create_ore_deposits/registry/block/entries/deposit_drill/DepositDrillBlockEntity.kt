package com.createcivilization.create_ore_deposits.registry.block.entries.deposit_drill

import com.createcivilization.create_ore_deposits.config.Config
import com.createcivilization.create_ore_deposits.registry.datamap.CreateOreDepositsDataMaps.COOLING_FACTOR_DATA
import com.createcivilization.create_ore_deposits.registry.datamap.CreateOreDepositsDataMaps.DEPOSIT_DATA
import com.createcivilization.create_ore_deposits.registry.datamap.CreateOreDepositsDataMaps.LUBRICANT_FACTOR_DATA
import com.createcivilization.create_ore_deposits.registry.datamap.CreateOreDepositsDataMaps.TIP_TIER_DATA
import com.createcivilization.create_ore_deposits.registry.fluid.CreateOreDepositsFluids
import com.createcivilization.create_ore_deposits.registry.fluid.FluidHandler
import com.createcivilization.create_ore_deposits.registry.tag.CreateOreDepositsTags
import com.createcivilization.create_ore_deposits.util.logD
import com.createcivilization.create_ore_deposits.util.translate

import com.simibubi.create.content.kinetics.base.BlockBreakingKineticBlockEntity
import com.simibubi.create.foundation.item.TooltipHelper
import com.simibubi.create.foundation.utility.BlockHelper
import com.simibubi.create.foundation.utility.ServerSpeedProvider
import net.createmod.catnip.animation.LerpedFloat
import net.createmod.catnip.nbt.NBTHelper

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.Fluids
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import net.neoforged.neoforge.items.IItemHandler
import net.neoforged.neoforge.items.ItemStackHandler

import java.util.ArrayList
import java.util.HashSet
import kotlin.math.roundToInt

private const val MIN_LERP = 0.5

// all the drill states. no GUI, everything shows up on the goggles instead.
enum class DrillState {
	IDLE,
	MINING,
	OVERHEATING,
	JAMMED,
	NO_TIP,
	INSUFFICIENT_TIP,
	NO_POWER
}

class DepositDrillBlockEntity(
	type: BlockEntityType<*>,
	pos: BlockPos,
	blockState: BlockState
) : BlockBreakingKineticBlockEntity(type, pos, blockState) {

	private var drillOffset: Float = 0f
	private var lerpedOffset: LerpedFloat = LerpedFloat.linear().startWithValue(MIN_LERP)
	private var lastBlock: Block? = null
	private var temperature: Float = Config.SERVER.DEPOSIT_DRILL.baseTemperature
	// smoothed copy of temperature for the goggles readout, otherwise the number flickers
	private var displayTemperature: Float = Config.SERVER.DEPOSIT_DRILL.baseTemperature
	// hardness grabbed once at the start of the tick, see tick() for why
	private var lastHardness: Float = 1f
	private var maxAttempts: Int = 0
	private var remainingAttempts: Int = 0
	private var drillTickCounter: Int = 0
	private var currentDepositPos: BlockPos? = null
	private var drillState: DrillState = DrillState.IDLE
	private var isActuallyMiningSnapshot: Boolean = false
	// fractional tip wear, lets mild overheating eat the tip slowly instead of in big jumps
	private var tipWearAccumulator: Float = 0f

	private var depositQueue: ArrayDeque<BlockPos> = ArrayDeque()
	private val depositQueueSet: MutableSet<BlockPos> = HashSet(256)
	private val bfsVisited: MutableSet<BlockPos> = HashSet(256)
	private val bfsPositions: MutableList<BlockPos> = ArrayList(256)
	private val bfsQueue: ArrayDeque<BlockPos> = ArrayDeque(256)

	private val itemHandler = ItemStackHandler(9)
	private val drillTipHandler = ItemStackHandler()

	private val lubricantHandler = FluidHandler(
		1000,
		mutableSetOf(
			CreateOreDepositsFluids.LUBRICANT.get(),
			CreateOreDepositsFluids.LUBRICANT.get().source
		)
	)

	private val coolantHandler = FluidHandler(
		1000,
		mutableSetOf(
			Fluids.WATER,
			Fluids.FLOWING_WATER
		)
	)

	override fun tick() {
		super.tick()

		val movementSpeed: Float = getMovementSpeed()
		val canMove: Boolean = getTargetBlock() == Blocks.AIR || movementSpeed < 0

		lerpedOffset.forceNextSync()
		if (canMove) {
			drillOffset = (movementSpeed + drillOffset).coerceAtLeast(0f)
			setLerpedOffset(drillOffset)
		} else setLerpedOffset(drillOffset.roundToInt())

		// grab the target once per tick. getTargetBlockState() flips between currentDepositPos
		// and drillTipPos while the queue rebuilds, which made hardness flicker and the
		// temperature jump around. hardness and mining get passed in now.
		val snapTargetState: BlockState? = getTargetBlockState()
		val snapIsDeposit: Boolean = isDeposit(snapTargetState)
		val snapHardness: Float = getBlockHardness(snapTargetState)
		lastHardness = snapHardness

		drillState = recomputeState()
		isActuallyMiningSnapshot = drillState == DrillState.MINING || drillState == DrillState.OVERHEATING

		updateTemperature(snapIsDeposit && isActuallyMiningSnapshot, snapHardness)
		displayTemperature = Mth.lerp(0.08f, displayTemperature, temperature)

		emitDrillFeedback()

		// nothing to mine right now (idle, no tip, jammed, no power), so skip extraction.
		// the tip only wears while actually turning through a deposit, not while parked.
		if (!isActuallyMiningSnapshot) return

		onBreakTick()
		damageTip()
	}

	// stops the parent BlockBreakingKineticBlockEntity loop (the one that breaks non-deposit
	// blocks on the way down) whenever one of the drill states pauses us
	override fun shouldRun(): Boolean = when (drillState) {
		DrillState.NO_TIP,
		DrillState.INSUFFICIENT_TIP,
		DrillState.JAMMED -> false
		else -> true
	}

	override fun lazyTick() {
		super.lazyTick()
		setChanged()
		sendData()

		// only drink fluids while actually mining or overheating. nothing drains when idle,
		// jammed or parked. fluids are optional, they just keep the temperature down.
		if (!isActuallyMiningSnapshot) return

		val cfg = Config.SERVER.DEPOSIT_DRILL
		val speedBonus: Int = (speed / 128f).toInt().coerceAtLeast(0)
		lubricantHandler.drain(cfg.lubeDrainPerLazy + speedBonus, FluidAction.EXECUTE)
		coolantHandler.drain(cfg.coolantDrainPerLazy + speedBonus, FluidAction.EXECUTE)
		// lazyTickRate is 10 ticks, drain = base + speed/128 per lazy tick. at 128 RPM with
		// the defaults that's 2mb/lazy = 0.2mb/tick, so about 83s per bucket. bump
		// lubeDrainPerLazy / coolantDrainPerLazy (or drain per tick) to get closer to the
		// ~35 min per bucket I was aiming for. still needs a balance pass.
	}

	override fun canBreak(stateToBreak: BlockState, blockHardness: Float): Boolean {
		if (isDeposit(stateToBreak)) return false
		return super.canBreak(stateToBreak, blockHardness)
	}

	override fun getBreakSpeed(): Float = super.getBreakSpeed()

	override fun getBreakingPos(): BlockPos = if (canMine()) getTargetPos() else BlockPos.ZERO

	fun onBreakTick() {
		val level = this.level ?: return
		if (level.isClientSide) return
		if (!canMine()) return

		val tipPos = getDrillTipPos()
		val tipState = level.getBlockState(tipPos)

		if (!isDeposit(tipState)) {
			clearDepositQueue()
			return
		}

		if (currentDepositPos == null || (tipPos != currentDepositPos && !depositQueueSet.contains(tipPos))) {
			buildDepositQueue(level, tipPos)
			currentDepositPos = getCurrentQueueHead()
			initializeCurrentDeposit(level)
		}

		val targetPos = currentDepositPos ?: return
		val blockState = level.getBlockState(targetPos)

		if (!isDeposit(blockState)) {
			advanceDepositQueue(level)
			return
		}

		// don't burn attempts on a deposit the current tip can't mine
		if (!isTipSufficientFor(blockState)) {
			drillState = DrillState.INSUFFICIENT_TIP
			clearDestroyProgress()
			return
		}

		if (remainingAttempts <= 0) return

		drillTickCounter++

		if (drillTickCounter >= calculateExtractionInterval()) {
			drillTickCounter = 0
			// handy while balancing, turn on debug logging to watch interval/state/heat live
			logD("Drill extraction @ $blockPos interval=${calculateExtractionInterval()} state=$drillState temp=${temperature.toInt()}K remaining=$remainingAttempts")

			// stall instead of voiding, remainingAttempts stays as it is
			if (!hasOutputSpace()) {
				drillState = DrillState.JAMMED
				return
			}

			remainingAttempts--

			val serverLevel: ServerLevel = level as ServerLevel
			val tipStack: ItemStack = drillTipHandler.getStackInSlot(0)
			for (stack in getSimulatedDrops(blockState, serverLevel, targetPos, tipStack)) {
				insertOutput(stack)
			}

			updateDestroyProgress(targetPos)

			if (remainingAttempts <= 0) {
				level.destroyBlockProgress(blockPos.hashCode(), targetPos, -1)
				// veins are finite for now. regrowth is a future thing, this is the spot
				// where it would get scheduled.
				if (Config.SERVER.DEPOSIT_DRILL.enableRegeneration) {
					// TODO: schedule a block tick to bring this deposit back after
					// regenerationTicks ticks. not implemented yet.
				}
				level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), 3)
				level.playSound(null, targetPos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f)
				advanceDepositQueue(level)
			}
		}
	}

	fun canMine(): Boolean {
		val tip: ItemStack = drillTipHandler.getStackInSlot(0)
		if (tip.isEmpty || !tip.tags.anyMatch(CreateOreDepositsTags.DRILL_TIP::equals)) return false
		val target: BlockState? = getTargetBlockState()
		if (target != null && isDeposit(target) && !isTipSufficientFor(target)) return false
		return hasOutputSpace()
	}

	// tips are disposable, no repair. tier comes from the tags first, then the datamap.
	fun getTipTier(stack: ItemStack): Int = when {
		stack.`is`(CreateOreDepositsTags.DIAMOND_TIP_TIER) -> 4
		stack.`is`(CreateOreDepositsTags.STEEL_TIP_TIER) -> 3
		stack.`is`(CreateOreDepositsTags.GOLD_TIP_TIER) -> 2
		stack.`is`(CreateOreDepositsTags.IRON_TIP_TIER) -> 1
		else -> stack.itemHolder.getData(TIP_TIER_DATA)?.tier ?: 1
	}

	fun isTipSufficientFor(state: BlockState?): Boolean {
		if (state == null || !isDeposit(state)) return true
		val required: Int = state.blockHolder.getData(DEPOSIT_DATA)?.requiredTier ?: 1
		return getTipTier(drillTipHandler.getStackInSlot(0)) >= required
	}

	fun hasOutputSpace(): Boolean {
		for (slot in 0 until itemHandler.slots) {
			val stack = itemHandler.getStackInSlot(slot)
			if (stack.isEmpty || stack.count < itemHandler.getSlotLimit(slot)) {
				return true
			}
		}
		return false
	}

	private fun insertOutput(stack: ItemStack) {
		var remaining = stack.copy()
		for (slot in 0 until itemHandler.slots) {
			if (remaining.isEmpty) break
			remaining = itemHandler.insertItem(slot, remaining, false)
		}
		if (!remaining.isEmpty) {
			// never void. hasOutputSpace() gates attempt consumption so this should basically
			// never happen, but if it does just pop the overflow into the world.
			val level = this.level ?: return
			Block.popResource(level, worldPosition.relative(outputFacing()), remaining)
		}
	}

	private fun outputFacing(): Direction =
		blockState.getValue(BlockStateProperties.HORIZONTAL_FACING).counterClockWise

	// ticks between extraction attempts. drop baseExtractionInterval to ~220-260 to hit
	// the ~20 ores/min target at 128 RPM with fluids (1 attempt = 1 simulated loot roll)
	fun calculateExtractionInterval(): Int {
		val cfg = Config.SERVER.DEPOSIT_DRILL
		val base = cfg.baseExtractionInterval
		val speedBonus = (speed * (cfg.speedFactor + getLubricantFactor() * cfg.lubeTickBonus)).roundToInt()
		val penalty = (getBlockHardness(getTargetBlockState()) * cfg.hardnessTickPenalty).roundToInt()
		var interval = base - speedBonus + penalty
		// the hotter it runs past the overheat threshold, the slower it pulls ore out.
		// at full severity that's (1 + overheatSlowdown)x the normal interval.
		interval = (interval * (1f + getOverheatFactor() * cfg.overheatSlowdown)).toInt()
		// a worn tip bites worse too, so the closer it is to breaking the slower it cuts
		interval = (interval * (1f + getTipWearFraction() * cfg.wornTipSlowdown)).toInt()
		return interval.coerceIn(cfg.minInterval, cfg.maxInterval)
	}

	fun getSimulatedDrops(
		state: BlockState,
		serverLevel: ServerLevel,
		pos: BlockPos,
		tip: ItemStack
	): List<ItemStack> {
		return Block.getDrops(state, serverLevel, pos, serverLevel.getBlockEntity(pos), null, tip)
	}

	override fun onBlockBroken(stateToBreak: BlockState) {
		lastBlock = getTargetBlock()
		BlockHelper.destroyBlock(level, breakingPos, 1f) { drops: ItemStack ->
			if (!isDeposit(stateToBreak)) {
				insertOutput(drops)
			}
		}
	}

	fun clearDestroyProgress() {
		currentDepositPos?.let { pos ->
			level?.destroyBlockProgress(blockPos.hashCode(), pos, -1)
		}
	}

	fun updateDestroyProgress(targetPos: BlockPos) {
		if (maxAttempts <= 0) return
		val attemptsUsed: Int = maxAttempts - remainingAttempts
		val stage: Int = ((attemptsUsed.toFloat() / maxAttempts) * 10f).toInt().coerceIn(0..9)
		level?.destroyBlockProgress(blockPos.hashCode(), targetPos, stage)
	}

	// heat model. mining always makes heat, no fluids required. lubricant cuts the friction
	// so less heat gets made in the first place, coolant carries heat away faster. how much
	// either helps scales with how full its tank is, so a full setup can hold the
	// temperature flat while a dry drill slowly climbs past the overheat point.
	fun updateTemperature(isMining: Boolean, hardness: Float) {
		val cfg = Config.SERVER.DEPOSIT_DRILL
		val ambient = cfg.baseTemperature

		// lube reduces generated heat, capped so it can never fully zero it out
		val lubeCut: Float = (getLubricantFactor() * getLubricantFill() * cfg.lubeHeatReduction)
			.coerceIn(0f, 0.9f)

		// heat load, floored at minHeatLoad. without the floor a slow drill settles at a cool
		// equilibrium and never gets anywhere near the overheat point, no matter how long you run it.
		val load: Float = (speed / 64f).coerceAtLeast(cfg.minHeatLoad)

		val heat: Float = if (isMining) {
			(cfg.heatPerTickBase + hardness * cfg.heatPerTickHardness) * load * (1f - lubeCut)
		} else 0f

		// passive cooling plus whatever the coolant is worth right now. clamped so a wild
		// config can't overshoot the step and make the temperature bounce around.
		val cooling: Float = (cfg.baseCooling +
			getCoolingFactor() * getCoolantFill() * cfg.coolantCoolingMult).coerceIn(0f, 0.9f)

		temperature += heat - cooling * (temperature - ambient)
		// tops out at full overheat severity, the rest of the heat just goes nowhere
		val ceiling: Float = maxOf(cfg.criticalThreshold, cfg.overheatThreshold)
		temperature = temperature.coerceIn(ambient, ceiling)
	}

	// tips wear down only while the drill runs past the overheat threshold. hotter means
	// faster wear, and at zero durability the tip is gone for good (setNoRepair at reg).
	private fun damageTip() {
		val itemStack: ItemStack = drillTipHandler.getStackInSlot(0)
		if (itemStack.isEmpty || !itemStack.tags.anyMatch(CreateOreDepositsTags.DRILL_TIP::equals)) return
		if (level?.isClientSide == true) return

		val cfg = Config.SERVER.DEPOSIT_DRILL
		val overheat: Float = getOverheatFactor()
		if (overheat <= 0f) return

		// fractional so mild overheating wears a tip down slowly instead of not at all
		tipWearAccumulator += overheat * cfg.maxTipWearPerTick
		val damage: Int = tipWearAccumulator.toInt()
		if (damage < 1) return
		tipWearAccumulator -= damage

		val world: ServerLevel = level as? ServerLevel ?: return
		itemStack.hurtAndBreak(damage, world, null) {
			drillTipHandler.setStackInSlot(0, ItemStack.EMPTY)
			temperature = cfg.baseTemperature + 50f
			tipWearAccumulator = 0f
			notifyUpdate()
			world.playSound(null, worldPosition, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f)
		}
	}

	// overheating doesn't stop the drill anymore, it just slows it down and roughens the
	// tip. fluids are optional and only change how fast the temperature climbs.
	fun recomputeState(): DrillState {
		val cfg = Config.SERVER.DEPOSIT_DRILL
		val tip: ItemStack = drillTipHandler.getStackInSlot(0)
		val target: BlockState? = getTargetBlockState()

		if (tip.isEmpty) return DrillState.NO_TIP
		if (target != null && isDeposit(target) && !isTipSufficientFor(target)) return DrillState.INSUFFICIENT_TIP
		if (!hasOutputSpace()) return DrillState.JAMMED
		if (speed == 0f) return DrillState.NO_POWER

		if (temperature > cfg.overheatThreshold) return DrillState.OVERHEATING
		if (target != null && isDeposit(target)) return DrillState.MINING
		return DrillState.IDLE
	}

	override fun calculateStressApplied(): Float {
		val cfg = Config.SERVER.DEPOSIT_DRILL
		val lubeReduction: Float = if (getLubricantFactor() > 0f) 1f - cfg.lubeStressReduction else 1f
		return cfg.baseImpact * (1f + lastHardness * cfg.hardnessStressMult) * lubeReduction
	}

	fun getBlockHardness(blockState: BlockState?): Float {
		return blockState?.blockHolder?.getData(DEPOSIT_DATA)?.hardness ?: 1.0f
	}

	fun getLubricantFactor(): Float {
		return lubricantHandler.getFluidInTank(0)
			.fluidHolder
			.getData(LUBRICANT_FACTOR_DATA)
			?.lubeFactor ?: 0.0f
	}

	fun getCoolingFactor(): Float {
		return coolantHandler.getFluidInTank(0)
			.fluidHolder
			.getData(COOLING_FACTOR_DATA)
			?.coolingFactor ?: 0.0f
	}

	// how full each tank is, 0..1. fluids get more effective the more you feed in, so a
	// topped off tank does far more than a splash at the bottom.
	fun getLubricantFill(): Float {
		val capacity = lubricantHandler.getCapacity()
		if (capacity <= 0) return 0f
		return (lubricantHandler.getFluidInTank(0).amount.toFloat() / capacity).coerceIn(0f, 1f)
	}

	fun getCoolantFill(): Float {
		val capacity = coolantHandler.getCapacity()
		if (capacity <= 0) return 0f
		return (coolantHandler.getFluidInTank(0).amount.toFloat() / capacity).coerceIn(0f, 1f)
	}

	// 0 at or below the overheat threshold, 1 at full severity. drives both the extraction
	// slowdown and how quickly the tip wears down.
	fun getOverheatFactor(): Float {
		val cfg = Config.SERVER.DEPOSIT_DRILL
		val span = (cfg.criticalThreshold - cfg.overheatThreshold).coerceAtLeast(1f)
		return ((temperature - cfg.overheatThreshold) / span).coerceIn(0f, 1f)
	}

	// 0 = fresh tip, 1 = one hit from breaking. 0 when the tip can't take durability damage.
	fun getTipWearFraction(): Float {
		val tip: ItemStack = drillTipHandler.getStackInSlot(0)
		if (tip.isEmpty || tip.maxDamage <= 0) return 0f
		return (tip.damageValue.toFloat() / tip.maxDamage).coerceIn(0f, 1f)
	}

	private fun isDeposit(state: BlockState?): Boolean = state?.`is`(CreateOreDepositsTags.DEPOSIT) ?: false

	// smoothed heat value, used by the visual for jitter/tint and by the goggles
	fun getDisplayTemperature(): Float = displayTemperature

	fun getTargetBlock(): Block? = getTargetBlockState()?.block

	fun getTargetBlockState(): BlockState? = level?.getBlockState(getTargetPos())

	fun getTargetPos(): BlockPos = currentDepositPos ?: getDrillTipPos()

	fun getDrillTipPos(): BlockPos = blockPos.offset(0, -lerpedOffset.value.toInt() - 1, 0)

	private fun buildDepositQueue(level: Level, startPos: BlockPos) {
		bfsVisited.clear()
		bfsPositions.clear()
		bfsQueue.clear()

		bfsVisited.add(startPos)
		bfsQueue.addLast(startPos)

		while (bfsQueue.isNotEmpty()) {
			val current = bfsQueue.removeFirst()
			bfsPositions.add(current)

			for (dir in Direction.entries) {
				val neighbor = current.relative(dir)
				if (!bfsVisited.contains(neighbor) && isDeposit(level.getBlockState(neighbor))) {
					bfsVisited.add(neighbor)
					bfsQueue.addLast(neighbor)
				}
			}
		}

		depositQueue.clear()
		depositQueueSet.clear()
		bfsPositions.sortWith(compareBy<BlockPos> { it.y }.thenBy { it.x }.thenBy { it.z })
		for (pos in bfsPositions) {
			depositQueue.addLast(pos)
			depositQueueSet.add(pos)
		}
	}

	private fun resetExtractionState() {
		maxAttempts = 0
		remainingAttempts = 0
		drillTickCounter = 0
	}

	private fun initializeCurrentDeposit(level: Level) {
		clearDestroyProgress()

		val currentDeposit = currentDepositPos ?: run {
			resetExtractionState()
			return
		}

		val blockState = level.getBlockState(currentDeposit)
		if (!isDeposit(blockState)) {
			resetExtractionState()
			return
		}
		maxAttempts = blockState.blockHolder.getData(DEPOSIT_DATA)?.maxAttempts ?: 0
		remainingAttempts = maxAttempts
		drillTickCounter = 0
	}

	private fun getCurrentQueueHead(): BlockPos? = if (depositQueue.isEmpty()) null else depositQueue.first()

	private fun advanceDepositQueue(level: Level) {
		if (depositQueue.isNotEmpty()) {
			val removed = depositQueue.removeFirst()
			depositQueueSet.remove(removed)
		}
		currentDepositPos = getCurrentQueueHead()
		initializeCurrentDeposit(level)
	}

	private fun clearDepositQueue() {
		clearDestroyProgress()
		currentDepositPos = null
		depositQueue.clear()
		depositQueueSet.clear()
		resetExtractionState()
	}

	private fun readDepositQueue(nbt: CompoundTag) {
		depositQueue.clear()
		depositQueueSet.clear()
		// same shape as writeDepositQueue, a list of compounds each holding a "pos" IntArrayTag
		val positions = nbt.getList("DepositQueue", Tag.TAG_COMPOUND.toInt())
		for (index in 0 until positions.size) {
			val posTag = positions.getCompound(index)
			val blockPos = NbtUtils.readBlockPos(posTag, "pos").orElse(null) ?: continue
			depositQueue.addLast(blockPos)
			depositQueueSet.add(blockPos)
		}
	}

	private fun writeDepositQueue(nbt: CompoundTag) {
		val positions = ListTag()
		depositQueue.forEach { pos ->
			val entry = CompoundTag()
			entry.put("pos", NbtUtils.writeBlockPos(pos))
			positions.add(entry)
		}
		nbt.put("DepositQueue", positions)
	}

	fun getInterpolatedOffset(partialTicks: Float): Float =
		lerpedOffset.getValue(partialTicks).coerceAtLeast(3 / 16f)

	fun getMovementSpeed(): Float {
		var movementSpeed = convertToLinear(getSpeed())
		if (level?.isClientSide == true) movementSpeed *= ServerSpeedProvider.get()
		return movementSpeed
	}

	private fun setLerpedOffset(value: Number) {
		lerpedOffset.setValue(value.toDouble().coerceAtLeast(MIN_LERP))
	}

	override fun read(compound: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
		val nbt: CompoundTag = compound.getCompound("DepositDrill")

		maxAttempts = nbt.getInt("MaxAttempts")
		remainingAttempts = nbt.getInt("RemainingAttempts")
		drillTickCounter = nbt.getInt("DrillTickCount")
		drillOffset = nbt.getFloat("DrillOffset")
		temperature = nbt.getFloat("Temperature")
		displayTemperature = nbt.getFloat("DisplayTemperature")

		if (nbt.contains("LastBlock")) {
			lastBlock = BuiltInRegistries.BLOCK.get(NBTHelper.readResourceLocation(nbt, "LastBlock"))
		}
		if (nbt.contains("CurrentDepositPos")) {
			currentDepositPos = NBTHelper.readBlockPos(nbt, "CurrentDepositPos")
		}
		readDepositQueue(nbt)

		itemHandler.deserializeNBT(registries, nbt.getCompound("ItemHandler"))
		drillTipHandler.deserializeNBT(registries, nbt.getCompound("DrillTipHandler"))
		lubricantHandler.deserializeNBT(registries, nbt.getCompound("Lubricant"))
		coolantHandler.deserializeNBT(registries, nbt.getCompound("Coolant"))

		super.read(compound, registries, clientPacket)
	}

	override fun write(compound: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
		val nbt = CompoundTag()

		nbt.putInt("MaxAttempts", maxAttempts)
		nbt.putInt("RemainingAttempts", remainingAttempts)
		nbt.putInt("DrillTickCount", drillTickCounter)
		nbt.putFloat("DrillOffset", drillOffset)
		nbt.putFloat("Temperature", temperature)
		nbt.putFloat("DisplayTemperature", displayTemperature)

		lastBlock?.let {
			NBTHelper.writeResourceLocation(nbt, "LastBlock", BuiltInRegistries.BLOCK.getKey(it))
		}
		currentDepositPos?.let {
			nbt.put("CurrentDepositPos", NbtUtils.writeBlockPos(it))
		}
		writeDepositQueue(nbt)

		nbt.put("ItemHandler", itemHandler.serializeNBT(registries))
		nbt.put("DrillTipHandler", drillTipHandler.serializeNBT(registries))
		nbt.put("Lubricant", lubricantHandler.serializeNBT(registries))
		nbt.put("Coolant", coolantHandler.serializeNBT(registries))

		compound.put("DepositDrill", nbt)
		super.write(compound, registries, clientPacket)
	}

	override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
		translate("tooltip.drill.header").forGoggles(tooltip)

		// state first, color-coded. no redstone or comparator output for now, goggles only.
		formatDrillState(tooltip)

		// Progress
		if (currentDepositPos != null && maxAttempts > 0) {
			val attemptsUsed: Int = maxAttempts - remainingAttempts
			val stage: Int = ((attemptsUsed.toFloat() / maxAttempts) * 20f).toInt().coerceIn(0, 20)
			val bar: String = TooltipHelper.makeProgressBar(20, stage)
			translate("tooltip.drill.progress", attemptsUsed, remainingAttempts)
				.add(Component.literal(bar))
				.forGoggles(tooltip)
		}

		// heat, using the smoothed value so it doesn't jitter. shows the threshold so it's
		// obvious how close the drill is to cooking itself.
		val cfg = Config.SERVER.DEPOSIT_DRILL
		val overheat: Float = getOverheatFactor()
		val heatBar: String = makeHeatBar(displayTemperature.toInt())
		val heatColor: ChatFormatting = if (overheat > 0f) ChatFormatting.RED else ChatFormatting.GREEN
		translate("tooltip.drill.heat", displayTemperature.toInt(), cfg.overheatThreshold.toInt(), heatBar)
			.style(heatColor)
			.forGoggles(tooltip)

		// only shown while actually overheating, and spells out what it's costing you
		if (overheat > 0f) {
			val slower: Int = (overheat * cfg.overheatSlowdown * 100f).toInt()
			translate("tooltip.drill.overheat", slower, overheat * cfg.maxTipWearPerTick)
				.style(ChatFormatting.RED)
				.forGoggles(tooltip)
		}

		// tip, disposable and unrepairable
		val tipStack: ItemStack = drillTipHandler.getStackInSlot(0)
		if (!tipStack.isEmpty) {
			if (tipStack.maxDamage > 0) {
				val currentDurability: Int = tipStack.maxDamage - tipStack.damageValue
				val percentage: Int = (currentDurability * 100) / tipStack.maxDamage
				// short bar, and the tip/durability/disposable bits get their own lines. all on
				// one line this ran way past the edge of the screen
				val tipBar: String = TooltipHelper.makeProgressBar(10, (percentage / 10).coerceIn(0, 10))
				translate("tooltip.drill.tip.line", Component.translatable(tipStack.item.descriptionId))
					.style(ChatFormatting.GREEN)
					.forGoggles(tooltip)
				translate("tooltip.drill.tip.durability", currentDurability, tipStack.maxDamage, percentage, tipBar)
					.style(ChatFormatting.GREEN)
					.forGoggles(tooltip)
				translate("tooltip.drill.tip.disposable")
					.style(ChatFormatting.DARK_GRAY)
					.forGoggles(tooltip)

				// only bother mentioning the slowdown once the tip is actually worn
				val wear: Float = getTipWearFraction()
				if (wear > 0f) {
					translate("tooltip.drill.tip.worn", (wear * cfg.wornTipSlowdown * 100f).toInt())
						.style(ChatFormatting.YELLOW)
						.forGoggles(tooltip)
				}
			} else {
				translate("tooltip.drill.tip.line_no_durability", Component.translatable(tipStack.item.descriptionId))
					.style(ChatFormatting.GREEN)
					.forGoggles(tooltip)
			}
		}

		// fluids, optional. full tanks hold the temperature flat, empty ones just heat up more
		formatFluidLine(
			tooltip, "tooltip.drill.fluid.lube", ChatFormatting.GOLD,
			lubricantHandler, getLubricantFactor() * getLubricantFill()
		)
		formatFluidLine(
			tooltip, "tooltip.drill.fluid.coolant", ChatFormatting.AQUA,
			coolantHandler, getCoolingFactor() * getCoolantFill()
		)

		// stress and output count, no comparator output
		translate("tooltip.drill.stress", calculateStressApplied().toInt(), speed.toInt())
			.style(ChatFormatting.GRAY)
			.forGoggles(tooltip)
		translate("tooltip.drill.output", getOutputCount(), itemHandler.slots * 64)
			.style(ChatFormatting.GRAY)
			.forGoggles(tooltip)

		// Physical I/O hint. one long line before, split so it stays on screen
		translate("tooltip.drill.hint")
			.style(ChatFormatting.DARK_GRAY)
			.forGoggles(tooltip)
		translate("tooltip.drill.hint2")
			.style(ChatFormatting.DARK_GRAY)
			.forGoggles(tooltip)
		translate("tooltip.drill.hint3")
			.style(ChatFormatting.DARK_GRAY)
			.forGoggles(tooltip)

		return super.addToGoggleTooltip(tooltip, isPlayerSneaking)
	}

	private fun formatDrillState(tooltip: MutableList<Component>) {
		val target: BlockState? = getTargetBlockState()

		val (key, color, args) = when (drillState) {
			DrillState.MINING -> Triple(
				"tooltip.drill.state.mining",
				ChatFormatting.GREEN,
				arrayOf<Any>(Component.translatable(target?.block?.descriptionId ?: ""))
			)
			DrillState.OVERHEATING -> Triple(
				"tooltip.drill.state.overheating",
				ChatFormatting.RED,
				arrayOf<Any>(displayTemperature.toInt())
			)
			DrillState.JAMMED -> Triple("tooltip.drill.state.jammed", ChatFormatting.RED, emptyArray())
			DrillState.NO_TIP -> Triple("tooltip.drill.state.no_tip", ChatFormatting.GRAY, emptyArray())
			DrillState.INSUFFICIENT_TIP -> Triple(
				"tooltip.drill.state.insufficient",
				ChatFormatting.RED,
				arrayOf<Any>(requiredTierComponent(target))
			)
			DrillState.NO_POWER -> Triple("tooltip.drill.state.no_power", ChatFormatting.GRAY, emptyArray())
			DrillState.IDLE -> Triple("tooltip.drill.state.idle", ChatFormatting.GRAY, emptyArray())
		}
		translate(key, *args).style(color).forGoggles(tooltip)
	}

	private fun requiredTierComponent(target: BlockState?): Component {
		val tier: Int = target?.blockHolder?.getData(DEPOSIT_DATA)?.requiredTier ?: 1
		return when (tier) {
			4 -> translate("tooltip.drill.tier.diamond").component()
			3 -> translate("tooltip.drill.tier.steel").component()
			2 -> translate("tooltip.drill.tier.gold").component()
			else -> translate("tooltip.drill.tier.iron").component()
		}
	}

	private fun makeHeatBar(temp: Int): String {
		val cfg = Config.SERVER.DEPOSIT_DRILL
		val min = cfg.baseTemperature.toInt()
		// scale so the bar tops out just past full overheat severity
		val max = (cfg.criticalThreshold * 1.25f).toInt().coerceAtLeast(min + 1)
		val span = (max - min).coerceAtLeast(1)
		val stage = (((temp - min).toFloat() / span) * 12f).toInt().coerceIn(0, 12)
		return TooltipHelper.makeProgressBar(12, stage)
	}

	private fun formatFluidLine(
		tooltip: MutableList<Component>,
		key: String,
		color: ChatFormatting,
		handler: FluidHandler,
		factor: Float
	) {
		val fluidStack: FluidStack = handler.getFluidInTank(0)
		if (fluidStack.isEmpty) {
			// fluids are optional now, an empty tank is just less cooling, not a stoppage
			translate("$key.missing")
				.style(ChatFormatting.DARK_GRAY)
				.forGoggles(tooltip)
			return
		}
		translate(
			key,
			Component.translatable(fluidStack.descriptionId),
			fluidStack.amount,
			handler.getCapacity(),
			factor
		)
			.style(color)
			.forGoggles(tooltip)
	}

	private fun getOutputCount(): Int {
		var count = 0
		for (slot in 0 until itemHandler.slots) {
			count += itemHandler.getStackInSlot(slot).count
		}
		return count
	}

	// redstone is ignored and there's no comparator output yet. the config flags
	// (enableRedstonePause / enableComparatorOutput) are there for when that gets added.
	// TODO: hook up redstone pause + comparator output once those flags are on.

	// smoke + grindstone sound while mining/overheating, lava + smoke + extinguish sound
	// once it's badly overheated. client-side only.
	private fun emitDrillFeedback() {
		val level = this.level ?: return
		if (!level.isClientSide) return

		val tipPos: BlockPos = getDrillTipPos()
		val cx: Double = tipPos.x + 0.5
		val cy: Double = tipPos.y + 0.5
		val cz: Double = tipPos.z + 0.5

		when {
			// badly overheated, spitting lava and steam
			getOverheatFactor() >= 0.5f -> {
				if (level.gameTime % 20L == 0L) {
					level.addParticle(ParticleTypes.LAVA, cx, cy, cz, 0.0, 0.1, 0.0)
					level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, cx, cy, cz, 0.0, 0.2, 0.0)
					level.playLocalSound(cx, cy, cz, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1.0f, 1.0f, false)
				}
			}
			drillState == DrillState.MINING || drillState == DrillState.OVERHEATING -> {
				if (level.gameTime % 10L == 0L) {
					level.addParticle(ParticleTypes.SMOKE, cx, cy, cz, 0.0, 0.1, 0.0)
					level.playLocalSound(
						cx, cy, cz,
						SoundEvents.GRINDSTONE_USE,
						SoundSource.BLOCKS,
						0.4f,
						0.8f + speed / 512f,
						false
					)
				}
			}
			else -> Unit
		}
	}

	fun getItemHandler(direction: Direction): IItemHandler? {
		val outputSide = blockState.getValue(BlockStateProperties.HORIZONTAL_FACING).counterClockWise
		return if (direction == outputSide) itemHandler else null
	}

	// used by DepositDrillBlock.onRemove to spill the 9 output slots
	fun getOutputInventory(): IItemHandler = itemHandler

	fun getDrillTipItemHandler(): IItemHandler = drillTipHandler

	fun getFluidHandler(dir: Direction): FluidHandler? = when {
		dir.axis == Direction.Axis.Y && dir.axisDirection == Direction.AxisDirection.POSITIVE -> lubricantHandler
		dir == blockState.getValue(BlockStateProperties.HORIZONTAL_FACING).opposite -> coolantHandler
		else -> null
	}
}