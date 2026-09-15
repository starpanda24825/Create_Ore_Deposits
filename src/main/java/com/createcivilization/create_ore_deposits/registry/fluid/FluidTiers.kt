package com.createcivilization.create_ore_deposits.registry.fluid

import com.createcivilization.create_ore_deposits.registry.datamap.CreateOreDepositsDataMaps.COOLING_FACTOR_DATA
import com.createcivilization.create_ore_deposits.registry.datamap.CreateOreDepositsDataMaps.LUBRICANT_FACTOR_DATA
import com.createcivilization.create_ore_deposits.util.translate

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.material.Fluid

import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.FluidUtil


enum class FluidRole(val langKey: String) {
	COOLANT("coolant"),
	LUBRICANT("lubricant")
}

data class FluidTier(val role: FluidRole, val tier: Int)

object FluidTiers {

	fun of(fluid: Fluid?): FluidTier? {
		if (fluid == null) return null
		val holder = fluid.builtInRegistryHolder()
		holder.getData(COOLING_FACTOR_DATA)?.let { return FluidTier(FluidRole.COOLANT, it.tier) }
		holder.getData(LUBRICANT_FACTOR_DATA)?.let { return FluidTier(FluidRole.LUBRICANT, it.tier) }
		return null
	}

	fun of(stack: FluidStack): FluidTier? = if (stack.isEmpty) null else of(stack.fluid)

	// for buckets. an empty bucket contains nothing, so it gets no tier line.
	fun ofBucket(stack: ItemStack): FluidTier? {
		val contained = FluidUtil.getFluidContained(stack)
		if (contained.isEmpty) return null
		return of(contained.get().fluid)
	}

	fun numeral(tier: Int): String = when (tier) {
		1 -> "I"
		2 -> "II"
		3 -> "III"
		4 -> "IV"
		else -> tier.toString()
	}

	fun label(tier: FluidTier): MutableComponent =
		translate("tooltip.fluid.tier.${tier.role.langKey}", numeral(tier.tier))
			.style(ChatFormatting.LIGHT_PURPLE)
			.component()
}
