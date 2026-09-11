package com.createcivilization.create_ore_deposits.registry.block.entries.deposit_drill

import com.createcivilization.create_ore_deposits.registry.block.entries.deposit_drill.DepositDrillBlockModels.DRILL_MAGNET
import com.createcivilization.create_ore_deposits.registry.block.entries.deposit_drill.DepositDrillBlockModels.HOSE
import com.createcivilization.create_ore_deposits.registry.block.entries.deposit_drill.DepositDrillBlockModels.HOSE_HALF
import com.createcivilization.create_ore_deposits.registry.block.entries.deposit_drill.DepositDrillBlockModels.HOSE_HALF_MAGNET

import com.mojang.math.Axis
import com.simibubi.create.AllPartialModels
import com.simibubi.create.AllSpriteShifts
import com.simibubi.create.content.kinetics.base.ShaftVisual
import com.simibubi.create.content.processing.burner.ScrollInstance
import com.simibubi.create.foundation.render.AllInstanceTypes
import dev.engine_room.flywheel.api.instance.Instance
import dev.engine_room.flywheel.api.instance.Instancer
import dev.engine_room.flywheel.api.model.Model
import dev.engine_room.flywheel.api.visual.DynamicVisual
import dev.engine_room.flywheel.api.visual.SectionTrackedVisual
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.lib.instance.InstanceTypes
import dev.engine_room.flywheel.lib.instance.TransformedInstance
import dev.engine_room.flywheel.lib.math.MoreMath
import dev.engine_room.flywheel.lib.model.Models
import dev.engine_room.flywheel.lib.model.baked.BakedModelBuilder
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual
import dev.engine_room.flywheel.lib.visual.util.SmartRecycler
import it.unimi.dsi.fastutil.bytes.ByteArrayList
import it.unimi.dsi.fastutil.bytes.ByteList
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.longs.LongSet
import net.createmod.catnip.animation.AnimationTickHolder
import net.createmod.catnip.math.AngleHelper
import net.createmod.catnip.render.SpriteShiftEntry
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.core.BlockPos.MutableBlockPos
import net.minecraft.core.Direction
import net.minecraft.core.SectionPos
import net.minecraft.util.Mth
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.LightLayer
import org.joml.Quaternionf
import org.joml.Quaternionfc
import java.util.function.Consumer
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.collections.indices
import kotlin.math.max

