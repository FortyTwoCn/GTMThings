package com.hepdd.gtmthings.common.item;

import com.gregtechceu.gtceu.api.mui.IItemUIHolder;

import net.minecraft.world.level.Level;

import brachy.modularui.factory.PlayerInventoryGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import com.hepdd.gtmthings.api.gui.WirelessMonitorUI;
import com.hepdd.gtmthings.api.misc.WirelessEnergyContainer;
import com.hepdd.gtmthings.utils.ItemData;

import java.util.UUID;

public class WirelessEnergyTerminalBehavior implements IItemUIHolder {

    @Override
    public ModularPanel<?> buildUI(PlayerInventoryGuiData<?> data, PanelSyncManager manager, UISettings settings) {
        WirelessMonitor monitor = new WirelessMonitor(data.getPlayer().getUUID(), data.getPlayer().level());
        var all = new BooleanSyncValue(() -> ItemData.read(data.getUsedItemStack()).getBoolean("all"),
                value -> ItemData.update(data.getUsedItemStack(), tag -> tag.putBoolean("all", value))).allowC2S();
        return ModularPanel.defaultPanel("wireless_energy_terminal", 236, 175)
                .child(WirelessMonitorUI.create(monitor, all, manager));
    }

    private static final class WirelessMonitor implements IWirelessMonitor {

        private final UUID uuid;
        private final Level level;
        private WirelessEnergyContainer cache;

        private WirelessMonitor(UUID uuid, Level level) {
            this.uuid = uuid;
            this.level = level;
        }

        @Override
        public UUID getUUID() {
            return uuid;
        }

        @Override
        public boolean display() {
            return false;
        }

        @Override
        public Level getLevel() {
            return level;
        }

        @Override
        public WirelessEnergyContainer getWirelessEnergyContainerCache() {
            return cache;
        }

        @Override
        public void setWirelessEnergyContainerCache(WirelessEnergyContainer container) {
            cache = container;
        }
    }
}
