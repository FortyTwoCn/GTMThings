package com.hepdd.gtmthings.api.gui;

import net.minecraft.world.item.ItemStack;

import brachy.modularui.utils.MouseData;
import brachy.modularui.value.sync.PhantomItemSlotSyncHandler;
import brachy.modularui.widgets.slot.ModularSlot;

/** A type selector: right/middle click or an empty cursor clears; left click selects one item. */
public final class SelectionItemSlotSyncHandler extends PhantomItemSlotSyncHandler {

    public SelectionItemSlotSyncHandler(ModularSlot slot) {
        super(slot);
    }

    @Override
    protected void phantomClick(MouseData mouse, ItemStack cursor) {
        if (cursor.isEmpty() || mouse.mouseButton() != 0) getSlot().set(ItemStack.EMPTY);
        else if (isItemValid(cursor)) getSlot().set(cursor.copyWithCount(1));
    }

    @Override
    public void incrementStackCount(int amount) {
        if (amount < 0) getSlot().set(ItemStack.EMPTY);
    }
}
