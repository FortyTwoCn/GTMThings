package com.hepdd.gtmthings.integration.jade.provider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Scoped data keys prevent the energy and computation providers from overwriting each other. */
public abstract class MachineDataProvider<T> implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    private final ResourceLocation uid;

    protected MachineDataProvider(ResourceLocation uid) {
        this.uid = uid;
    }

    @Override
    public ResourceLocation getUid() {
        return uid;
    }

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor block) {
        T capability = getCapability(block.getLevel(), block.getPosition(), block.getSide());
        if (capability == null) return;
        var data = new CompoundTag();
        write(data, capability);
        tag.put(uid.toString(), data);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor block, IPluginConfig config) {
        if (block.getBlockEntity() != null && block.getServerData().contains(uid.toString(), CompoundTag.TAG_COMPOUND))
            addTooltip(block.getServerData().getCompound(uid.toString()), tooltip, block.getPlayer(), block, block.getBlockEntity(), config);
    }

    protected abstract @Nullable T getCapability(Level level, BlockPos pos, @Nullable Direction side);

    protected abstract void write(CompoundTag data, T capability);

    protected abstract void addTooltip(CompoundTag data, ITooltip tooltip, Player player, BlockAccessor block, BlockEntity entity, IPluginConfig config);
}
