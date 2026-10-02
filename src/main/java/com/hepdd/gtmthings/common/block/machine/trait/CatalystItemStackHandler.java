package com.hepdd.gtmthings.common.block.machine.trait;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.utils.ItemStackHashStrategy;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CatalystItemStackHandler extends NotifiableItemStackHandler {

    public CatalystItemStackHandler(int slots, @NotNull IO handlerIO, @NotNull IO capabilityIO) {
        super(slots, handlerIO, capabilityIO, CustomItemStackHandler::new);
    }

    public CatalystItemStackHandler(int slots, @NotNull IO handlerIO) {
        this(slots, handlerIO, handlerIO);
    }

    @Override
    public List<SizedIngredient> handleRecipeInner(IO io, GTRecipe recipe, List<SizedIngredient> left,
                                                   boolean simulate) {
        Object2IntMap<ItemStack> map = new Object2IntOpenCustomHashMap<>(ItemStackHashStrategy.comparingAllButCount());
        for (int i = 0; i < storage.getSlots(); i++) {
            map.putIfAbsent(storage.getStackInSlot(i), 1);
        }
        for (Content content : recipe.getInputContents(ItemRecipeCapability.CAP)) {
            var ingredient = (SizedIngredient) content.content();
            for (ItemStack is : map.keySet()) {
                if (ingredient.ingredient().test(is) && content.chance() > 0) return left;
            }
        }
        return handleRecipe(io, recipe, left, simulate, this.handlerIO, storage, getMachine());
    }
}
