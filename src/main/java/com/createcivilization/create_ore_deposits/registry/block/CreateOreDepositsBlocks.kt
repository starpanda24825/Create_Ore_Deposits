package com.createcivilization.create_ore_deposits.registry.block

import com.createcivilization.create_ore_deposits.CreateOreDeposits.REGISTRATE
import com.createcivilization.create_ore_deposits.config.Config
import com.createcivilization.create_ore_deposits.registry.block.entries.cast.CastBlock
import com.createcivilization.create_ore_deposits.registry.block.entries.deposit_drill.DepositDrillBlock
import com.createcivilization.create_ore_deposits.registry.tag.CreateOreDepositsTags
import com.createcivilization.create_ore_deposits.registry.item.CreateOreDepositsItems
import com.simibubi.create.api.behaviour.movement.MovementBehaviour.movementBehaviour
import com.simibubi.create.api.stress.BlockStressValues
import com.simibubi.create.content.kinetics.drill.DrillMovementBehaviour
import com.simibubi.create.foundation.data.ModelGen.customItemModel

import com.simibubi.create.foundation.data.TagGen.pickaxeOnly
import com.tterrag.registrate.providers.loot.RegistrateBlockLootTables

import net.minecraft.tags.TagKey
import com.tterrag.registrate.util.entry.BlockEntry
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.ItemLike
import net.minecraft.world.level.block.RotatedPillarBlock
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.storage.loot.LootPool
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.level.storage.loot.entries.LootItem
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue

data object CreateOreDepositsBlocks {

	@JvmField
	val DRILL_BLOCK: BlockEntry<DepositDrillBlock> = REGISTRATE.block("deposit_drill", ::DepositDrillBlock)
		.initialProperties { Blocks.IRON_BLOCK }
		.properties { it.noOcclusion() }
		.transform(pickaxeOnly())
		.onRegister(movementBehaviour(DrillMovementBehaviour()))
		.onRegister { block ->
			BlockStressValues.IMPACTS.register(block) { Config.SERVER.DEPOSIT_DRILL.stressPerRpm.toDouble() }
		}
		.item()
		.transform(customItemModel())
		.register()

	@JvmField
	val CAST: BlockEntry<CastBlock> = registerCast("cast")

	@JvmField
	val LUBRICATED_CAST: BlockEntry<CastBlock> = registerCast("lubricated_cast")

	@JvmField
	val CASTED_IRON: BlockEntry<CastBlock> = registerCast("casted_iron")

	@JvmField
	val CASTED_GOLD: BlockEntry<CastBlock> = registerCast("casted_gold")

	@JvmField
	val CASTED_COPPER: BlockEntry<CastBlock> = registerCast("casted_copper")

	@JvmField
	val CASTED_BRASS: BlockEntry<CastBlock> = registerCast("casted_brass")

	@JvmField
	val CASTED_STEEL: BlockEntry<CastBlock> = registerCast("casted_steel")

	@JvmField
	val CASTED_NETHERITE: BlockEntry<CastBlock> = registerCast("casted_netherite")

	@JvmField
	val STEEL_BLOCK: BlockEntry<Block> = REGISTRATE.block("steel_block", ::Block)
		.initialProperties { Blocks.IRON_BLOCK }
		.simpleItem()
		.register()

	// DEPOSIT LOOT:
	// Hardcoded. One row per ore, columns always in this order:
	//   rolls  - roll attempts per deposit block (whole numbers only)
	//   chance - odds that one roll drops anything (0.0 - 1.0)
	//   count  - items handed out per successful roll
	//   pillar - true only for deposits that render as a pillar (netherite)
	//   tip    - lowest drill tip tier that can mine it (tag)
	//
	// Yield per block = maxAttempts x rolls x chance x count.
	// maxAttempts / hardness live in DataMapProvider.kt (DEPOSIT_DATA).



