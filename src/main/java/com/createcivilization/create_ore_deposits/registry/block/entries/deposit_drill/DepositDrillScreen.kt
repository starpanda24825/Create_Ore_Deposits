package com.createcivilization.create_ore_deposits.registry.block.entries.deposit_drill

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory

class DepositDrillScreen(menu: DepositDrillMenu, inventory: Inventory, title: Component) :
	AbstractContainerScreen<DepositDrillMenu>(menu, inventory, title) {

	override fun init() {
		super.init()
		titleLabelX = (imageWidth - font.width(title)) / 2
	}

	override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
		super.render(guiGraphics, mouseX, mouseY, partialTick)
		renderTooltip(guiGraphics, mouseX, mouseY)
	}

	// TODO: once the lube/coolant tanks are synced to the client, draw them here as gauges, like
	//  the fluid storage in those backpack mods
	override fun renderBg(guiGraphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
		guiGraphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight)
	}

	companion object {
		private val BACKGROUND: ResourceLocation =
			ResourceLocation.withDefaultNamespace("textures/gui/container/dispenser.png")
	}
}