class DepositDrillBlockVisual(
	dispatcher: VisualizationContext,
	blockEntity: DepositDrillBlockEntity,
	partialTick: Float
) : ShaftVisual<DepositDrillBlockEntity>(dispatcher, blockEntity, partialTick), SimpleDynamicVisual {

	private val coil: ScrollInstance
	private val magnet: TransformedInstance
	private var tip: TransformedInstance
	private val rope: SmartRecycler<Boolean, TransformedInstance>
	private var renderedTipItem: Item

	val rotatingAbout: Direction = Direction.get(Direction.AxisDirection.POSITIVE, rotationAxis())
	val rotationAxis: Axis = Axis.of(rotatingAbout.step())

	private val lightCache = LightCache()

	// cache tip models per Item so the BakedModel doesn't get rebuilt every frame
	private val tipModelCache: HashMap<Item, Model> = HashMap()

	private var offset = 0f
	private var drillRotation = 0f

	init {
		val blockStateAngle: Float = AngleHelper.horizontalAngle(rotatingAbout)
		val rotation: Quaternionfc = Quaternionf().rotationY(Mth.DEG_TO_RAD * blockStateAngle)

		this.coil = this.coilModel.createInstance()
			.rotation(rotation)
			.position(visualPosition)
			.setSpriteShift(this.coilAnimation)

		this.coil.setChanged()

		this.magnet = magnetInstancer().createInstance()

		this.renderedTipItem = currentTipRenderStack().item
		this.tip = tipInstanceFor(currentTipRenderStack())

		this.rope = SmartRecycler<Boolean, TransformedInstance> { b: Boolean -> if (b) this.halfRopeModel.createInstance() else this.ropeModel.createInstance() }

		updateState(partialTick)
		updateLight(partialTick)
		animate()
	}

	override fun setSectionCollector(sectionCollector: SectionTrackedVisual.SectionCollector?) {
		super.setSectionCollector(sectionCollector)
		this.lightCache.updateSections()
	}

	val ropeModel: Instancer<TransformedInstance> get() =
		instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(HOSE))

	val magnetModel: Instancer<TransformedInstance> get() =
		instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(DRILL_MAGNET))

	val halfMagnetModel: Instancer<TransformedInstance> get() =
		instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(HOSE_HALF_MAGNET))

	val coilModel: Instancer<ScrollInstance> get() =
		instancerProvider().instancer(AllInstanceTypes.SCROLLING, Models.partial(AllPartialModels.HOSE_COIL))

	val halfRopeModel: Instancer<TransformedInstance> get() =
		instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(HOSE_HALF))

	fun getOffset(pt: Float): Float = blockEntity.getInterpolatedOffset(pt)

	private fun currentTipRenderStack(): ItemStack =
		this.blockEntity.getDrillTipItemHandler().getStackInSlot(0)

	private fun cachedTipModel(stack: ItemStack): Model {
		return tipModelCache.getOrPut(stack.item) {
			val bakedModel: BakedModel = Minecraft.getInstance().itemRenderer.getModel(stack, null, null, 0)
			BakedModelBuilder(bakedModel).build()
		}
	}

	private fun tipInstanceFor(stack: ItemStack): TransformedInstance {
		// no tip, make an instance from the cached model and just keep it hidden in animate()
		val model: Model = cachedTipModel(if (stack.isEmpty) ItemStack(Items.NETHERITE_BLOCK) else stack)
		return instancerProvider().instancer(InstanceTypes.TRANSFORMED, model).createInstance()
	}

	private fun refreshTipInstance() {
		val stack = currentTipRenderStack()
		if (stack.item == renderedTipItem) return
		tip.delete()
		renderedTipItem = stack.item
		tip = tipInstanceFor(stack)
	}

	val coilAnimation: SpriteShiftEntry get() = AllSpriteShifts.HOSE_PULLEY_COIL

	private fun magnetInstancer(): Instancer<TransformedInstance> = if (offset > .25f) this.magnetModel else this.halfMagnetModel

	override fun beginFrame(ctx: DynamicVisual.Context) {
		updateState(ctx.partialTick())
		animate()
	}

	private fun animate() {
		val stack: ItemStack = blockEntity.getDrillTipItemHandler().getStackInSlot(0)
		refreshTipInstance()

		coil.offsetV = -offset
		coil.setChanged()

		// magnet and tip hang at the very end of the rope, so they show whenever the rope is
		// out. used to hide them unless the drill was working, which made a fresh tip invisible
		magnet.setVisible(true)
		tip.setVisible(!stack.isEmpty)

		magnetInstancer().stealInstance(magnet)

		magnet.setIdentityTransform()
			.translate(visualPosition)
			.translate(0f, -offset, 0f)
			.light(lightCache.getPackedLight(max(0, Mth.floor(offset))))
			.setChanged()

		// overheated drills jitter a bit, critical ones glow red
		val temp: Float = blockEntity.getDisplayTemperature()
		var rotation: Float = drillRotation
		if (temp > 600f) rotation += ((AnimationTickHolder.getRenderTime(blockEntity.getLevel()!!) % 2.0) - 1.0).toFloat()
		if (temp > 900f) {
			tip.color(1f, 0.35f, 0.35f)
		} else {
			tip.color(1f, 1f, 1f)
		}

		tip.setIdentityTransform()
			.translate(visualPosition)
			.translate(0f, -offset - 2/16f, 0f)
			.center()
			.rotateYDegrees(rotation)
			.uncenter()
			.light(lightCache.getPackedLight(max(0, Mth.floor(offset))))
			.setChanged()

		rope.resetCount()

		if (shouldRenderHalfRope()) {
			// what does 'f' represent ?
			val f: Float = offset % 1
			val halfRopeNudge: Float = if (f > .75f) f - 1 else f

			rope.get(true)!!.setIdentityTransform()
				.translate(visualPosition)
				.translate(0f, -halfRopeNudge, 0f)
				.light(lightCache.getPackedLight(0))
				.setChanged()
		}

		// rope segments come from how far the drill is lowered, not from whether it happens to
		// be mining. neededRopeCount is 0 while the rope is still short
		val neededRopeCount: Int = this.neededRopeCount

		for (i in 0..<neededRopeCount) {
			rope.get(false)!!
				.setIdentityTransform()
				.translate(visualPosition)
				.translate(0f, -offset + i + 1, 0f)
				.light(lightCache.getPackedLight(neededRopeCount - 1 - i))
				.setChanged()
		}

		rope.discardExtra()
	}

	override fun updateLight(partialTick: Float) {
		super.updateLight(partialTick)
		relight(coil)

		lightCache.update()
	}

	private fun updateState(pt: Float) {
		offset = getOffset(pt)
		drillRotation = (AnimationTickHolder.getRenderTime(blockEntity.getLevel()!!) * blockEntity.getSpeed() * 3f / 10 + offset) % 360
		lightCache.setSize(Mth.ceil(offset) + 2)
	}

	override fun _delete() {
		super._delete()
		coil.delete()
		magnet.delete()
		rope.delete()
		tip.delete()
	}

	private val neededRopeCount: Int
		get() = max(0, Mth.ceil(offset - 1.25f))

	private fun shouldRenderHalfRope(): Boolean {
		val f = offset % 1
		return offset > .75f && (f !in .25f.. .75f)
	}

	override fun collectCrumblingInstances(consumer: Consumer<Instance?>) {
		super.collectCrumblingInstances(consumer)
		consumer.accept(coil)
		consumer.accept(magnet)
	}

	private inner class LightCache {
		private val data: ByteList = ByteArrayList()
		private val sections: LongSet = LongOpenHashSet()
		private val mutablePos = MutableBlockPos()
		private var sectionCount = 0

		fun setSize(size: Int) {
			if (size != data.size) {
				data.size(size)
				update()

				val sectionCount: Int = MoreMath.ceilingDiv(size + 15 - pos.y + pos.y / 4 * 4, SectionPos.SECTION_SIZE)
				if (sectionCount != this.sectionCount) {
					this.sectionCount = sectionCount
					sections.clear()
					val sectionX: Int = SectionPos.blockToSectionCoord(pos.x)
					val sectionY: Int = SectionPos.blockToSectionCoord(pos.y)
					val sectionZ: Int = SectionPos.blockToSectionCoord(pos.z)
					for (i in 0..<sectionCount) {
						sections.add(SectionPos.asLong(sectionX, sectionY - i, sectionZ))
					}
					// Will be null during initialization
					if (lightSections != null) {
						updateSections()
					}
				}
			}
		}

		fun updateSections() {
			lightSections.sections(sections)
		}

		fun update() {
			mutablePos.set(pos)

			for (index: Int in data.indices) {
				val blockLight: Int = level.getBrightness(LightLayer.BLOCK, mutablePos)
				val skyLight: Int = level.getBrightness(LightLayer.SKY, mutablePos)
				val light: Int = ((skyLight and 0xF) shl 4) or (blockLight and 0xF)
				data.set(index, light.toByte())
				mutablePos.move(Direction.DOWN)
			}
		}

		fun getPackedLight(offset: Int): Int {
			if (offset < 0 || offset >= data.size) {
				return 0
			}

			val light: Int = java.lang.Byte.toUnsignedInt(data.getByte(offset))
			val blockLight: Int = light and 0xF
			val skyLight: Int = (light ushr 4) and 0xF
			return LightTexture.pack(blockLight, skyLight)
		}
	}
}