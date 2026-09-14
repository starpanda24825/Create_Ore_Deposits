package com.createcivilization.create_ore_deposits.registry.block.entries.deposit_drill

import com.createcivilization.create_ore_deposits.CreateOreDeposits.REGISTRATE
import com.tterrag.registrate.builders.MenuBuilder.ForgeMenuFactory
import com.tterrag.registrate.builders.MenuBuilder.ScreenFactory
import com.tterrag.registrate.util.entry.MenuEntry
import com.tterrag.registrate.util.nullness.NonNullSupplier

import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.items.IItemHandler
import net.neoforged.neoforge.items.ItemStackHandler
import net.neoforged.neoforge.items.SlotItemHandler

private val DRILL_MENU_FACTORY: ForgeMenuFactory<DepositDrillMenu> = ForgeMenuFactory { _, windowId, inventory, buffer ->
	DepositDrillMenu.client(windowId, inventory, buffer)
}

private val DRILL_SCREEN_FACTORY: NonNullSupplier<ScreenFactory<DepositDrillMenu, DepositDrillScreen>> =
	NonNullSupplier { ScreenFactory { menu, inventory, title -> DepositDrillScreen(menu, inventory, title) } }

class DepositDrillMenu(
	type: MenuType<*>,
	windowId: Int,
	private val inventory: Inventory,
	private val blockPos: BlockPos,
	private val itemHandler: IItemHandler
) : AbstractContainerMenu(type, windowId) {

	init {
		for (row in 0 until 3) {
			for (column in 0 until 3) {
				addSlot(OutputSlot(itemHandler, column + row * 3, 62 + column * 18, 17 + row * 18))
			}
		}
		for (row in 0 until 3) {
			for (column in 0 until 9) {
				addSlot(Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18))
			}
		}
		for (column in 0 until 9) {
			addSlot(Slot(inventory, column, 8 + column * 18, 142))
		}
	}

	override fun quickMoveStack(player: Player, index: Int): ItemStack {
		val slot: Slot = slots[index]
		if (!slot.hasItem()) return ItemStack.EMPTY

		val stack: ItemStack = slot.item
		val original: ItemStack = stack.copy()

		if (index < DRILL_SLOTS) {
			// out of the drill, into the player
			if (!moveItemStackTo(stack, DRILL_SLOTS, slots.size, true)) return ItemStack.EMPTY
		} else {
			// into the drill. the slots are output only, so there is nothing to move back in
			if (!moveItemStackTo(stack, 0, DRILL_SLOTS, false)) return ItemStack.EMPTY
		}

		if (stack.isEmpty) slot.setByPlayer(ItemStack.EMPTY) else slot.setChanged()
		if (stack.count == original.count) return ItemStack.EMPTY

		slot.onTake(player, stack)
		return original
	}

	override fun stillValid(player: Player): Boolean {
		if (player.distanceToSqr(blockPos.x + 0.5, blockPos.y + 0.5, blockPos.z + 0.5) > 64.0) return false
		return player.level().getBlockEntity(blockPos) is DepositDrillBlockEntity
	}

	// the drill's slots are its output buffer: take what is in them, put nothing in. a slot the
	// player can stuff junk into would jam the drill just as well as a full belt would.
	private class OutputSlot(itemHandler: IItemHandler, index: Int, x: Int, y: Int) :
		SlotItemHandler(itemHandler, index, x, y) {

		override fun mayPlace(stack: ItemStack): Boolean = false
	}

	companion object {
		const val DRILL_SLOTS: Int = 9

		// server side, the block entity hands over its real inventory
		fun server(windowId: Int, inventory: Inventory, blockPos: BlockPos, itemHandler: IItemHandler): DepositDrillMenu =
			DepositDrillMenu(DepositDrillMenus.DEPOSIT_DRILL.get(), windowId, inventory, blockPos, itemHandler)

		// client side, only the position comes over the wire, so the inventory is looked up on the
		// block entity the client already has loaded
		fun client(windowId: Int, inventory: Inventory, buffer: RegistryFriendlyByteBuf?): DepositDrillMenu {
			val blockPos: BlockPos = buffer?.readBlockPos() ?: BlockPos.ZERO
			val blockEntity = inventory.player.level().getBlockEntity(blockPos) as? DepositDrillBlockEntity
			return DepositDrillMenu(
				DepositDrillMenus.DEPOSIT_DRILL.get(),
				windowId,
				inventory,
				blockPos,
				blockEntity?.getOutputInventory() ?: ItemStackHandler(DRILL_SLOTS)
			)
		}
	}
}

// the drill's menu type and its client screen, registered like the rest of the mod's registries.
// lives next to the menu so the whole GUI (menu, screen, registration) is one place.
data object DepositDrillMenus {

	@JvmField
	val DEPOSIT_DRILL: MenuEntry<DepositDrillMenu> = REGISTRATE
		.menu("deposit_drill", DRILL_MENU_FACTORY, DRILL_SCREEN_FACTORY)
		.register()
}