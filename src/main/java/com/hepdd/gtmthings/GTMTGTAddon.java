package com.hepdd.gtmthings;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.data.recipes.RecipeOutput;

import com.hepdd.gtmthings.common.registry.GTMTRegistration;
import com.hepdd.gtmthings.data.GTMTRecipe;

@GTAddon(GTMThings.MOD_ID)
public class GTMTGTAddon implements IGTAddon {

    @Override
    public GTRegistrate getRegistrate() {
        return GTMTRegistration.GTMTHINGS_REGISTRATE;
    }

    @Override
    public void addRecipes(RecipeOutput provider) {
        GTMTRecipe.init(provider);
    }
}
