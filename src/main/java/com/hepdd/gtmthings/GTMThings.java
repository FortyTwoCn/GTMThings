package com.hepdd.gtmthings;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import com.hepdd.gtmthings.config.ConfigHolder;
import com.hepdd.gtmthings.data.*;

import static com.hepdd.gtmthings.common.registry.GTMTRegistration.GTMTHINGS_REGISTRATE;

@Mod(GTMThings.MOD_ID)
public class GTMThings {

    public static final String MOD_ID = "gtmthings";
    public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(MOD_ID);
    public static final String NAME = "GTM Things";

    public static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, name);
    }

    public GTMThings(IEventBus modEventBus) {
        ConfigHolder.init();
        GTMTDataComponents.init(modEventBus);
        GTMTHINGS_REGISTRATE.registerEventListeners(modEventBus);
        CreativeModeTabs.init();
        GTMTRecipeTypes.init();
        GTMTCovers.init();
        CreativeMachines.init();
        WirelessMachines.init();
        CustomMachines.init();
        CustomItems.init();
    }
}
