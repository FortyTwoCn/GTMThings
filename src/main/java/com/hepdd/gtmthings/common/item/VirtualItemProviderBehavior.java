package com.hepdd.gtmthings.common.item;

import com.gregtechceu.gtceu.api.item.component.IAddInformation;
import com.gregtechceu.gtceu.api.mui.IItemUIHolder;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import brachy.modularui.factory.PlayerInventoryGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widgets.slot.ItemSlot;
import brachy.modularui.widgets.slot.ModularSlot;
import com.hepdd.gtmthings.data.CustomItems;
import com.hepdd.gtmthings.data.GTMTDataComponents;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class VirtualItemProviderBehavior implements IAddInformation, IItemUIHolder {

    public static final VirtualItemProviderBehavior INSTANCE = new VirtualItemProviderBehavior();

    public static void setVirtualItem(ItemStack stack, ItemStack virtualItem) {
        if (virtualItem.isEmpty()) stack.remove(GTMTDataComponents.VIRTUAL_ITEM);
        else stack.set(GTMTDataComponents.VIRTUAL_ITEM, new com.hepdd.gtmthings.api.misc.VirtualItemData(virtualItem));
    }

    public static ItemStack getVirtualItem(ItemStack item) {
        var selected = item.get(GTMTDataComponents.VIRTUAL_ITEM);
        return selected == null ? ItemStack.EMPTY : selected.asStack();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
        ItemStack virtualItem = getVirtualItem(stack);
        if (!virtualItem.isEmpty()) {
            lines.add(Component.translatable("gui.ae2.Items").append(": ").append(virtualItem.getDisplayName()));
        }
    }

    @Override
    public ModularPanel<?> buildUI(PlayerInventoryGuiData<?> data, PanelSyncManager syncManager, UISettings settings) {
        return ModularPanel.defaultPanel("virtual_item_provider").bindPlayerInventory()
                .child(new ItemSlot().slot(new ModularSlot(new ItemHandler(data), 0))
                        .horizontalCenter().top(24));
    }

    record ItemHandler(PlayerInventoryGuiData<?> data) implements IItemHandlerModifiable {

        private boolean locked() {
            return data.getUsedItemStack().getOrDefault(GTMTDataComponents.MARKED, false);
        }

        @Override
        public void setStackInSlot(int slot, @NotNull ItemStack value) {
            if (locked()) return;
            ItemStack provider = data.getUsedItemStack().copy();
            setVirtualItem(provider, value);
            data.setUsedItemStack(provider);
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return getVirtualItem(data.getUsedItemStack());
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack value, boolean simulate) {
            if (value.isEmpty() || !isItemValid(slot, value) || !getStackInSlot(slot).isEmpty()) return value;
            if (!simulate) setStackInSlot(slot, value.copyWithCount(1));
            return value.copyWithCount(value.getCount() - 1);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (locked() || amount <= 0) return ItemStack.EMPTY;
            ItemStack result = getStackInSlot(slot);
            if (!simulate) setStackInSlot(slot, ItemStack.EMPTY);
            return result;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack value) {
            return !locked() && !value.is(CustomItems.VIRTUAL_ITEM_PROVIDER.asItem());
        }
    }
}
