package com.hepdd.gtmthings.client;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import com.hepdd.gtmthings.GTMThings;
import com.hepdd.gtmthings.data.CustomItems;

@EventBusSubscriber(modid = GTMThings.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientExtensions {

    @SubscribeEvent
    public static void register(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return VirtualItemProviderRenderer.INSTANCE;
            }
        }, CustomItems.VIRTUAL_ITEM_PROVIDER.asItem());
    }
}
