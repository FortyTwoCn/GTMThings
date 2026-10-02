package com.hepdd.gtmthings.common.item;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.integration.ae2.utils.KeyStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.hepdd.gtmthings.api.machine.trait.InaccessibleInfiniteHandler;
import com.hepdd.gtmthings.api.machine.trait.InaccessibleInfiniteTank;
import com.hepdd.gtmthings.common.block.machine.multiblock.part.appeng.MEOutputPartMachine;
import com.hepdd.gtmthings.data.CustomMachines;
import com.hepdd.gtmthings.data.GTMTDataComponents;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder("gtmthings")
@PrefixGameTestTemplate(false)
public final class PortAEIntegrationGameTests {

    @GameTest(template = "empty", timeoutTicks = 500)
    public static void nativeGridPreservesFullOutputsAndRespectsWorkingToggle(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(1, 2, 2));
        level.setBlockAndUpdate(pos, CustomMachines.ME_EXPORT_BUFFER.get().getBlock().defaultBlockState());
        var machine = (MEOutputPartMachine) MetaMachine.getMachine(level, pos);
        machine.setFrontFacing(Direction.EAST);
        level.setBlockAndUpdate(pos.east(), AEBlocks.DRIVE.block().defaultBlockState());
        level.setBlockAndUpdate(pos.east().south(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var drive = (DriveBlockEntity) level.getBlockEntity(pos.east());
        drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        drive.getInternalInventory().setItemDirect(1, AEItems.FLUID_CELL_1K.stack());
        var diamond = AEItemKey.of(Items.DIAMOND);
        var water = AEFluidKey.of(Fluids.WATER);
        enqueue(machine, 100, 1000);
        int[] stage = { 0 }, ticks = { 0 };
        long[] retained = { 0, 0 };
        helper.onEachTick(() -> {
            ticks[0]++;
            var grid = machine.getMainNode().getGrid();
            if (grid == null || !machine.isOnline()) return;
            var stored = grid.getStorageService().getInventory().getAvailableStacks();
            if (stage[0] == 0) {
                if (stored.get(diamond) != 100 || stored.get(water) != 1000) return;
                helper.assertTrue(pending(machine, "items", diamond) == 0 && pending(machine, "fluids", water) == 0,
                        "Online output must leave both recipe buffers exactly once");
                enqueue(machine, 100000, 100000000);
                stage[0] = 1;
                ticks[0] = 0;
            } else if (stage[0] == 1 && ticks[0] > 60) {
                retained[0] = pending(machine, "items", diamond);
                retained[1] = pending(machine, "fluids", water);
                helper.assertTrue(retained[0] > 0 && retained[1] > 0, "Full real AE2 cells must leave unaccepted output buffered");
                helper.assertTrue(stored.get(diamond) + retained[0] == 100100 && stored.get(water) + retained[1] == 100001000,
                        "Live network insertion must conserve every item and millibucket");
                machine.setWorkingEnabled(false);
                drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_256K.stack());
                drive.getInternalInventory().setItemDirect(1, AEItems.FLUID_CELL_256K.stack());
                stage[0] = 2;
                ticks[0] = 0;
            } else if (stage[0] == 2 && ticks[0] > 60) {
                helper.assertTrue(stored.get(diamond) == 0 && stored.get(water) == 0,
                        "A disabled output must not transfer to newly available network storage");
                helper.assertTrue(pending(machine, "items", diamond) == retained[0] && pending(machine, "fluids", water) == retained[1],
                        "Disabling work must preserve pending output");
                machine.setWorkingEnabled(true);
                stage[0] = 3;
            } else if (stage[0] == 3 && pending(machine, "items", diamond) == 0 && pending(machine, "fluids", water) == 0) {
                helper.assertTrue(stored.get(diamond) == retained[0] && stored.get(water) == retained[1],
                        "Re-enabling work must transfer every remaining item and fluid exactly once");
                helper.succeed();
            }
        });
    }

    private static void enqueue(MEOutputPartMachine machine, int items, int fluids) {
        machine.getTraits(InaccessibleInfiniteHandler.class).getFirst().handleRecipeInner(IO.OUT, null,
                new ArrayList<>(List.of(SizedIngredient.of(Items.DIAMOND, items))), false);
        machine.getTraits(InaccessibleInfiniteTank.class).getFirst().handleRecipeInner(IO.OUT, null,
                new ArrayList<>(List.of(SizedFluidIngredient.of(Fluids.WATER, fluids))), false);
    }

    private static long pending(MEOutputPartMachine machine, String buffer, AEKey key) {
        var drop = CustomMachines.ME_EXPORT_BUFFER.asStack();
        machine.saveToItem(drop, machine.getLevel().registryAccess());
        var contents = new KeyStorage();
        contents.deserializeNBT(machine.getLevel().registryAccess(), drop.get(GTMTDataComponents.ME_BUFFER).copyTag().getList(buffer, net.minecraft.nbt.Tag.TAG_COMPOUND));
        return contents.storage.getLong(key);
    }

    private PortAEIntegrationGameTests() {}
}
