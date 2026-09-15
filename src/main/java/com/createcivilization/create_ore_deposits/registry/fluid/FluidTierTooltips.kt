package com.createcivilization.create_ore_deposits.registry.fluid

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent

object FluidTierTooltips {

	@SubscribeEvent
	fun onItemTooltip(event: ItemTooltipEvent) {
		val tier = FluidTiers.ofBucket(event.itemStack) ?: return
		event.toolTip.add(FluidTiers.label(tier))
	}
}
