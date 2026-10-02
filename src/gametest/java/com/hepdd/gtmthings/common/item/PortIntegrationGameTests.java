package com.hepdd.gtmthings.common.item;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.hepdd.gtmthings.GTMThings;
import com.hepdd.gtmthings.api.machine.trait.ProgrammableCircuitHandler;
import com.hepdd.gtmthings.api.misc.UnlimitedItemStackTransfer;
import com.hepdd.gtmthings.api.misc.VirtualItemData;
import com.hepdd.gtmthings.api.pattern.AdvancedBlockPattern;
import com.hepdd.gtmthings.api.transfer.UnlimitItemTransferHelper;
import com.hepdd.gtmthings.common.block.machine.electric.DigitalMiner;
import com.hepdd.gtmthings.common.block.machine.multiblock.part.appeng.MEOutputPartMachine;
import com.hepdd.gtmthings.common.block.machine.trait.CatalystFluidStackHandler;
import com.hepdd.gtmthings.common.block.machine.trait.CatalystItemStackHandler;
import com.hepdd.gtmthings.data.CustomItems;
import com.hepdd.gtmthings.data.CustomMachines;
import com.hepdd.gtmthings.data.GTMTCovers;
import com.hepdd.gtmthings.data.GTMTDataComponents;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder("gtmthings")
@PrefixGameTestTemplate(false)
public final class PortIntegrationGameTests {

