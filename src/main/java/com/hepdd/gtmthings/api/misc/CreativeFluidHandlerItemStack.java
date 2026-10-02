package com.hepdd.gtmthings.api.misc;

import com.gregtechceu.gtceu.api.capability.IThermalFluidHandlerItemStack;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

import com.hepdd.gtmthings.common.item.CreativeFluidStats;
import org.jetbrains.annotations.NotNull;

/** Infinite source and sink. Neither simulation nor draining changes the selected fluid. */
public final class CreativeFluidHandlerItemStack implements IFluidHandlerItem, IThermalFluidHandlerItemStack {

    private final ItemStack container;

    public CreativeFluidHandlerItemStack(ItemStack container) {
        this.container = container;
    }

    private int capacity() {
        return CreativeFluidStats.getAccurate(container) ? CreativeFluidStats.getCapacity(container) : Integer.MAX_VALUE;
    }

    @Override
    public @NotNull ItemStack getContainer() {
        return container;
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public @NotNull FluidStack getFluidInTank(int tank) {
        FluidStack fluid = CreativeFluidStats.getStored(container);
        return fluid.isEmpty() ? FluidStack.EMPTY : fluid.copyWithAmount(capacity());
    }

    @Override
    public int getTankCapacity(int tank) {
        return capacity();
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return !stack.isEmpty();
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return resource.getAmount();
    }

    @Override
    public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
        if (!FluidStack.isSameFluidSameComponents(resource, CreativeFluidStats.getStored(container))) return FluidStack.EMPTY;
        return drain(resource.getAmount(), action);
    }

    @Override
    public @NotNull FluidStack drain(int amount, FluidAction action) {
        if (amount <= 0) return FluidStack.EMPTY;
        FluidStack fluid = CreativeFluidStats.getStored(container);
        return fluid.isEmpty() ? FluidStack.EMPTY : fluid.copyWithAmount(Math.min(amount, capacity()));
    }

    @Override
    public int getMaxFluidTemperature() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isGasProof() {
        return true;
    }

    @Override
    public boolean isAcidProof() {
        return true;
    }

    @Override
    public boolean isCryoProof() {
        return true;
    }

    @Override
    public boolean isPlasmaProof() {
        return true;
    }
}
