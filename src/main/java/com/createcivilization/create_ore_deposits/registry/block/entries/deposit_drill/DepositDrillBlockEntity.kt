package com.createcivilization.create_ore_deposits.registry.block.entries.deposit_drill

import com.createcivilization.create_ore_deposits.config.Config
import com.createcivilization.create_ore_deposits.registry.datamap.CreateOreDepositsDataMaps.COOLING_FACTOR_DATA
import com.createcivilization.create_ore_deposits.registry.datamap.CreateOreDepositsDataMaps.DEPOSIT_DATA
import com.createcivilization.create_ore_deposits.registry.datamap.CreateOreDepositsDataMaps.LUBRICANT_FACTOR_DATA
import com.createcivilization.create_ore_deposits.registry.fluid.CreateOreDepositsFluids
import com.createcivilization.create_ore_deposits.registry.fluid.FluidHandler
import com.createcivilization.create_ore_deposits.registry.tag.CreateOreDepositsTags
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
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
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

class DepositDrillBlockEntity(
	type: BlockEntityType<*>,
	pos: BlockPos,
	blockState: BlockState
) : BlockBreakingKineticBlockEntity(type, pos, blockState) {

	private var drillOffset: Float = 0f
	private var lerpedOffset: LerpedFloat = LerpedFloat.linear().startWithValue(MIN_LERP)
	private var lastBlock: Block? = null
	private var temperature: Float = Config.SERVER.DEPOSIT_DRILL.baseTemperature
	// Lerped copy of temperature, used for the goggles heat readout so it doesn't jitter.
	private var displayTemperature: Float = Config.SERVER.DEPOSIT_DRILL.baseTemperature
	// Hardness snapshot taken once at tick start (fixes heat jitter, see tick()).
	private var lastHardness: Float = 1f
	private var maxAttempts: Int = 0
	private var remainingAttempts: Int = 0
	private var drillTickCounter: Int = 0
	private var currentDepositPos: BlockPos? = null
	private var isActuallyMiningSnapshot: Boolean = false

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

		// FIXME (heat jitter): snapshot the target state once per tick — getTargetBlockState()
		// flips between currentDepositPos and drillTipPos during queue rebuilds, which made
		// hardness flicker and the temperature readout jump. Hardness and mining are passed in.
		val snapTargetState: BlockState? = getTargetBlockState()
		val snapIsDeposit: Boolean = isDeposit(snapTargetState)
		val snapHardness: Float = getBlockHardness(snapTargetState)
		lastHardness = snapHardness
		val snapCanMineBasic: Boolean =
			snapIsDeposit && speed != 0f && !drillTipHandler.getStackInSlot(0).isEmpty
		isActuallyMiningSnapshot = snapCanMineBasic

		updateTemperature(snapIsDeposit && snapCanMineBasic, snapHardness)
		displayTemperature = Mth.lerp(0.08f, displayTemperature, temperature)

		onBreakTick()
		damageTip()
	}

	override fun lazyTick() {
		super.lazyTick()
		setChanged()
		sendData()
		// Only drain fluids while actually mining a deposit — previously drained on any speed,
		// even when idle/jammed/fluid-starved.
		if (!isActuallyMiningSnapshot) return
		lubricantHandler.drain(1, FluidAction.EXECUTE)
		coolantHandler.drain(1, FluidAction.EXECUTE)
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

		if (remainingAttempts <= 0) return

		drillTickCounter++

		if (drillTickCounter >= calculateExtractionInterval()) {
			drillTickCounter = 0
			remainingAttempts--

			val serverLevel: ServerLevel = level as ServerLevel
			for (stack in getSimulatedDrops(blockState, serverLevel, targetPos)) {
				insertOutput(stack)
			}

			updateDestroyProgress(targetPos)

			if (remainingAttempts <= 0) {
				level.destroyBlockProgress(blockPos.hashCode(), targetPos, -1)
				level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), 3)
				advanceDepositQueue(level)
			}
		}
	}

	fun canMine(): Boolean {
		val tip: ItemStack = drillTipHandler.getStackInSlot(0)
		if (tip.isEmpty || !tip.tags.anyMatch(CreateOreDepositsTags.DRILL_TIP::equals)) return false
		return hasOutputSpace()
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

	fun hasFluids(): Boolean =
		!lubricantHandler.getFluidInTank(0).isEmpty && !coolantHandler.getFluidInTank(0).isEmpty

	// Used by DepositDrillBlock.onRemove to spill the 9 output slots.
	fun getOutputInventory(): IItemHandler = itemHandler

	private fun insertOutput(stack: ItemStack) {
		var remaining = stack.copy()
		for (slot in 0 until itemHandler.slots) {
			if (remaining.isEmpty) break
			remaining = itemHandler.insertItem(slot, remaining, false)
		}
	}

	fun calculateExtractionInterval(): Int = (1025 - (speed * 4).roundToInt()).coerceIn(12, 600)

	fun getSimulatedDrops(state: BlockState, serverLevel: ServerLevel, pos: BlockPos): List<ItemStack> {
		return Block.getDrops(state, serverLevel, pos, null, null, ItemStack.EMPTY)
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

	// FIXME: temps jumping around in the tooltip was caused by the hardness flicker between
	// currentDepositPos and drillTipPos — hardness is now snapshotted in tick() and passed in.
	fun updateTemperature(isMining: Boolean, hardness: Float) {
		val baseCooling: Float = Config.SERVER.DEPOSIT_DRILL.baseCooling
		val baseTemperature: Float = Config.SERVER.DEPOSIT_DRILL.baseTemperature

		val heatGen: Float = if (isMining) speed * hardness else 0.0f

		val dissipation: Float = (baseCooling + getLubricantFactor() + getCoolingFactor()).coerceAtLeast(0.1f)
		val equilibriumTemp: Float = baseTemperature + (heatGen / dissipation)
		val approachRate: Float = (0.02f * dissipation).coerceIn(0.01f, 1.0f)

		temperature += (equilibriumTemp - temperature) * approachRate
	}

	private fun damageTip() {
		val itemStack: ItemStack = drillTipHandler.getStackInSlot(0)
		if (itemStack.isEmpty || !itemStack.tags.anyMatch(CreateOreDepositsTags.DRILL_TIP::equals)) return

		val excessTemp: Float = (temperature - Config.SERVER.DEPOSIT_DRILL.baseTemperature).coerceAtLeast(0f)

		val damage: Int = when {
			excessTemp < 20f -> 0
			excessTemp < 50f -> 1
			excessTemp < 100f -> ((excessTemp - 50f) / 25f).toInt() + 1
			else -> ((excessTemp - 100f) / 20f + 3f).toInt().coerceAtMost(8)
		}

		if (damage < 1) return
		// tick() runs on both sides — this previously crashed the client by casting to ServerLevel.
		if (level?.isClientSide == true) return
		val world: ServerLevel = level as? ServerLevel ?: return

		itemStack.hurtAndBreak(damage, world, null) {
			drillTipHandler.setStackInSlot(0, ItemStack.EMPTY)
			notifyUpdate()
		}
	}

	override fun calculateStressApplied(): Float {
		val lubricantFactor: Float = getLubricantFactor()
		val hardness: Float = getBlockHardness(getTargetBlockState())
		return 128 * (4 - lubricantFactor) * hardness
	}

	fun getBlockHardness(blockState: BlockState?): Float {
		return blockState?.blockHolder?.getData(DEPOSIT_DATA)?.hardness ?: 1.0f
	}

	fun getLubricantFactor(): Float {
		// FIXED: was reading tank index 1 on a 1-tank handler, always returning 0.
		return lubricantHandler.getFluidInTank(0)
			.fluidHolder
			.getData(LUBRICANT_FACTOR_DATA)
			?.lubeFactor ?: 0.0f
	}

	fun getCoolingFactor(): Float {
		// FIXED: was reading LUBRICANT_FACTOR_DATA (lubeFactor) instead of COOLING_FACTOR_DATA.
		return coolantHandler.getFluidInTank(0)
			.fluidHolder
			.getData(COOLING_FACTOR_DATA)
			?.coolingFactor ?: 0.0f
	}

	private fun isDeposit(state: BlockState?): Boolean = state?.`is`(CreateOreDepositsTags.DEPOSIT) ?: false

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
		// Symmetric with writeDepositQueue: list of compounds each holding a "pos" IntArrayTag.
		// Previously read raw "X"/"Y"/"Z" ints off the (now IntArray) tags, corrupting the queue.
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

		// Currently Drilling
		val targetBlock: Block? = getTargetBlockState()?.block
		if (targetBlock != null && targetBlock != Blocks.AIR) {
			translate("tooltip.drill.drilling", Component.translatable(targetBlock.descriptionId))
				.style(ChatFormatting.GRAY)
				.forGoggles(tooltip)
		}

		// Breaking Progress
		if (currentDepositPos != null && maxAttempts > 0) {
			val attemptsUsed: Int = maxAttempts - remainingAttempts
			val stage: Int = ((attemptsUsed.toFloat() / maxAttempts) * 10f).toInt().coerceIn(0, 10)
			val bar: String = TooltipHelper.makeProgressBar(10, stage)
			translate("tooltip.drill.progress")
				.add(Component.literal(bar))
				.forGoggles(tooltip)
		}

		// Drill Tip
		val tipHandler: IItemHandler = getDrillTipItemHandler()
		if (!tipHandler.getStackInSlot(0).isEmpty) {
			val tipStack: ItemStack = tipHandler.getStackInSlot(0)

			translate("tooltip.drill.tip.contains", Component.translatable(tipStack.item.descriptionId))
				.style(ChatFormatting.GREEN)
				.forGoggles(tooltip)

			if (tipStack.maxDamage > 0) {
				val currentDurability: Int = tipStack.maxDamage - tipStack.damageValue
				val percentage: Int = (currentDurability * 100) / tipStack.maxDamage
				translate("tooltip.drill.tip.durability", currentDurability, tipStack.maxDamage, percentage)
					.style(ChatFormatting.YELLOW)
					.forGoggles(tooltip)
			}
		}

		// Lubricant
		val lubeInTank: FluidStack = lubricantHandler.getFluidInTank(0)
		if (!lubeInTank.isEmpty) {
			translate("tooltip.drill.contains.lube",
				Component.translatable(lubeInTank.descriptionId),
				lubeInTank.amount)
				.style(ChatFormatting.GOLD)
				.forGoggles(tooltip)
		}

		// Coolant
		val coolantInTank: FluidStack = coolantHandler.getFluidInTank(0)
		if (!coolantInTank.isEmpty) {
			translate("tooltip.drill.contains.coolant",
				Component.translatable(coolantInTank.descriptionId),
				coolantInTank.amount)
				.style(ChatFormatting.AQUA)
				.forGoggles(tooltip)
		}

		// Heat
		translate("tooltip.drill.heat", displayTemperature.toInt())
			.style(ChatFormatting.RED)
			.forGoggles(tooltip)

		return super.addToGoggleTooltip(tooltip, isPlayerSneaking)
	}

	fun getItemHandler(direction: Direction): IItemHandler? {
		val outputSide = blockState.getValue(BlockStateProperties.HORIZONTAL_FACING).counterClockWise
		return if (direction == outputSide) itemHandler else null
	}

	fun getDrillTipItemHandler(): IItemHandler = drillTipHandler

	fun getFluidHandler(dir: Direction): FluidHandler? = when {
		dir.axis == Direction.Axis.Y && dir.axisDirection == Direction.AxisDirection.POSITIVE -> lubricantHandler
		dir == blockState.getValue(BlockStateProperties.HORIZONTAL_FACING).opposite -> coolantHandler
		else -> null
	}
}