    @GameTest(template = "empty")
    public static void runtimeRecipesIncludeNativeAddonRecipes(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager().getAllRecipesFor(GTRecipeTypes.ASSEMBLER_RECIPES.get());
        for (String id : List.of("virtual_item_provider", "virtual_item_provider_cell", "wireless_energy_monitor", "wireless_energy_interface", "wireless_item_transfer_cover", "wireless_fluid_transfer_cover", "wireless_energy_receive_cover_lv", "wireless_energy_receive_cover_uv")) {
            var expected = GTMThings.id("assembler/" + id);
            helper.assertTrue(recipes.stream().anyMatch(r -> r.id().equals(expected)), "Runtime recipe is absent: " + expected);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mePendingOutputSurvivesMachineItemRoundTrip(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var state = CustomMachines.ME_EXPORT_BUFFER.get().getBlock().defaultBlockState();
        level.setBlockAndUpdate(pos, state);
        var machine = (MEOutputPartMachine) MetaMachine.getMachine(level, pos);
        var items = machine.getTraits(com.hepdd.gtmthings.api.machine.trait.InaccessibleInfiniteHandler.class).getFirst();
        var fluids = machine.getTraits(com.hepdd.gtmthings.api.machine.trait.InaccessibleInfiniteTank.class).getFirst();
        items.handleRecipeInner(IO.OUT, null, new ArrayList<>(List.of(new SizedIngredient(Ingredient.of(Items.DIAMOND), 1234567))), false);
        fluids.handleRecipeInner(IO.OUT, null, new ArrayList<>(List.of(SizedFluidIngredient.of(Fluids.WATER, 9876543))), false);
        var drop = CustomMachines.ME_EXPORT_BUFFER.asStack();
        machine.saveToItem(drop, level.registryAccess());
        helper.assertTrue(drop.has(GTMTDataComponents.ME_BUFFER), "Dropped machine must carry pending output");
        var serialized = drop.save(level.registryAccess());
        drop = ItemStack.parse(level.registryAccess(), serialized).orElseThrow();
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, state);
        var restored = (MEOutputPartMachine) MetaMachine.getMachine(level, pos);
        restored.applyComponentsFromItemStack(drop);
        var secondDrop = CustomMachines.ME_EXPORT_BUFFER.asStack();
        restored.saveToItem(secondDrop, level.registryAccess());
        helper.assertTrue(drop.get(GTMTDataComponents.ME_BUFFER).equals(secondDrop.get(GTMTDataComponents.ME_BUFFER)), "Breaking and replacing an offline ME buffer must retain all item and fluid counts");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void minerMinesQueuedOreAfterBlockEntityReload(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var ore = pos.east();
        var state = CustomMachines.DIGITAL_MINER[GTValues.LV].get().getBlock().defaultBlockState();
        level.setBlockAndUpdate(pos, state);
        level.setBlockAndUpdate(ore, Blocks.DIAMOND_ORE.defaultBlockState());
        var machine = (DigitalMiner) MetaMachine.getMachine(level, pos);
        machine.setSilk(true);
        machine.setMinerRadius(1);
        machine.setMinHeight(pos.getY());
        machine.setMaxHeight(pos.getY());
        var filter = com.gregtechceu.gtceu.common.data.GTItems.ITEM_FILTER.asStack();
        filter.set(com.gregtechceu.gtceu.common.data.item.GTDataComponents.SIMPLE_ITEM_FILTER,
                new com.gregtechceu.gtceu.api.cover.filter.SimpleItemFilter(false, true, List.of(new ItemStack(Blocks.DIAMOND_ORE))));
        machine.getFilterInventory().setFilterItem(filter);
        var logic = machine.getRecipeLogic();
        logic.configure(1, pos.getY(), pos.getY(), 1, null);
        logic.setCurrentRadius(1);
        logic.resetArea(true);
        helper.assertTrue(logic.getOreAmount() == 1, "Scan must queue the ore at the inclusive height bound");
        var saved = machine.saveWithFullMetadata(level.registryAccess());
        level.removeBlockEntity(pos);
        var restored = (DigitalMiner) BlockEntity.loadStatic(pos, state, saved, level.registryAccess());
        level.setBlockEntity(restored);
        helper.assertTrue(restored.getItemFilter() != null && restored.getItemFilter().test(new ItemStack(Blocks.DIAMOND_ORE)) && !restored.getItemFilter().test(new ItemStack(Blocks.IRON_ORE)), "Miner whitelist must survive native filter inventory reload");
        helper.assertTrue(restored.getRecipeLogic().getOreAmount() == 1, "Queued ore must survive native sync-system persistence");
        restored.energyContainer.setEnergyStored(restored.energyContainer.getEnergyCapacity());
        restored.getRecipeLogic().configure(1, pos.getY(), pos.getY(), 1, null);
        restored.setWorkingEnabled(true);
        helper.onEachTick(() -> restored.getRecipeLogic().serverTick());
        helper.succeedWhen(() -> {
            helper.assertTrue(!level.getBlockState(ore).is(Blocks.DIAMOND_ORE), "Miner must replace the queued ore");
            helper.assertTrue(restored.exportItems.storage.getStackInSlot(0).is(Items.DIAMOND_ORE), "Native silk-touch loot must enter the output inventory");
        });
    }

    @GameTest(template = "empty")
    public static void unlimitedExportHonorsBudgetAndVanillaCapacity(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
        var source = new UnlimitedItemStackTransfer(1);
        source.setStackInSlot(0, new ItemStack(Items.DIAMOND, 5000));
        UnlimitItemTransferHelper.exportToTarget(source, 100, s -> true, level, pos, Direction.UP);
        helper.assertTrue(source.getStackInSlot(0).getCount() == 4900, "Export budget must apply across ordinary stacks");
        UnlimitItemTransferHelper.exportToTarget(source, Integer.MAX_VALUE, s -> true, level, pos, Direction.UP);
        helper.assertTrue(source.getStackInSlot(0).getCount() == 5000 - 27 * 64, "Full vanilla target must return the remainder without discarding items");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void programmableTraitUsesNativeSingleMachineAndSurvivesReload(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var state = GTMachines.MACERATOR[GTValues.LV].get().getBlock().defaultBlockState();
        level.setBlockAndUpdate(pos, state);
        var machine = (SimpleTieredMachine) MetaMachine.getMachine(level, pos);
        var programming = machine.getTraits(ProgrammableCircuitHandler.class);
        helper.assertTrue(programming.size() == 1, "Mixin must attach exactly one persistent programming trait to native GT8 machines");
        var cover = GTMTCovers.PROGRAMMABLE_COVER.value().createCoverBehavior(machine.getCoverContainer(), Direction.UP);
        helper.assertTrue(cover.canAttach(), "Programmable cover must attach to a native simple machine");
        cover.onAttached(CustomItems.PROGRAMMABLE_COVER.asStack(), null);
        machine.getCoverContainer().setCoverAtSide(cover, Direction.UP);
        var provider = CustomItems.VIRTUAL_ITEM_PROVIDER.asStack();
        provider.set(GTMTDataComponents.VIRTUAL_ITEM, new VirtualItemData(new ItemStack(Items.DIAMOND)));
        var handler = programming.getFirst();
        handler.storage.insertItem(0, provider, true);
        helper.assertTrue(handler.storage.getStackInSlot(0).isEmpty(), "Programming simulation cannot mutate its persistent recipe inventory");
        handler.storage.insertItem(0, provider, false);
        helper.assertTrue(handler.storage.getStackInSlot(0).is(Items.DIAMOND), "Programming must expose configured recipe contents");
        helper.assertTrue(handler.extractItem(0, 1, false).isEmpty(), "Virtual recipe contents cannot be physically extracted");
        var restored = (SimpleTieredMachine) BlockEntity.loadStatic(pos, state, machine.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
        helper.assertTrue(restored.getTraits(ProgrammableCircuitHandler.class).getFirst().storage.getStackInSlot(0).is(Items.DIAMOND), "Programming configuration must survive save and reload");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void catalystInventoriesNeverSupplyConsumableIngredients(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var level = helper.getLevel();
        level.setBlockAndUpdate(pos, CustomMachines.HUGE_ITEM_IMPORT_BUS[1].get().getBlock().defaultBlockState());
        var machine = MetaMachine.getMachine(level, pos);
        var items = machine.attachTrait(new CatalystItemStackHandler(1, IO.IN));
        items.storage.setStackInSlot(0, new ItemStack(Items.DIAMOND));
        var consumable = GTRecipeTypes.ASSEMBLER_RECIPES.recipeBuilder(GTMThings.id("test_consume")).inputItems(Items.DIAMOND).build();
        var remaining = items.handleRecipeInner(IO.IN, consumable, new ArrayList<>(List.of(new SizedIngredient(Ingredient.of(Items.DIAMOND), 1))), false);
        helper.assertTrue(remaining != null && !remaining.isEmpty() && items.storage.getStackInSlot(0).getCount() == 1, "Shared catalyst storage cannot satisfy a consumable item input");
        var catalyst = GTRecipeTypes.ASSEMBLER_RECIPES.recipeBuilder(GTMThings.id("test_catalyst")).notConsumable(Items.DIAMOND).build();
        remaining = items.handleRecipeInner(IO.IN, catalyst, new ArrayList<>(List.of(new SizedIngredient(Ingredient.of(Items.DIAMOND), 1))), true);
        helper.assertTrue((remaining == null || remaining.isEmpty()) && items.storage.getStackInSlot(0).getCount() == 1, "Shared catalyst storage must match nonconsumable item input without consuming it");
        var fluids = machine.attachTrait(new CatalystFluidStackHandler(1, 16000, IO.IN, IO.IN));
        fluids.getStorages()[0].setFluid(new FluidStack(Fluids.WATER, 1000));
        var fluidRecipe = GTRecipeTypes.ASSEMBLER_RECIPES.recipeBuilder(GTMThings.id("test_fluid")).inputFluids(new FluidStack(Fluids.WATER, 1000)).build();
        var fluidRemaining = fluids.handleRecipeInner(IO.IN, fluidRecipe, new ArrayList<>(List.of(SizedFluidIngredient.of(Fluids.WATER, 1000))), false);
        helper.assertTrue(fluidRemaining != null && !fluidRemaining.isEmpty() && fluids.getFluidInTank(0).getAmount() == 1000, "Shared catalyst storage cannot satisfy consumable fluid input");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void advancedTerminalBuildsNativeBlastFurnace(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(pos, GTMultiMachines.ELECTRIC_BLAST_FURNACE.get().getBlock().defaultBlockState());
        var machine = (MultiblockControllerMachine) MetaMachine.getMachine(level, pos);
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        var changed = new AdvancedBlockPattern(machine.getDefaultStructurePattern()).autoBuild(player, machine, new AdvancedTerminalBehavior.AutoBuildSetting(1, 1, false, false, false));
        helper.assertTrue(changed, "Terminal must place native GT8 structure blocks");
        machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed(), "Terminal-built blast furnace must pass the native structure validator: " + machine.getPatternState(MultiblockControllerMachine.DEFAULT_STRUCTURE));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void wirelessCoverDebitsOnlyAcceptedEnergy(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos, GTMachines.MACERATOR[1].get().getBlock().defaultBlockState());
        var machine = (SimpleTieredMachine) MetaMachine.getMachine(level, pos);
        var owner = java.util.UUID.randomUUID();
        machine.setOwnerUUID(owner);
        var source = com.hepdd.gtmthings.api.misc.WirelessEnergyContainer.getOrCreateContainer(owner);
        source.setStorage(java.math.BigInteger.valueOf(17));
        machine.energyContainer.setEnergyStored(machine.energyContainer.getEnergyCapacity() - 3);
        var cover = GTMTCovers.WIRELESS_ENERGY_RECEIVE[0].value().createCoverBehavior(machine.getCoverContainer(), Direction.UP);
        helper.assertTrue(cover.canAttach(), "Wireless receiver must attach to the native LV machine");
        machine.getCoverContainer().setCoverAtSide(cover, Direction.UP);
        cover.onLoad();
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(machine.energyContainer.getEnergyStored() == machine.energyContainer.getEnergyCapacity(), "Wireless cover must fill the three EU of remaining capacity");
            helper.assertTrue(source.getStorage().equals(java.math.BigInteger.valueOf(14)), "A full machine cannot debit rejected wireless energy");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void computationBindingsReconnectAndPersistBothPartners(GameTestHelper helper) {
        var level = helper.getLevel();
        var a = helper.absolutePos(new BlockPos(1, 2, 2));
        var b = a.east();
        var c = b.east();
        var transmitter = com.hepdd.gtmthings.data.WirelessMachines.WIRELESS_COMPUTATION_HATCH_TRANSMITTER;
        var receiver = com.hepdd.gtmthings.data.WirelessMachines.WIRELESS_COMPUTATION_HATCH_RECEIVER;
        level.setBlockAndUpdate(a, transmitter.get().getBlock().defaultBlockState());
        level.setBlockAndUpdate(b, receiver.get().getBlock().defaultBlockState());
        level.setBlockAndUpdate(c, receiver.get().getBlock().defaultBlockState());
        var sender = (com.hepdd.gtmthings.common.block.machine.multiblock.part.computation.WirelessOpticalComputationHatchMachine) MetaMachine.getMachine(level, a);
        var first = (com.hepdd.gtmthings.common.block.machine.multiblock.part.computation.WirelessOpticalComputationHatchMachine) MetaMachine.getMachine(level, b);
        var second = (com.hepdd.gtmthings.common.block.machine.multiblock.part.computation.WirelessOpticalComputationHatchMachine) MetaMachine.getMachine(level, c);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var stick = com.gregtechceu.gtceu.common.data.GTItems.TOOL_DATA_STICK.asStack();
        sender.onDataStickShiftUse(player, stick);
        first.onDataStickUse(player, stick);
        helper.assertTrue(a.equals(first.getTransmitterPos()) && b.equals(sender.getReceiverPos()), "Data stick must bind both endpoints");
        second.onDataStickUse(player, stick);
        helper.assertTrue(first.getTransmitterPos() == null && c.equals(sender.getReceiverPos()) && a.equals(second.getTransmitterPos()), "Rebinding must clear the old partner");
        var restored = (com.hepdd.gtmthings.common.block.machine.multiblock.part.computation.WirelessOpticalComputationHatchMachine) BlockEntity.loadStatic(c, second.getBlockState(), second.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
        helper.assertTrue(a.equals(restored.getTransmitterPos()), "Computation binding must survive native persistence");
        helper.assertTrue(second.getComputationContainer().requestCWUt(10, true) == 0, "An unformed transmitter cannot supply computation");
        level.setBlockAndUpdate(a, Blocks.AIR.defaultBlockState());
        helper.assertTrue(second.getTransmitterPos() == null, "Destroying an endpoint must unlink the surviving partner");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void teamOwnerNamesResolvePlayerAndTeamIds(GameTestHelper helper) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var level = helper.getLevel();
        var unknown = java.util.UUID.randomUUID();
        helper.assertTrue(com.hepdd.gtmthings.utils.TeamUtil.getTeamUUID(unknown).equals(unknown), "Unknown players must retain their own wireless network identity");
        if (net.neoforged.fml.ModList.get().isLoaded("ftbteams")) {
            var api = dev.ftb.mods.ftbteams.api.FTBTeamsAPI.api();
            helper.assertTrue(api.isManagerLoaded(), "FTB Teams manager must be available after server startup");
            var team = api.getManager().createServerTeam(level.getServer().createCommandSourceStack(), "port-" + unknown.toString().substring(0, 8), "Port integration test", dev.ftb.mods.ftblibrary.icon.Color4I.WHITE);
            helper.assertTrue(com.hepdd.gtmthings.utils.TeamUtil.hasOwner(level, team.getTeamId()), "Persisted team IDs must resolve as owners even when all members are offline");
            helper.assertTrue(com.hepdd.gtmthings.utils.TeamUtil.GetName(level, team.getTeamId()).getString().equals(team.getName().getString()), "Monitor and Jade must display a team name for a stored team ID");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void terminalBuildsEachHorizontalRotation(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 1, 2));
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        for (var direction : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
            for (var clear : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 4, 2))) level.setBlockAndUpdate(clear, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos, GTMultiMachines.ELECTRIC_BLAST_FURNACE.get().getBlock().defaultBlockState());
            var controller = (MultiblockControllerMachine) MetaMachine.getMachine(level, pos);
            controller.setFrontFacing(direction);
            new AdvancedBlockPattern(controller.getDefaultStructurePattern()).autoBuild(player, controller, new AdvancedTerminalBehavior.AutoBuildSetting(1, 1, false, false, false));
            controller.checkAndFormStructure();
            helper.assertTrue(controller.isFormed(), "Native structure must form facing " + direction + ": " + controller.getDefaultPatternState());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void terminalNoHatchModeFillsOptionalHatchPositionsWithCasing(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(pos, GTMultiMachines.ELECTRIC_BLAST_FURNACE.get().getBlock().defaultBlockState());
        var controller = (MultiblockControllerMachine) MetaMachine.getMachine(level, pos);
        new AdvancedBlockPattern(controller.getDefaultStructurePattern()).autoBuild(helper.makeMockPlayer(GameType.CREATIVE), controller, new AdvancedTerminalBehavior.AutoBuildSetting(1, 1, true, false, false));
        int casings = 0;
        for (var p : BlockPos.betweenClosed(pos.offset(-2, 0, -2), pos.offset(2, 4, 2))) {
            var block = level.getBlockState(p).getBlock();
            helper.assertTrue(!com.hepdd.gtmthings.api.misc.Hatch.isHatch(block), "No-hatch mode cannot place a multiblock part");
            if (block == com.gregtechceu.gtceu.common.data.GTBlocks.CASING_INVAR_HEATPROOF.get()) casings++;
        }
        helper.assertTrue(casings == 16, "All 16 optional hatch positions must be filled with casing, got " + casings);
        helper.succeed();
    }

    @GameTest(template = "empty_large", timeoutTicks = 300)
    public static void terminalRespectsNativeAssemblyLineRepetitionAndFlip(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(12, 3, 12));
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        level.setBlockAndUpdate(pos, GTMultiMachines.ASSEMBLY_LINE.get().getBlock().defaultBlockState());
        var controller = (MultiblockControllerMachine) MetaMachine.getMachine(level, pos);
        controller.setFrontFacing(Direction.EAST);
        controller.setFlipped(true);
        new AdvancedBlockPattern(controller.getDefaultStructurePattern()).autoBuild(player, controller, new AdvancedTerminalBehavior.AutoBuildSetting(0, 5, false, false, false));
        controller.checkAndFormStructure();
        if (!controller.isFormed()) {
            var blocks = new java.util.TreeMap<String, Integer>();
            for (var p : BlockPos.betweenClosed(pos.offset(-12, -2, -12), pos.offset(11, 2, 11))) {
                var block = level.getBlockState(p).getBlock();
                if (block != Blocks.AIR) blocks.merge(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString(), 1, Integer::sum);
            }
            GTMThings.LOGGER.info("PORT ASSEMBLY DIAGNOSTIC {}", blocks);
            var bp = (com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern) controller.getDefaultStructurePattern();
            bp.getPredicates().get('D').expand().forEach(base -> GTMThings.LOGGER.info("PORT DATA PREDICATE {} min {} max {} candidates {}", base, base.getMinCount(), base.getMaxCount(), base.getCandidates()));
        }
        helper.assertTrue(controller.isFormed(), "Flipped, repeated native assembly line must form: " + controller.getDefaultPatternState().getErrors().stream().map(e -> e instanceof com.gregtechceu.gtceu.api.multiblock.error.PatternStringError text ? text.getComponent().toString() : e.getClass().getSimpleName() + " at " + e.getPos() + " actual " + level.getBlockState(e.getPos())).toList());
        long buses = BlockPos.betweenClosedStream(pos.offset(-12, -2, -12), pos.offset(11, 2, 11)).filter(p -> level.getBlockState(p).is(GTMachines.ITEM_IMPORT_BUS[0].get().getBlock())).count();
        helper.assertTrue(buses == 6, "Five repeated slices plus the initial slice must provide six input buses, got " + buses);
        helper.assertTrue(controller.isFlipped(), "Native validator must retain the terminal's mirrored layout");
        helper.assertTrue(!new AdvancedBlockPattern(controller.getDefaultStructurePattern()).autoBuild(player, controller, new AdvancedTerminalBehavior.AutoBuildSetting(0, 5, false, false, false)), "Rebuilding a valid repeated structure must not add or consume blocks");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void exclusiveHatchMinimumIsGlobalUnlessSliceLimitIsExplicit(GameTestHelper helper) {
        var global = com.gregtechceu.gtceu.api.multiblock.Predicates.blocks(Blocks.GOLD_BLOCK)
                .xor(com.gregtechceu.gtceu.api.multiblock.Predicates.blocks(Blocks.EMERALD_BLOCK)).setExactLimit(1);
        var context = new com.gregtechceu.gtceu.api.multiblock.pattern.PatternState().resetContext(true);
        helper.assertTrue(global.postSliceTest(context), "A globally required XOR branch need not be present in each slice");
        helper.assertTrue(!global.postGlobalTest(context), "A globally required XOR branch must still be required for the whole structure");
        helper.assertTrue(!global.setMinSliceCount(1).postSliceTest(context), "Explicit per-slice minima must still be enforced");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void terminalSurvivalConstructionConsumesInventoryExactlyOnce(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(pos, GTMultiMachines.ELECTRIC_BLAST_FURNACE.get().getBlock().defaultBlockState());
        var controller = (MultiblockControllerMachine) MetaMachine.getMachine(level, pos);
        var pattern = new AdvancedBlockPattern(controller.getDefaultStructurePattern());
        var options = new AdvancedTerminalBehavior.AutoBuildSetting(1, 1, false, false, false);
        pattern.autoBuild(helper.makeMockPlayer(GameType.CREATIVE), controller, options);
        var needed = new java.util.LinkedHashMap<net.minecraft.world.item.Item, Integer>();
        for (var p : BlockPos.betweenClosed(pos.offset(-2, 0, -2), pos.offset(2, 4, 2))) {
            if (p.equals(pos)) continue;
            var state = level.getBlockState(p);
            if (!state.isAir()) {
                needed.merge(state.getBlock().asItem(), 1, Integer::sum);
                level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
            }
        }
        controller.invalidateAllStructures();
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        int slot = 0;
        for (var entry : needed.entrySet()) player.getInventory().setItem(slot++, new ItemStack(entry.getKey(), entry.getValue()));
        pattern.autoBuild(player, controller, options);
        controller.checkAndFormStructure();
        helper.assertTrue(controller.isFormed(), "Survival terminal must form the same native structure from available inventory");
        helper.assertTrue(player.getInventory().isEmpty(), "Each survival block must be consumed exactly once");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void canceledCoilReplacementRestoresBlocksAndRefundsMaterials(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(pos, GTMultiMachines.ELECTRIC_BLAST_FURNACE.get().getBlock().defaultBlockState());
        var controller = (MultiblockControllerMachine) MetaMachine.getMachine(level, pos);
        var pattern = new AdvancedBlockPattern(controller.getDefaultStructurePattern());
        pattern.autoBuild(helper.makeMockPlayer(GameType.CREATIVE), controller, new AdvancedTerminalBehavior.AutoBuildSetting(1, 1, false, false, false));
        controller.checkAndFormStructure();
        var replacement = com.gregtechceu.gtceu.api.GTCEuAPI.HEATING_COILS.entrySet().stream().filter(e -> e.getKey().getTier() == 1).findFirst().orElseThrow().getValue().get();
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(replacement, 16));
        java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent> cancel = event -> { if (event.getEntity() == player) event.setCanceled(true); };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cancel);
        try {
            helper.assertTrue(!pattern.autoBuild(player, controller, new AdvancedTerminalBehavior.AutoBuildSetting(2, 1, false, true, false)), "Canceled replacement cannot report placed blocks");
        } finally {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel);
        }
        helper.assertTrue(player.getInventory().getItem(0).getCount() == 16, "Canceled coil placement must refund every extracted item");
        int coils = 0;
        for (var p : BlockPos.betweenClosed(pos.offset(-2, 0, -2), pos.offset(2, 4, 2))) if (level.getBlockState(p).getBlock() instanceof com.gregtechceu.gtceu.common.block.CoilBlock coil) {
            helper.assertTrue(coil.coilType.getTier() == 0, "Cancellation must restore the original coil block");
            coils++;
        }
        helper.assertTrue(coils == 16, "The complete original coil ring must survive canceled replacement");
        helper.succeed();
    }

    private PortIntegrationGameTests() {}
}
