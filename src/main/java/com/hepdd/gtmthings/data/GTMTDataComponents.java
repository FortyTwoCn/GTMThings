package com.hepdd.gtmthings.data;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.hepdd.gtmthings.GTMThings;
import com.hepdd.gtmthings.api.misc.VirtualItemData;
import com.mojang.serialization.Codec;

/** Components are immutable snapshots; never mutate a stack retrieved from a component. */
public final class GTMTDataComponents {

    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, GTMThings.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<VirtualItemData>> VIRTUAL_ITEM = COMPONENTS.registerComponentType("virtual_item", b -> b.persistent(VirtualItemData.CODEC)
            .networkSynchronized(VirtualItemData.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> MARKED = COMPONENTS.registerComponentType("marked", b -> b.persistent(Codec.BOOL)
            .networkSynchronized(ByteBufCodecs.BOOL));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> ACCURATE = COMPONENTS.registerComponentType("accurate", b -> b.persistent(Codec.BOOL)
            .networkSynchronized(ByteBufCodecs.BOOL));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CAPACITY = COMPONENTS.registerComponentType("capacity", b -> b.persistent(Codec.intRange(1, Integer.MAX_VALUE))
            .networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.world.item.component.CustomData>> ME_BUFFER = COMPONENTS.registerComponentType("me_buffer", b -> b.persistent(net.minecraft.world.item.component.CustomData.CODEC)
            .networkSynchronized(ByteBufCodecs.COMPOUND_TAG.map(net.minecraft.world.item.component.CustomData::of, net.minecraft.world.item.component.CustomData::copyTag)));

    public static void init(IEventBus bus) {
        COMPONENTS.register(bus);
    }

    private GTMTDataComponents() {}
}
