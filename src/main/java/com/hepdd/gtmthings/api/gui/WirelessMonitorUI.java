package com.hepdd.gtmthings.api.gui;

import net.minecraft.network.chat.Component;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widgets.ToggleButton;
import brachy.modularui.widgets.layout.Flow;
import com.hepdd.gtmthings.common.item.IWirelessMonitor;

import java.util.List;

public final class WirelessMonitorUI {

    public static Flow create(IWirelessMonitor monitor, BooleanSyncValue all, PanelSyncManager manager) {
        manager.syncValue("all", all);
        return Flow.column().width(228).coverChildrenHeight().padding(4).childPadding(4)
                .child(new ToggleButton().widthRel(1).height(18).value(all)
                        .overlay(Text.dynamic(() -> Component.translatable("gtmthings.machine.wireless_energy_monitor.tooltip.statistics")
                                .append(Component.translatable(all.getBoolValue() ? "gtmthings.machine.wireless_energy_monitor.tooltip.all" : "gtmthings.machine.wireless_energy_monitor.tooltip.team")))))
                .child(ComponentListUI.create("monitor", manager,
                        () -> monitor.getLevel() == null || monitor.getLevel().isClientSide ? List.of() : monitor.getDisplayText(all.getBoolValue(), 220), 135));
    }

    private WirelessMonitorUI() {}
}
