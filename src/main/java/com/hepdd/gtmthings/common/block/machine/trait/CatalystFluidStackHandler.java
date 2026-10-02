package com.hepdd.gtmthings.common.block.machine.trait;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.List;

/**
 * @author EasterFG on 2024/10/3
 */
public class CatalystFluidStackHandler extends NotifiableFluidTank {

    public CatalystFluidStackHandler(int slots, int capacity, IO io, IO capabilityIO) {
        super(slots, capacity, io, capabilityIO);
    }

    @Override
    public List<SizedFluidIngredient> handleRecipeInner(IO io, GTRecipe recipe, List<SizedFluidIngredient> left, boolean simulate) {
        for (Content content : recipe.getInputContents(FluidRecipeCapability.CAP)) {
            var ingredient = (SizedFluidIngredient) content.content();
            for (CustomFluidTank tank : getStorages()) {
                FluidStack is = tank.getFluid();
                if (ingredient.ingredient().test(is) && content.chance() > 0) return left;
            }
        }

        return super.handleRecipeInner(io, recipe, left, simulate);
    }
}
