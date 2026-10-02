package com.hepdd.gtmthings.api.misc;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

import java.util.IdentityHashMap;
import java.util.Map;

/** Classify lazily after registries are ready, without pretending a block entity is creation metadata. */
public final class Hatch {

    private static final Map<Block, Boolean> PARTS = new IdentityHashMap<>();

    public static boolean isHatch(Block block) {
        return PARTS.computeIfAbsent(block, b -> b instanceof MetaMachineBlock machine && machine.getDefinition().getBlockEntityType().create(BlockPos.ZERO, b.defaultBlockState()) instanceof MultiblockPartMachine);
    }

    private Hatch() {}
}
