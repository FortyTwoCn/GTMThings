package com.hepdd.gtmthings.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderIngredient;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.integration.ae2.utils.KeyStorage;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import appeng.api.stacks.AEItemKey;

import java.util.HashMap;
import java.util.List;

/** Recipe-only output buffer. Simulation reserves space across every output with the same AE key. */
public class InaccessibleInfiniteHandler extends NotifiableItemStackHandler {

    private final KeyStorage buffer;

    public InaccessibleInfiniteHandler(KeyStorage buffer) {
        super(1, IO.OUT, IO.NONE, size -> new EmptyStorage());
        this.buffer = buffer;
        shouldDropInventoryInWorld(false);
        buffer.setOnContentsChanged(() -> {
            onContentsChanged();
            markAsChanged();
        });
    }

    @Override
    public List<SizedIngredient> handleRecipeInner(IO io, GTRecipe recipe, List<SizedIngredient> left, boolean simulate) {
        if (io != IO.OUT) return left;
        var reserved = new HashMap<AEItemKey, Long>();
        boolean changed = false;
        for (var it = left.listIterator(); it.hasNext();) {
            SizedIngredient ingredient = it.next();
            ItemStack[] items = ingredient.ingredient().getCustomIngredient() instanceof IntProviderIngredient provider && simulate ? new ItemStack[] { provider.getMaxSizeStack() } : ingredient.getItems();
            if (items.length == 0 || items[0].isEmpty()) {
                it.remove();
                continue;
            }
            AEItemKey key = AEItemKey.of(items[0]);
            int amount = ingredient.ingredient().getCustomIngredient() instanceof IntProviderIngredient provider && simulate ? provider.getMaxRoll() : ingredient.count();
            long existing = reserved.getOrDefault(key, buffer.storage.getLong(key));
            int accepted = (int) Math.min(amount, Long.MAX_VALUE - existing);
            reserved.put(key, existing + accepted);
            if (!simulate && accepted > 0) {
                buffer.storage.put(key, existing + accepted);
                changed = true;
            }
            if (accepted >= amount) it.remove();
            else it.set(new SizedIngredient(ingredient.ingredient(), amount - accepted));
        }
        if (changed) buffer.onChanged();
        return left;
    }

    @Override
    public List<Object> getContents() {
        return List.of();
    }

    @Override
    public double getTotalContentAmount() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    private static final class EmptyStorage extends CustomItemStackHandler {

        @Override
        public int getSlots() {
            return Short.MAX_VALUE;
        }

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {}

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }
    }
}
