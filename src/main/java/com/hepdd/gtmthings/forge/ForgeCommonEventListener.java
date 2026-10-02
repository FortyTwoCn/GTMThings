package com.hepdd.gtmthings.forge;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.hepdd.gtmthings.GTMThings;
import com.hepdd.gtmthings.api.misc.WirelessEnergyContainer;
import com.hepdd.gtmthings.common.item.WirelessEnergyBindingToolBehavior;
import com.hepdd.gtmthings.data.WirelessEnergySavaedData;

@net.neoforged.fml.common.EventBusSubscriber(modid = GTMThings.MOD_ID, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.GAME)
public class ForgeCommonEventListener {

    @SubscribeEvent
    public static void onServerTickEvent(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 10 == 0) {
            WirelessEnergyContainer.TRANSFER_SNAPSHOT = java.util.Map.copyOf(WirelessEnergyContainer.TRANSFER_DATA);
            WirelessEnergyContainer.TRANSFER_DATA.clear();
        }
        if (WirelessEnergySavaedData.INSTANCE != null) {
            if (event.getServer().getTickCount() % 20 == 0) {
                boolean refreshBinding = event.getServer().getTickCount() % 200 == 0;
                for (WirelessEnergyContainer container : WirelessEnergySavaedData.INSTANCE.containerMap.values()) {
                    if (refreshBinding) {
                        GlobalPos pos = container.getBindPos();
                        if (pos != null) {
                            long rate = WirelessEnergyBindingToolBehavior.getRate(
                                    event.getServer().getLevel(pos.dimension()), pos.pos());
                            container.setRate(rate);
                        }
                        // no bindPos = leave rate alone
                    }
                    container.getEnergyStat().tick();
                }
            }
        }
    }

    @SubscribeEvent
    public static void beforeServerTick(ServerTickEvent.Pre event) {
        WirelessEnergyContainer.observed = false;
    }

    @SubscribeEvent
    public static void serverSetup(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ServerLevel serverLevel = level.getServer().getLevel(Level.OVERWORLD);
            if (serverLevel == null) return;
            WirelessEnergySavaedData.INSTANCE = WirelessEnergySavaedData.getOrCreate(serverLevel);
            WirelessEnergyContainer.server = level.getServer();
        }
    }

    // fixes 110
    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        WirelessEnergyContainer.TRANSFER_DATA.clear();
        WirelessEnergyContainer.observed = false;
        WirelessEnergyContainer.TRANSFER_SNAPSHOT = java.util.Map.of();
        WirelessEnergyContainer.server = null;
        WirelessEnergySavaedData.INSTANCE = null;
    }
}
