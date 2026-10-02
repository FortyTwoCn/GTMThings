package com.hepdd.gtmthings.api.misc;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import com.mojang.serialization.Codec;

/** Immutable, value-equal item selection for vanilla data components and AE2 keys. */
public final class VirtualItemData {

    public static final Codec<VirtualItemData> CODEC = ItemStack.CODEC.xmap(VirtualItemData::new, VirtualItemData::asStack);
    public static final StreamCodec<RegistryFriendlyByteBuf, VirtualItemData> STREAM_CODEC = ItemStack.STREAM_CODEC.map(VirtualItemData::new, VirtualItemData::asStack);
    private final ItemStack item;

    public VirtualItemData(ItemStack item) {
        this.item = item.copyWithCount(1);
    }

    public ItemStack asStack() {
        return item.copy();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VirtualItemData data && ItemStack.isSameItemSameComponents(item, data.item);
    }

    @Override
    public int hashCode() {
        return ItemStack.hashItemAndComponents(item);
    }
}
