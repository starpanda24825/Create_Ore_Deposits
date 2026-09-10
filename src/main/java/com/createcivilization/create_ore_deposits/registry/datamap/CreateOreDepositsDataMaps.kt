package com.createcivilization.create_ore_deposits.registry.datamap

import com.createcivilization.create_ore_deposits.util.asResource
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.material.Fluid
import net.neoforged.neoforge.registries.datamaps.DataMapType

object CreateOreDepositsDataMaps {

	data class DepositData(val maxAttempts: Int, val hardness: Float, val requiredTier: Int) {
		companion object {
			val CODEC: Codec<DepositData> = RecordCodecBuilder.create { instance ->
				instance.group(
					Codec.INT.fieldOf("max_attempts").forGetter(DepositData::maxAttempts),
					Codec.FLOAT.fieldOf("hardness").forGetter(DepositData::hardness),
					Codec.INT.optionalFieldOf("required_tier", 1).forGetter(DepositData::requiredTier)
				).apply(instance, ::DepositData)
			}
		}
	}

	data class TipTierData(val tier: Int) {
		companion object {
			val CODEC: Codec<TipTierData> = Codec.INT.xmap(::TipTierData, TipTierData::tier)
		}
	}

	data class CoolingFactorData(val coolingFactor: Float) {
		companion object {
			val CODEC: Codec<CoolingFactorData> = RecordCodecBuilder.create { instance ->
				instance.group(
					Codec.FLOAT.fieldOf("cooling_factor").forGetter(CoolingFactorData::coolingFactor)
				).apply(instance, ::CoolingFactorData)
			}
		}
	}

	data class LubricantFactorData(val lubeFactor: Float) {
		companion object {
			val CODEC: Codec<LubricantFactorData> = RecordCodecBuilder.create { instance ->
				instance.group(
					Codec.FLOAT.fieldOf("lube_factor").forGetter(LubricantFactorData::lubeFactor)
				).apply(instance, ::LubricantFactorData)
			}
		}
	}

	val DEPOSIT_DATA: DataMapType<Block?, DepositData?> =
		DataMapType.builder(
			"deposit_data".asResource(),
			Registries.BLOCK,
			DepositData.CODEC
		).build()

	val COOLING_FACTOR_DATA: DataMapType<Fluid, CoolingFactorData> =
		DataMapType.builder(
			"cooling_factor_data".asResource(),
			Registries.FLUID,
			CoolingFactorData.CODEC
		).build()

	val LUBRICANT_FACTOR_DATA: DataMapType<Fluid, LubricantFactorData> =
		DataMapType.builder(
			"lubricant_factor_data".asResource(),
			Registries.FLUID,
			LubricantFactorData.CODEC
		).build()

	// Answer 6: disposable tip tiers, mirrors the *_TIP_TIER tags for redundancy
	val TIP_TIER_DATA: DataMapType<Item, TipTierData> =
		DataMapType.builder(
			"tip_tier_data".asResource(),
			Registries.ITEM,
			TipTierData.CODEC
		).build()
}
