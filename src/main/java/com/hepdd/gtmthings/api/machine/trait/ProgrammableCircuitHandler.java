package com.hepdd.gtmthings.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import net.minecraft.world.item.ItemStack;

import com.hepdd.gtmthings.common.cover.ProgrammableCover;
import com.hepdd.gtmthings.common.item.VirtualItemProviderBehavior;
import com.hepdd.gtmthings.data.CustomItems;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class ProgrammableCircuitHandler extends NotifiableItemStackHandler {

    public ProgrammableCircuitHandler(MetaMachine machine) {
        super(1, IO.IN, IO.IN, size -> new ProgrammingStorage(size, machine));
    }

    @Override
    public @NotNull List<Object> getContents() {
        return Collections.singletonList(storage.getStackInSlot(0));
    }

    @Override
    public double getTotalContentAmount() {
        return storage.getStackInSlot(0).getCount();
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        return ItemStack.EMPTY;
    }

    private static final class ProgrammingStorage extends CustomItemStackHandler {

        private final MetaMachine machine;

        private ProgrammingStorage(int size, MetaMachine machine) {
            super(size);
            this.machine = machine;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return stack.is(CustomItems.VIRTUAL_ITEM_PROVIDER.asItem()) && !VirtualItemProviderBehavior.getVirtualItem(stack).isEmpty() && (!(machine instanceof SimpleTieredMachine) || machine.getCoverContainer().getCovers().stream().anyMatch(ProgrammableCover.class::isInstance));
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack)) return stack;
            if (!simulate) setStackInSlot(slot, VirtualItemProviderBehavior.getVirtualItem(stack));
            return ItemStack.EMPTY;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }
    }
}
