package com.hepdd.gtmthings.common.item;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.integration.ae2.utils.KeyStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import brachy.modularui.factory.PlayerInventoryGuiData;
import brachy.modularui.factory.inventory.InventoryTypes;
import com.hepdd.gtmthings.api.machine.trait.InaccessibleInfiniteHandler;
import com.hepdd.gtmthings.api.machine.trait.InaccessibleInfiniteTank;
import com.hepdd.gtmthings.api.misc.UnlimitedItemStackTransfer;
import com.hepdd.gtmthings.api.misc.WirelessEnergyContainer;
import com.hepdd.gtmthings.data.CustomItems;
import com.hepdd.gtmthings.data.GTMTDataComponents;
import com.hepdd.gtmthings.data.WirelessEnergySavaedData;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder("gtmthings")
@PrefixGameTestTemplate(false)
public class PortGameTests {

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void everyMachineConstructsAndReloads(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        int count = 0;
        var actualIds = new java.util.TreeSet<String>();
        for (var definition : GTRegistries.MACHINES) {
            if (!definition.getId().getNamespace().equals("gtmthings")) continue;
            actualIds.add(definition.getId().toString());
            var state = definition.getBlock().defaultBlockState();
            level.setBlockAndUpdate(pos, state);
            var machine = MetaMachine.getMachine(level, pos);
            helper.assertTrue(machine != null, "Machine must have native block entity: " + definition.getId());
            var saved = machine.saveWithFullMetadata(level.registryAccess());
            var restored = BlockEntity.loadStatic(pos, state, saved, level.registryAccess());
            helper.assertTrue(restored != null && restored.getClass() == machine.getClass(), "Machine reload must retain type: " + definition.getId());
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            count++;
        }
        var resource = "/gtmthings-machine-ids-" + (com.gregtechceu.gtceu.api.GTCEuAPI.isHighTier() ? "high" : "low") + ".txt";
        try (var input = PortGameTests.class.getResourceAsStream(resource)) {
            var expected = new java.util.TreeSet<>(java.util.Arrays.asList(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip().split("\\n")));
            helper.assertTrue(actualIds.equals(expected), "Machine IDs must exactly match the 1.20.1 registration scheme; missing " + expected.stream().filter(id -> !actualIds.contains(id)).toList() + "; added " + actualIds.stream().filter(id -> !expected.contains(id)).toList());
        } catch (java.io.IOException error) {
            throw new RuntimeException(error);
        }
        com.hepdd.gtmthings.GTMThings.LOGGER.info("PORT REGISTRY PASSED: {} original machine IDs constructed and reloaded", count);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unlimitedCountsSurviveSavingAndSimulation(GameTestHelper helper) {
        var items = new UnlimitedItemStackTransfer(2);
        items.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, Integer.MAX_VALUE));
        helper.assertTrue(items.extractItem(0, 1024, true).getCount() == 1024 && items.getStackInSlot(0).getCount() == Integer.MAX_VALUE, "Simulated extraction cannot mutate storage");
        helper.assertTrue(items.extractItem(0, -1, false).isEmpty(), "Negative extraction cannot add items");
        var restored = new UnlimitedItemStackTransfer(1);
        restored.deserializeNBT(helper.getLevel().registryAccess(), items.serializeNBT(helper.getLevel().registryAccess()));
        helper.assertTrue(restored.getSlots() == 2 && restored.getStackInSlot(0).getCount() == Integer.MAX_VALUE, "Real counts must survive the vanilla item codec");
        helper.assertTrue(restored.insertItem(0, new ItemStack(Items.IRON_INGOT), false).getCount() == 1, "Full storage must return overflow");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void providerUsesItsOwnSlotAndDoesNotMutateOnSimulation(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var main = CustomItems.VIRTUAL_ITEM_PROVIDER.asStack();
        player.getInventory().setItem(0, main);
        player.getInventory().setItem(40, CustomItems.VIRTUAL_ITEM_PROVIDER.asStack());
        var data = PlayerInventoryGuiData.of(player, InventoryTypes.PLAYER, null, 40);
        var handler = new VirtualItemProviderBehavior.ItemHandler(data);
        helper.assertTrue(handler.insertItem(0, new ItemStack(Items.DIAMOND, 2), true).getCount() == 1, "Provider consumes one configured item");
        helper.assertTrue(handler.getStackInSlot(0).isEmpty(), "Simulation cannot write an item component");
        handler.insertItem(0, new ItemStack(Items.DIAMOND, 2), false);
        helper.assertTrue(handler.getStackInSlot(0).is(Items.DIAMOND), "Offhand provider must receive its own configuration");
        helper.assertTrue(VirtualItemProviderBehavior.getVirtualItem(main).isEmpty(), "Offhand editing cannot modify mainhand");
        var returned = handler.getStackInSlot(0);
        returned.setCount(99);
        helper.assertTrue(handler.getStackInSlot(0).getCount() == 1, "Component reads cannot expose mutable stored stacks");
        data.getUsedItemStack().set(GTMTDataComponents.MARKED, true);
        helper.assertTrue(handler.extractItem(0, 1, false).isEmpty(), "AE2 marked providers must remain locked");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aeBuffersReserveDuplicateKeysAndRetainOverflow(GameTestHelper helper) {
        var buffer = new KeyStorage();
        var key = AEItemKey.of(new ItemStack(Items.DIAMOND));
        buffer.storage.put(key, Long.MAX_VALUE - 10);
        var handler = new InaccessibleInfiniteHandler(buffer);
        List<SizedIngredient> outputs = List.of(new SizedIngredient(Ingredient.of(Items.DIAMOND), 8), new SizedIngredient(Ingredient.of(Items.DIAMOND), 8));
        var simulated = handler.handleRecipeInner(IO.OUT, null, new ArrayList<>(outputs), true);
        helper.assertTrue(buffer.storage.getLong(key) == Long.MAX_VALUE - 10 && simulated.size() == 1 && simulated.getFirst().count() == 6, "Simulation must reserve duplicate keys without modifying AE storage");
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var definition = GTRegistries.MACHINES.stream().filter(d -> d.getId().getNamespace().equals("gtmthings")).findFirst().orElseThrow();
        helper.getLevel().setBlockAndUpdate(pos, definition.getBlock().defaultBlockState());
        MetaMachine.getMachine(helper.getLevel(), pos).attachTrait(handler);
        var remainder = handler.handleRecipeInner(IO.OUT, null, new ArrayList<>(outputs), false);
        helper.assertTrue(buffer.storage.getLong(key) == Long.MAX_VALUE && remainder.size() == 1 && remainder.getFirst().count() == 6, "Execution cannot overflow or silently discard outputs");
        var fluids = new KeyStorage();
        var water = AEFluidKey.of(new FluidStack(Fluids.WATER, 1));
        fluids.storage.put(water, Long.MAX_VALUE - 1000);
        var tank = new InaccessibleInfiniteTank(fluids);
        var fluidRemainder = tank.handleRecipeInner(IO.OUT, null, new ArrayList<>(List.of(SizedFluidIngredient.of(Fluids.WATER, 750), SizedFluidIngredient.of(Fluids.WATER, 750))), true);
        helper.assertTrue(fluids.storage.getLong(water) == Long.MAX_VALUE - 1000 && fluidRemainder.size() == 1 && fluidRemainder.getFirst().amount() == 500, "Fluid output simulation must reserve duplicate keys");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wirelessEnergyAndOriginBindingSurviveReload(GameTestHelper helper) {
        UUID uuid = UUID.randomUUID();
        BigInteger amount = BigInteger.ONE.shiftLeft(128).add(BigInteger.valueOf(17));
        var data = new WirelessEnergySavaedData();
        data.containerMap.put(uuid, new WirelessEnergyContainer(uuid, amount, 12345, GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO)));
        var restored = new WirelessEnergySavaedData(data.save(new CompoundTag(), helper.getLevel().registryAccess()));
        var container = restored.containerMap.get(uuid);
        helper.assertTrue(container.getStorage().equals(amount), "Wireless energy must retain values beyond long range");
        helper.assertTrue(container.getBindPos().pos().equals(BlockPos.ZERO) && container.getBindPos().dimension() == Level.OVERWORLD, "Origin is a valid binding position");
        helper.assertTrue(container.getRate() == 12345, "Transfer limits must survive reload");
        helper.succeed();
    }
}
