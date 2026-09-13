package com.createcivilization.create_ore_deposits.registry.datagen

import com.createcivilization.create_ore_deposits.registry.block.CreateOreDepositsBlocks
import com.createcivilization.create_ore_deposits.registry.datamap.CreateOreDepositsDataMaps
import com.createcivilization.create_ore_deposits.registry.fluid.CreateOreDepositsFluids
import com.createcivilization.create_ore_deposits.registry.item.CreateOreDepositsItems
import net.minecraft.core.HolderLookup
import net.minecraft.data.PackOutput
import net.minecraft.tags.FluidTags
import net.neoforged.neoforge.common.data.DataMapProvider
import java.util.concurrent.CompletableFuture

// All deposit vein tuning now lives in the server config so it can be edited from one place without regenerating data.
//Only static per-block drill metadata remains in data maps.

class DataMapProvider(
	packOutput: PackOutput,
	lookupProvider: CompletableFuture<HolderLookup.Provider>
) : DataMapProvider(packOutput, lookupProvider) {

	override fun gather(provider: HolderLookup.Provider) {
		// PER-ORE DRILL BALANCE
		// Hardcoded. One row per ore, columns always in this order:
		// DepositData(maxAttempts, hardness, requiredTier)
		// maxAttempts   - extraction attempts one deposit block is worth
		// hardness      - slows the interval and adds heat (hardnessTickPenalty)
		// requiredTier  - lowest drill tip tier that can mine it
		//
		// Yield per block = maxAttempts x rolls x chance x count.
		// rolls / chance / count live in CreateOreDepositsBlocks.kt (deposit loot).


		builder(CreateOreDepositsDataMaps.DEPOSIT_DATA)
			//                          maxAtt  hardness  tier
			.add(CreateOreDepositsBlocks.EXAMPLE_DEPOSIT, CreateOreDepositsDataMaps.DepositData(20, 1.0f, 1), false) // dev block
			.add(CreateOreDepositsBlocks.COAL_ORE_DEPOSIT, CreateOreDepositsDataMaps.DepositData(6, 1.2f, 1), false) // bulk fuel
			.add(CreateOreDepositsBlocks.IRON_ORE_DEPOSIT, CreateOreDepositsDataMaps.DepositData(8, 1.8f, 1), false) // core metal
			.add(CreateOreDepositsBlocks.COPPER_ORE_DEPOSIT, CreateOreDepositsDataMaps.DepositData(6, 1.4f, 1), false) // early metal
			.add(CreateOreDepositsBlocks.QUARTZ_ORE_DEPOSIT, CreateOreDepositsDataMaps.DepositData(6, 1.5f, 1), false) // utility
			.add(CreateOreDepositsBlocks.LAPIS_ORE_DEPOSIT, CreateOreDepositsDataMaps.DepositData(6, 1.6f, 1), false) // utility
			.add(CreateOreDepositsBlocks.GOLD_ORE_DEPOSIT, CreateOreDepositsDataMaps.DepositData(6, 2.6f, 2), false) // tier 2 metal
			.add(CreateOreDepositsBlocks.DIAMOND_ORE_DEPOSIT, CreateOreDepositsDataMaps.DepositData(10, 3.4f, 3), false) // rare
			.add(CreateOreDepositsBlocks.EMERALD_ORE_DEPOSIT, CreateOreDepositsDataMaps.DepositData(8, 3.0f, 3), false) // rare
			.add(CreateOreDepositsBlocks.NETHERITE_ORE_DEPOSIT, CreateOreDepositsDataMaps.DepositData(12, 5.0f, 4), false) // endgame

		builder(CreateOreDepositsDataMaps.COOLING_FACTOR_DATA)
			.add(FluidTags.WATER, CreateOreDepositsDataMaps.CoolingFactorData(1.8f), false)

		builder(CreateOreDepositsDataMaps.LUBRICANT_FACTOR_DATA)
			.add(CreateOreDepositsFluids.LUBRICANT.get().source.builtInRegistryHolder(), CreateOreDepositsDataMaps.LubricantFactorData(2.5f), false)
			.add(CreateOreDepositsFluids.LUBRICANT.get().builtInRegistryHolder(), CreateOreDepositsDataMaps.LubricantFactorData(2.5f), false)

		// Answer 6: tip tiers — mirrors the IRON/GOLD/STEEL/DIAMOND_TIP_TIER tags for redundancy
		builder(CreateOreDepositsDataMaps.TIP_TIER_DATA)
			.add(CreateOreDepositsItems.IRON_DRILL_TIP, CreateOreDepositsDataMaps.TipTierData(1), false)
			.add(CreateOreDepositsItems.GOLD_DRILL_TIP, CreateOreDepositsDataMaps.TipTierData(2), false)
			.add(CreateOreDepositsItems.STEEL_DRILL_TIP, CreateOreDepositsDataMaps.TipTierData(3), false)
			.add(CreateOreDepositsItems.DIAMOND_DRILL_TIP, CreateOreDepositsDataMaps.TipTierData(4), false)
	}
}