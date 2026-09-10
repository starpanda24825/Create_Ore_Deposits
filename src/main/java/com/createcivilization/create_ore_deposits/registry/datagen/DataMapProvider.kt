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
		// Retuned for Answer 1A: finite veins, ~20 ores/min with fluids @128 RPM.
		// maxAttempts/hardness/requiredTier — the old values were inverted (coal 1000 vs diamond 50).
		builder(CreateOreDepositsDataMaps.DEPOSIT_DATA)
			.add(CreateOreDepositsBlocks.EXAMPLE_DEPOSIT, CreateOreDepositsDataMaps.DepositData(20, 1.0f, 1), false)
			.add(CreateOreDepositsBlocks.COAL_ORE_DEPOSIT, CreateOreDepositsDataMaps.DepositData(120, 1.2f, 1), false)
			.add(CreateOreDepositsBlocks.COPPER_ORE_DEPOSIT, CreateOreDepositsDataMaps.DepositData(140, 1.4f, 1), false)
			.add(CreateOreDepositsBlocks.IRON_ORE_DEPOSIT, CreateOreDepositsDataMaps.DepositData(180, 1.8f, 1), false)
			.add(CreateOreDepositsBlocks.QUARTZ_ORE_DEPOSIT, CreateOreDepositsDataMaps.DepositData(110, 1.5f, 1), false)
			.add(CreateOreDepositsBlocks.LAPIS_ORE_DEPOSIT, CreateOreDepositsDataMaps.DepositData(100, 1.6f, 1), false)
			.add(CreateOreDepositsBlocks.GOLD_ORE_DEPOSIT, CreateOreDepositsDataMaps.DepositData(160, 2.6f, 2), false)
			.add(CreateOreDepositsBlocks.DIAMOND_ORE_DEPOSIT, CreateOreDepositsDataMaps.DepositData(80, 3.4f, 3), false)
			.add(CreateOreDepositsBlocks.EMERALD_ORE_DEPOSIT, CreateOreDepositsDataMaps.DepositData(60, 3.0f, 3), false)
			.add(CreateOreDepositsBlocks.NETHERITE_ORE_DEPOSIT, CreateOreDepositsDataMaps.DepositData(70, 5.0f, 4), false)

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