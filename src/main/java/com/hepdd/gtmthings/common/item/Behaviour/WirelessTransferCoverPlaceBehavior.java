package com.hepdd.gtmthings.common.item.Behaviour;

import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.item.component.IInteractionItem;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;

import com.hepdd.gtmthings.data.CustomItems;
import com.hepdd.gtmthings.utils.ItemData;

public record WirelessTransferCoverPlaceBehavior(Holder<CoverDefinition> coverDefinition) implements IInteractionItem {

    @Override
    public InteractionResult onItemUseFirst(ItemStack item, UseOnContext context) {
        if (context.getPlayer() == null || !context.getPlayer().isShiftKeyDown()) return InteractionResult.PASS;
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var side = context.getClickedFace();
        var items = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
        var fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
        boolean itemCover = item.is(CustomItems.WIRELESS_ITEM_TRANSFER_COVER.asItem()) || item.is(CustomItems.ADVANCED_WIRELESS_ITEM_TRANSFER_COVER.asItem());
        boolean fluidCover = item.is(CustomItems.WIRELESS_FLUID_TRANSFER_COVER.asItem()) || item.is(CustomItems.ADVANCED_WIRELESS_FLUID_TRANSFER_COVER.asItem());
        if (!level.isClientSide && ((itemCover && items != null && items.getSlots() > 0) || (fluidCover && fluids != null && fluids.getTanks() > 0))) {
            ItemData.update(item, tag -> {
                tag.putString("dimensionid", level.dimension().location().toString());
                tag.putString("blockid", level.getBlockState(pos).getBlock().getDescriptionId());
                tag.putString("pos", pos.toShortString());
                tag.putString("facing", side.getName());
                tag.putInt("x", pos.getX());
                tag.putInt("y", pos.getY());
                tag.putInt("z", pos.getZ());
            });
            context.getPlayer().sendSystemMessage(Component.translatable("item.gtmthings.wireless_transfer.tooltip.bind.1",
                    Component.translatable(level.getBlockState(pos).getBlock().getDescriptionId()), pos.toShortString()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(ItemStack item, Level level, Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                ItemData.update(item, tag -> { for (String key : new String[] { "dimensionid", "blockid", "pos", "facing", "x", "y", "z" }) tag.remove(key); });
                player.sendSystemMessage(Component.translatable("item.gtmthings.wireless_transfer.tooltip.bind.2"));
            }
            return InteractionResultHolder.sidedSuccess(item, level.isClientSide);
        }
        return IInteractionItem.super.use(item, level, player, hand);
    }
}
