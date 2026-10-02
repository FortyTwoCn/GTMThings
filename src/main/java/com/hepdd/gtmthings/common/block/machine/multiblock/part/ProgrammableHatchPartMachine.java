package com.hepdd.gtmthings.common.block.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.common.machine.multiblock.part.DualHatchPartMachine;

import com.hepdd.gtmthings.api.machine.trait.ProgrammableCircuitHandler;
import com.hepdd.gtmthings.data.CustomItems;

public class ProgrammableHatchPartMachine extends DualHatchPartMachine {

    @SaveField(nbtKey = "circuitInventory")
    private final ProgrammableCircuitHandler programming;

    public ProgrammableHatchPartMachine(BlockEntityCreationInfo info, int tier, IO io, Object... args) {
        super(info, tier, io);
        getInventory().setFilter(stack -> !stack.is(CustomItems.VIRTUAL_ITEM_PROVIDER.asItem()));
        programming = attachTrait(new ProgrammableCircuitHandler(this));
    }
}
