package com.hepdd.gtmthings.data;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.registry.registrate.entry.GTRecipeTypeEntry;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;

import static com.hepdd.gtmthings.common.registry.GTMTRegistration.GTMTHINGS_REGISTRATE;

public class GTMTRecipeTypes {

    public static final String ELECTRIC = "electric";
    public static final GTRecipeTypeEntry DIGITAL_MINER_RECIPE = GTMTHINGS_REGISTRATE
            .recipeType("digital_miner", ELECTRIC)
            .setMaxIOSize(0, 27, 0, 0).setEUIO(IO.IN)
            .UI(builder -> builder.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static void init() {}
}
