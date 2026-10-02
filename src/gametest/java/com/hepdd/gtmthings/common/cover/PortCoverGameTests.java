package com.hepdd.gtmthings.common.cover;

import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.filter.SimpleItemFilter;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.item.GTDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.hepdd.gtmthings.common.block.machine.multiblock.part.HugeBusPartMachine;
import com.hepdd.gtmthings.data.CustomItems;
import com.hepdd.gtmthings.data.CustomMachines;
import com.hepdd.gtmthings.data.GTMTCovers;
import com.hepdd.gtmthings.utils.ItemData;

import java.util.List;

@GameTestHolder("gtmthings")
@PrefixGameTestTemplate(false)
public final class PortCoverGameTests {

    public static void seedFilter(CoverBehavior behavior) {
        var cover = (AdvancedWirelessTransferCover) behavior;
        if (cover.transferType == WirelessTransferCover.TRANSFER_ITEM) {
            var filter = GTItems.ITEM_FILTER.asStack();
            filter.set(GTDataComponents.SIMPLE_ITEM_FILTER, new SimpleItemFilter(false, true, List.of(new ItemStack(Items.DIAMOND))));
            cover.filterHandlerItem.setFilterItem(filter);
        } else cover.filterHandlerFluid.setFilterItem(GTItems.FLUID_FILTER.asStack());
    }

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void wirelessCoverTransfersOnlyMatchingItemsAndPersistsBinding(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(1, 2, 2));
        var target = pos.east(2);
        level.setBlockAndUpdate(pos, CustomMachines.HUGE_ITEM_EXPORT_BUS[1].get().getBlock().defaultBlockState());
        level.setBlockAndUpdate(target, Blocks.CHEST.defaultBlockState());
        var machine = (HugeBusPartMachine) MetaMachine.getMachine(level, pos);
        machine.getInventory().storage.setStackInSlot(0, new ItemStack(Items.DIAMOND, 900));
        machine.getInventory().storage.setStackInSlot(1, new ItemStack(Items.IRON_INGOT, 500));
        var stack = CustomItems.ADVANCED_WIRELESS_ITEM_TRANSFER_COVER.asStack();
        var binding = ItemData.read(stack);
        binding.putString("dimensionid", level.dimension().location().toString());
        binding.putString("facing", Direction.UP.getName());
        binding.putInt("x", target.getX());
        binding.putInt("y", target.getY());
        binding.putInt("z", target.getZ());
        ItemData.update(stack, tag -> tag.merge(binding));
        var cover = (AdvancedWirelessTransferCover) GTMTCovers.ADVANCED_WIRELESS_ITEM_TRANSFER.value().createCoverBehavior(machine.getCoverContainer(), Direction.UP);
        helper.assertTrue(cover.canAttach(), "Advanced item transfer must attach to an unlimited output bus");
        cover.onAttached(stack, null);
        seedFilter(cover);
        machine.getCoverContainer().setCoverAtSide(cover, Direction.UP);
        cover.onLoad();
        helper.succeedWhen(() -> {
            var inventory = level.getCapability(Capabilities.ItemHandler.BLOCK, target, Direction.UP);
            int diamonds = 0;
            for (int slot = 0; slot < inventory.getSlots(); slot++) if (inventory.getStackInSlot(slot).is(Items.DIAMOND)) diamonds += inventory.getStackInSlot(slot).getCount();
            helper.assertTrue(diamonds == 900, "Bound wireless output must deliver the configured diamond stacks");
            helper.assertTrue(machine.getInventory().storage.getStackInSlot(0).isEmpty() && machine.getInventory().storage.getStackInSlot(1).getCount() == 500,
                    "The whitelist must preserve every nonmatching item");
            var restored = (HugeBusPartMachine) BlockEntity.loadStatic(pos, machine.getBlockState(), machine.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
            var restoredCover = (AdvancedWirelessTransferCover) restored.getCoverContainer().getCoverAtSide(Direction.UP);
            helper.assertTrue(target.equals(restoredCover.targetPos) && restoredCover.facing == Direction.UP,
                    "Native cover persistence must retain the target position and actual target face");
            helper.assertTrue(restoredCover.testItem(new ItemStack(Items.DIAMOND)) && !restoredCover.testItem(new ItemStack(Items.IRON_INGOT)),
                    "Native filter components must retain the whitelist through block entity reload");
        });
    }

    private PortCoverGameTests() {}
}
