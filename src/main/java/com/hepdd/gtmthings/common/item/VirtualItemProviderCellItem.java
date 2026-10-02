package com.hepdd.gtmthings.common.item;

import net.minecraft.world.item.ItemStack;

import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.items.storage.CreativeCellItem;
import appeng.util.ConfigInventory;
import com.hepdd.gtmthings.data.CustomItems;
import com.hepdd.gtmthings.data.GTMTDataComponents;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public final class VirtualItemProviderCellItem extends CreativeCellItem {

    public VirtualItemProviderCellItem(Properties props) {
        super(props);
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack stack) {
        Holder holder = new Holder(stack);
        holder.inventory = new VirtualConfigInventory(holder::save);
        holder.inventory.readFromList(stack.getOrDefault(AEComponents.STORAGE_CELL_CONFIG_INV, List.of()));
        return holder.inventory;
    }

    private static final class Holder {

        private final ItemStack stack;
        private ConfigInventory inventory;

        private Holder(ItemStack stack) {
            this.stack = stack;
        }

        private void save() {
            stack.set(AEComponents.STORAGE_CELL_CONFIG_INV, inventory.toList());
        }
    }

    private static final class VirtualConfigInventory extends ConfigInventory {

        private VirtualConfigInventory(Runnable listener) {
            super(Set.of(AEKeyType.items()), null, Mode.CONFIG_TYPES, 63, listener, false);
        }

        @Override
        public void setStack(int slot, @Nullable GenericStack stack) {
            if (stack == null) {
                super.setStack(slot, null);
            } else if (stack.what() instanceof AEItemKey key && key.getItem() == CustomItems.VIRTUAL_ITEM_PROVIDER.asItem()) {
                ItemStack provider = key.toStack();
                if (VirtualItemProviderBehavior.getVirtualItem(provider).isEmpty()) return;
                provider.set(GTMTDataComponents.MARKED, true);
                // AE keys are immutable; change a copy to preserve the network's key hash and identity.
                super.setStack(slot, new GenericStack(AEItemKey.of(provider), 0));
            }
        }
    }
}
