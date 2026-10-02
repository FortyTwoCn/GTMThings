package com.hepdd.gtmthings.common.block.machine.electric;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;

import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import com.hepdd.gtmthings.api.gui.WirelessMonitorUI;
import com.hepdd.gtmthings.api.misc.WirelessEnergyContainer;
import com.hepdd.gtmthings.common.item.IWirelessMonitor;

import java.util.UUID;

public class WirelessEnergyMonitor extends MetaMachine implements IMuiMachine, IWirelessMonitor {

    public static final int DISPLAY_TEXT_WIDTH = 220;
    private WirelessEnergyContainer containerCache;
    @SaveField
    private boolean all;

    public WirelessEnergyMonitor(BlockEntityCreationInfo info) {
        super(info);
    }

    @Override
    public void buildMainUI(ParentWidget<?> main, PosGuiData data, PanelSyncManager manager, UISettings settings) {
        var allValue = new BooleanSyncValue(() -> all, value -> {
            all = value;
            markAsChanged();
        }).allowC2S();
        main.child(WirelessMonitorUI.create(this, allValue, manager));
    }

    @Override
    public UUID getUUID() {
        return getOwnerUUID();
    }

    @Override
    public boolean display() {
        return false;
    }

    @Override
    public WirelessEnergyContainer getWirelessEnergyContainerCache() {
        return containerCache;
    }

    @Override
    public void setWirelessEnergyContainerCache(WirelessEnergyContainer container) {
        containerCache = container;
    }
}
