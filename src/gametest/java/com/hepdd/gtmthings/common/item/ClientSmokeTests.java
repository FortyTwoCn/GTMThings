package com.hepdd.gtmthings.common.item;

import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.mui.factory.MachineUIFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import brachy.modularui.api.IMuiScreen;
import brachy.modularui.factory.PlayerInventoryUIFactory;
import com.hepdd.gtmthings.GTMThings;
import com.hepdd.gtmthings.data.CreativeMachines;
import com.hepdd.gtmthings.data.CustomItems;
import com.hepdd.gtmthings.data.CustomMachines;
import com.hepdd.gtmthings.data.WirelessMachines;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Opt-in development-only client smoke run: actual server menus, packets, widget layout and rendering. */
@EventBusSubscriber(modid = "gtmthings", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ClientSmokeTests {

    private static List<Supplier<ItemStack>> subjects;
    private static boolean complete;
    private static int ticks, index, menuTicks;
    private static CompletableFuture<Void> opening;
    private static boolean menuRequested;
    private static CompletableFuture<Void> validation;
    private static BlockPos position;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (complete || !Boolean.getBoolean("gtmthings.portSmoke")) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        if (subjects == null) {
            subjects = List.of(
                    () -> CustomMachines.DIGITAL_MINER[1].asStack(),
                    () -> CustomMachines.HUGE_ITEM_IMPORT_BUS[1].asStack(),
                    () -> CustomMachines.HUGE_INPUT_DUAL_HATCH[4].asStack(),
                    CreativeMachines.CREATIVE_ITEM_INPUT_BUS::asStack,
                    CreativeMachines.CREATIVE_FLUID_INPUT_HATCH::asStack,
                    CreativeMachines.CREATIVE_ENERGY_INPUT_HATCH::asStack,
                    CreativeMachines.CREATIVE_LASER_INPUT_HATCH::asStack,
                    CustomMachines.ME_EXPORT_BUFFER::asStack,
                    WirelessMachines.WIRELESS_ENERGY_MONITOR::asStack,
                    () -> {
                        var stack = CustomItems.VIRTUAL_ITEM_PROVIDER.asStack();
                        VirtualItemProviderBehavior.setVirtualItem(stack, new ItemStack(net.minecraft.world.item.Items.DIAMOND));
                        return stack;
                    },
                    CustomItems.ADVANCED_TERMINAL::asStack,
                    CustomItems.WIRELESS_ENERGY_TERMINAL::asStack,
                    () -> {
                        var stack = CreativeMachines.CREATIVE_FLUID_CELL.asStack();
                        CreativeFluidStats.setStored(stack, new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000));
                        return stack;
                    },
                    () -> WirelessMachines.WIRELESS_ENERGY_INPUT_HATCH[1].asStack(),
                    () -> WirelessMachines.WIRELESS_ENERGY_INPUT_HATCH_65536A[4].asStack(),
                    WirelessMachines.WIRELESS_COMPUTATION_HATCH_RECEIVER::asStack,
                    WirelessMachines.WIRELESS_COMPUTATION_HATCH_TRANSMITTER::asStack,
                    () -> CustomMachines.PROGRAMMABLEC_HATCH[6].asStack(),
                    CustomItems.ADVANCED_WIRELESS_ITEM_TRANSFER_COVER::asStack,
                    CustomItems.ADVANCED_WIRELESS_FLUID_TRANSFER_COVER::asStack,
                    () -> {
                        var stack = CustomMachines.ME_EXPORT_BUFFER.asStack();
                        stack.set(com.hepdd.gtmthings.data.GTMTDataComponents.MARKED, true);
                        return stack;
                    });
            position = mc.player.blockPosition().offset(2, 0, 0);
        }
        if (++ticks < 100) return;
        if (opening == null) {
            if (index == subjects.size()) {
                if (!dev.emi.emi.runtime.EmiReloadManager.isLoaded()) return;
                complete = true;
                GTMThings.LOGGER.info("PORT CLIENT SMOKE PASSED: {} native GUIs", subjects.size());
                mc.stop();
                return;
            }
            ItemStack stack = subjects.get(index).get();
            var server = mc.getSingleplayerServer();
            var uuid = mc.player.getUUID();
            opening = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(uuid);
                if (player == null) throw new IllegalStateException("Smoke player is absent");
                player.closeContainer();
                // Keep the player in reach throughout menu construction and packet delivery.
                player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                position = player.blockPosition().offset(2, 0, 0);
                if (isCover(stack)) {
                    player.serverLevel().setBlockAndUpdate(position, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    player.serverLevel().setBlockAndUpdate(position, com.gregtechceu.gtceu.common.data.GTMachines.CHEMICAL_REACTOR[1].get().getBlock().defaultBlockState());
                    var machine = com.gregtechceu.gtceu.api.machine.MetaMachine.getMachine(player.serverLevel(), position);
                    var definition = stack.is(CustomItems.ADVANCED_WIRELESS_ITEM_TRANSFER_COVER.asItem()) ? com.hepdd.gtmthings.data.GTMTCovers.ADVANCED_WIRELESS_ITEM_TRANSFER.value() : com.hepdd.gtmthings.data.GTMTCovers.ADVANCED_WIRELESS_FLUID_TRANSFER.value();
                    if (!machine.getCoverContainer().placeCoverOnSide(net.minecraft.core.Direction.UP, stack, definition, player)) throw new IllegalStateException("Cover could not attach " + stack);
                    com.hepdd.gtmthings.common.cover.PortCoverGameTests.seedFilter(machine.getCoverContainer().getCoverAtSide(net.minecraft.core.Direction.UP));
                } else if (stack.getItem() instanceof com.gregtechceu.gtceu.api.item.MetaMachineItem item) {
                    player.serverLevel().setBlockAndUpdate(position, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    player.serverLevel().setBlockAndUpdate(position, item.getBlock().defaultBlockState());
                    var machine = com.gregtechceu.gtceu.api.machine.MetaMachine.getMachine(player.serverLevel(), position);
                    if (!(machine instanceof IMuiMachine)) throw new IllegalStateException("Missing native UI for " + stack);
                    if (stack.getOrDefault(com.hepdd.gtmthings.data.GTMTDataComponents.MARKED, false)) {
                        machine.getTraits(com.hepdd.gtmthings.api.machine.trait.InaccessibleInfiniteHandler.class).getFirst().handleRecipeInner(
                                com.gregtechceu.gtceu.api.capability.recipe.IO.OUT, null, new java.util.ArrayList<>(java.util.List.of(net.neoforged.neoforge.common.crafting.SizedIngredient.of(net.minecraft.world.item.Items.DIAMOND, 1234567))), false);
                        machine.getTraits(com.hepdd.gtmthings.api.machine.trait.InaccessibleInfiniteTank.class).getFirst().handleRecipeInner(
                                com.gregtechceu.gtceu.api.capability.recipe.IO.OUT, null, new java.util.ArrayList<>(java.util.List.of(net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient.of(net.minecraft.world.level.material.Fluids.WATER, 9876543))), false);
                    }

                } else {
                    player.setItemInHand(InteractionHand.OFF_HAND, stack);

                }
                GTMThings.LOGGER.info("PORT GUI FIXTURE {}: {} at {}", index, stack, position);
            });
            menuTicks = 0;
            menuRequested = false;
            validation = null;
        } else if (opening.isDone()) {
            opening.join(); // Surface server-side UI exceptions in this test instead of silently timing out.
            if (!menuRequested) {
                ItemStack stack = subjects.get(index).get();
                // Chunk block updates and inventory changes are flushed after the server task finishes.
                // A normal player opens an existing block; this fixture must wait for that state too.
                boolean ready = isCover(stack) ? com.gregtechceu.gtceu.api.machine.MetaMachine.getMachine(mc.level, position) instanceof com.gregtechceu.gtceu.api.machine.MetaMachine machine && machine.getCoverContainer().getCoverAtSide(net.minecraft.core.Direction.UP) != null : stack.getItem() instanceof com.gregtechceu.gtceu.api.item.MetaMachineItem item ? mc.level.getBlockState(position).is(item.getBlock()) && mc.level.getBlockEntity(position) instanceof IMuiMachine : ItemStack.isSameItemSameComponents(mc.player.getOffhandItem(), stack);
                if (!ready) {
                    if (++menuTicks > 400) throw new IllegalStateException("Client fixture never synchronized for " + stack);
                    return;
                }
                var server = mc.getSingleplayerServer();
                var uuid = mc.player.getUUID();
                opening = server.submit(() -> {
                    var player = server.getPlayerList().getPlayer(uuid);
                    if (isCover(stack))
                        com.gregtechceu.gtceu.api.mui.factory.CoverUIFactory.INSTANCE.open(player, (com.gregtechceu.gtceu.api.cover.IMuiCover) com.gregtechceu.gtceu.api.machine.MetaMachine.getMachine(player.serverLevel(), position).getCoverContainer().getCoverAtSide(net.minecraft.core.Direction.UP));
                    else if (stack.getItem() instanceof com.gregtechceu.gtceu.api.item.MetaMachineItem)
                        MachineUIFactory.INSTANCE.open(player, (IMuiMachine) com.gregtechceu.gtceu.api.machine.MetaMachine.getMachine(player.serverLevel(), position));
                    else PlayerInventoryUIFactory.INSTANCE.openFromHand(player, InteractionHand.OFF_HAND);
                    GTMThings.LOGGER.info("PORT GUI SERVER OPEN {}: {}", index, stack);
                });
                menuRequested = true;
                menuTicks = 0;
                return;
            }
            ++menuTicks;
            if (menuTicks == 30 && mc.screen instanceof IMuiScreen && (index == 18 || index == 19)) {
                var manager = ((brachy.modularui.screen.ModularContainerMenu) mc.player.containerMenu).getSyncManager().getMainPSM();
                manager.findPanelHandlerNullable("filterPanel").openPanel();
            }
            if (menuTicks == 20 && mc.screen instanceof IMuiScreen && (index == 0 || index == 10)) {
                var manager = ((brachy.modularui.screen.ModularContainerMenu) mc.player.containerMenu).getSyncManager().getMainPSM();
                String name = index == 0 ? "radius" : "RepeatCount";
                var value = (brachy.modularui.value.sync.IntSyncValue) manager.getSyncHandlerFromMapKey(brachy.modularui.value.sync.PanelSyncManager.makeSyncKey(name, 0));
                value.setIntValue(index == 0 ? 3 : 7, true, true);
            }
            if (menuTicks < 60) return;
            if (!(mc.screen instanceof IMuiScreen)) {
                if (menuTicks < 400) return;
                throw new IllegalStateException("Native screen did not open for " + subjects.get(index).get() + "; actual screen " + mc.screen);
            }
            if (validation == null && (index == 0 || index == 10)) {
                int current = index;
                var uuid = mc.player.getUUID();
                var server = mc.getSingleplayerServer();
                validation = server.submit(() -> {
                    if (current == 0) {
                        var miner = (com.hepdd.gtmthings.common.block.machine.electric.DigitalMiner) com.gregtechceu.gtceu.api.machine.MetaMachine.getMachine(server.getPlayerList().getPlayer(uuid).serverLevel(), position);
                        if (miner.getMinerRadius() != 3) throw new IllegalStateException("Miner GUI packet did not update the authoritative radius");
                    } else if (AdvancedTerminalBehavior.getAutoBuildSetting(server.getPlayerList().getPlayer(uuid).getOffhandItem()).repeatCount() != 7) {
                        throw new IllegalStateException("Terminal GUI packet did not update the actual offhand stack");
                    }
                    GTMThings.LOGGER.info("PORT GUI C2S PASSED {}", current);
                });
                return;
            }
            if (validation != null) {
                if (!validation.isDone()) return;
                validation.join();
            }
            var area = ((IMuiScreen) mc.screen).screen().getMainPanel().getArea();
            if (area.x < 0 || area.y < 0 || area.x + area.width > mc.screen.width || area.y + area.height > mc.screen.height)
                throw new IllegalStateException("Native GUI is clipped for " + subjects.get(index).get() + ": " + area);
            if (index == 18 || index == 19) {
                var manager = ((brachy.modularui.screen.ModularContainerMenu) mc.player.containerMenu).getSyncManager().getMainPSM();
                if (!manager.findPanelHandlerNullable("filterPanel").isPanelOpen())
                    throw new IllegalStateException("Native cover filter popup did not open");
            }
            Screenshot.grab(mc.gameDirectory, "port_gui_" + index + ".png", mc.getMainRenderTarget(), component -> {});
            GTMThings.LOGGER.info("PORT GUI PASSED {}: {}", index, subjects.get(index).get().getItem());
            mc.player.closeContainer();
            index++;
            opening = null;
        }
    }

    private static boolean isCover(ItemStack stack) {
        return stack.is(CustomItems.ADVANCED_WIRELESS_ITEM_TRANSFER_COVER.asItem()) || stack.is(CustomItems.ADVANCED_WIRELESS_FLUID_TRANSFER_COVER.asItem());
    }

    private ClientSmokeTests() {}
}
