package com.hepdd.gtmthings.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderFluidIngredient;
import com.gregtechceu.gtceu.integration.ae2.utils.KeyStorage;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import appeng.api.stacks.AEFluidKey;

import java.util.HashMap;
import java.util.List;

public class InaccessibleInfiniteTank extends NotifiableFluidTank {

    private final KeyStorage buffer;

    public InaccessibleInfiniteTank(KeyStorage buffer) {
        super(1, Integer.MAX_VALUE, IO.OUT, IO.NONE);
        this.buffer = buffer;
        buffer.setOnContentsChanged(() -> {
            onContentsChanged();
            markAsChanged();
        });
    }

    @Override
    public List<SizedFluidIngredient> handleRecipeInner(IO io, GTRecipe recipe, List<SizedFluidIngredient> left, boolean simulate) {
        if (io != IO.OUT) return left;
        var reserved = new HashMap<AEFluidKey, Long>();
        boolean changed = false;
        for (var it = left.listIterator(); it.hasNext();) {
            SizedFluidIngredient ingredient = it.next();
            FluidStack[] fluids = ingredient.ingredient() instanceof IntProviderFluidIngredient provider && simulate ? new FluidStack[] { provider.getMaxSizeStack() } : ingredient.getFluids();
            if (fluids.length == 0 || fluids[0].isEmpty()) {
                it.remove();
                continue;
            }
            AEFluidKey key = AEFluidKey.of(fluids[0]);
            int amount = fluids[0].getAmount();
            long existing = reserved.getOrDefault(key, buffer.storage.getLong(key));
            int accepted = (int) Math.min(amount, Long.MAX_VALUE - existing);
            reserved.put(key, existing + accepted);
            if (!simulate && accepted > 0) {
                buffer.storage.put(key, existing + accepted);
                changed = true;
            }
            if (accepted >= amount) it.remove();
            else it.set(new SizedFluidIngredient(ingredient.ingredient(), amount - accepted));
        }
        if (changed) buffer.onChanged();
        return left;
    }

    @Override
    public int getTanks() {
        return 128;
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

    @Override
    public FluidStack getFluidInTank(int tank) {
        return FluidStack.EMPTY;
    }

    @Override
    public void setFluidInTank(int tank, FluidStack fluid) {}

    @Override
    public int getTankCapacity(int tank) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return true;
    }
}