	val COAL_ORE_DEPOSIT: BlockEntry<Block> = registerDeposit(
		"coal_ore_deposit", Blocks.COAL_ORE, CreateOreDepositsItems.UNREFINED_COAL_ORE,
		3f, 0.60f, 1) // bulk fuel, three rolls; one block for both stone and deepslate levels
	val IRON_ORE_DEPOSIT: BlockEntry<Block> = registerDepositSingleRoll(
		"iron_ore_deposit", Blocks.IRON_ORE, CreateOreDepositsItems.UNREFINED_IRON_ORE,
		0.75f, 2) // core metal, two each
	val DEEPSLATE_IRON_ORE_DEPOSIT: BlockEntry<Block> = registerDepositSingleRoll(
		"deepslate_iron_ore_deposit", Blocks.DEEPSLATE_IRON_ORE, CreateOreDepositsItems.UNREFINED_IRON_ORE,
		0.75f, 2, false, CreateOreDepositsTags.NEEDS_GOLD_TIP) // same yield as iron, one tier up
	val COPPER_ORE_DEPOSIT: BlockEntry<Block> = registerDepositSingleRoll(
		"copper_ore_deposit", Blocks.COPPER_ORE, CreateOreDepositsItems.UNREFINED_COPPER_ORE,
		0.70f, 2) // early metal, two each
	val DEEPSLATE_COPPER_ORE_DEPOSIT: BlockEntry<Block> = registerDepositSingleRoll(
		"deepslate_copper_ore_deposit", Blocks.DEEPSLATE_COPPER_ORE, CreateOreDepositsItems.UNREFINED_COPPER_ORE,
		0.70f, 2, false, CreateOreDepositsTags.NEEDS_GOLD_TIP) // same yield as copper, one tier up
	val QUARTZ_ORE_DEPOSIT: BlockEntry<Block> = registerDeposit(
		"quartz_ore_deposit", Blocks.NETHER_QUARTZ_ORE, CreateOreDepositsItems.UNREFINED_QUARTZ_ORE,
		1f, 0.50f, 2) // utility, two each
// vanilla has no deepslate quartz, so plain deepslate is the closest stand-in for its twin
	val DEEPSLATE_QUARTZ_ORE_DEPOSIT: BlockEntry<Block> = registerDeposit(
		"deepslate_quartz_ore_deposit", Blocks.DEEPSLATE, CreateOreDepositsItems.UNREFINED_QUARTZ_ORE,
		1f, 0.50f, 2, false, CreateOreDepositsTags.NEEDS_GOLD_TIP) // same yield as quartz, one tier up
	val LAPIS_ORE_DEPOSIT: BlockEntry<Block> = registerDepositSingleRoll(
		"lapis_ore_deposit", Blocks.LAPIS_ORE, CreateOreDepositsItems.UNREFINED_LAPIS_ORE,
		0.65f, 1) // utility, single
	val DEEPSLATE_LAPIS_ORE_DEPOSIT: BlockEntry<Block> = registerDepositSingleRoll(
		"deepslate_lapis_ore_deposit", Blocks.DEEPSLATE_LAPIS_ORE, CreateOreDepositsItems.UNREFINED_LAPIS_ORE,
		0.65f, 1, false, CreateOreDepositsTags.NEEDS_GOLD_TIP) 
	val REDSTONE_ORE_DEPOSIT: BlockEntry<Block> = registerDepositSingleRoll(
		"redstone_ore_deposit", Blocks.IRON_ORE, Items.REDSTONE,
		0.75f, 2) // plain dust for now, no processing branch yet
	val DEEPSLATE_REDSTONE_ORE_DEPOSIT: BlockEntry<Block> = registerDepositSingleRoll(
		"deepslate_redstone_ore_deposit", Blocks.DEEPSLATE_IRON_ORE, Items.REDSTONE,
		0.75f, 2, false, CreateOreDepositsTags.NEEDS_GOLD_TIP) // same yield as redstone, one tier up
	val GOLD_ORE_DEPOSIT: BlockEntry<Block> = registerDepositSingleRoll(
		"gold_ore_deposit", Blocks.GOLD_ORE, CreateOreDepositsItems.UNREFINED_GOLD_ORE,
		0.50f, 1, false, CreateOreDepositsTags.NEEDS_GOLD_TIP) // tier 2 metal
	val DEEPSLATE_GOLD_ORE_DEPOSIT: BlockEntry<Block> = registerDepositSingleRoll(
		"deepslate_gold_ore_deposit", Blocks.DEEPSLATE_GOLD_ORE, CreateOreDepositsItems.UNREFINED_GOLD_ORE,
		0.50f, 1, false, CreateOreDepositsTags.NEEDS_STEEL_TIP) // same yield as gold, steel tip only gains deepslate
	val NETHERITE_ORE_DEPOSIT: BlockEntry<Block> = registerDepositSingleRoll(
		"netherite_ore_deposit", Blocks.ANCIENT_DEBRIS, Items.ANCIENT_DEBRIS,
		0.07f, 1, true, CreateOreDepositsTags.NEEDS_DIAMOND_TIP)
//	val ZINC_ORE_DEPOSIT: BlockEntry<Block> = registerDeposit("zinc_ore_deposit", AllBlocks.ZINC_ORE.get(), CreateOreDepositsItems.UNREFINED_ZINC)

	private fun registerCast(blockName: String): BlockEntry<CastBlock> = REGISTRATE.block(blockName, ::CastBlock)
		.initialProperties { Blocks.WHITE_WOOL }
		.properties { it.noOcclusion().strength(0.5f).sound(SoundType.STONE) }
		.loot { lootTables: RegistrateBlockLootTables, castBlock: CastBlock ->
			lootTables.dropSelf(castBlock)
		}
		.simpleItem()
		.register()

	fun registerDepositSingleRoll(
		blockName: String,
		block: Block,
		ore: ItemLike,
		chance: Float,
		count: Int = 1,
		isRotatedPillar: Boolean = false,
		vararg requiredTipTags: TagKey<Block>
	): BlockEntry<Block> = registerDeposit(blockName, block, ore, 1f, chance, count, isRotatedPillar, *requiredTipTags)

	fun registerDeposit(
		blockName: String,
		block: Block,
		ore: ItemLike,
		rolls: Float,
		chance: Float,
		count: Int = 1,
		isRotatedPillar: Boolean = false,
		vararg requiredTipTags: TagKey<Block>
	): BlockEntry<Block> {
		var builder = REGISTRATE
			.block(blockName) {
				if (isRotatedPillar) {
					RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(block))
				} else {
					Block(BlockBehaviour.Properties.ofFullCopy(block))
				}
			}
			.simpleItem()
			.tag(CreateOreDepositsTags.DEPOSIT)

		requiredTipTags.forEach { requiredTipTag ->
			builder = builder.tag(requiredTipTag)
		}

		return builder
			.loot { lootTables: RegistrateBlockLootTables, depositBlock: Block ->
				lootTables.add(
					depositBlock,
					LootTable.lootTable()
						.withPool(
							LootPool.lootPool()
								.setRolls(ConstantValue.exactly(rolls))
								.add(
									LootItem.lootTableItem(ore)
										.`when`(LootItemRandomChanceCondition.randomChance(chance))
										.apply(SetItemCountFunction.setCount(ConstantValue.exactly(count.toFloat())))
								)
						)
				)
			}
			.register()
	}
}
