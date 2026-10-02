package com.hepdd.gtmthings.api.gui;

import com.gregtechceu.gtceu.api.GTValues;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.value.sync.IntSyncValue;
import brachy.modularui.value.sync.LongSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.textfield.TextFieldWidget;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

public final class EnergyConfigurationUI {

    private EnergyConfigurationUI() {}

    public static void build(ParentWidget<?> parent, PanelSyncManager manager, LongSupplier getVoltage,
                             LongConsumer setVoltage, IntSupplier getAmps, IntConsumer setAmps, int minTier,
                             int minAmps) {
        var voltage = new LongSyncValue(getVoltage, v -> setVoltage.accept(Math.max(GTValues.VEX[minTier], v))).allowC2S();
        var amps = new IntSyncValue(getAmps, a -> setAmps.accept(Math.max(minAmps, Math.min(67108864, a)))).allowC2S();
        manager.syncValue("voltage", voltage);
        manager.syncValue("amps", amps);
        var column = Flow.column().width(180).coverChildrenHeight().childPadding(4);
        column.child(Text.lang("gtceu.creative.energy.voltage").asWidget());
        column.child(new TextFieldWidget().height(18).width(170)
                .setNumbersLong(() -> GTValues.VEX[minTier], () -> Long.MAX_VALUE).value(voltage));
        var tiers = Flow.column().width(170).coverChildrenHeight().childPadding(2);
        for (int row = minTier; row <= GTValues.MAX; row += 5) {
            var buttons = Flow.row().height(18).childPadding(2);
            for (int index = row; index <= Math.min(row + 4, GTValues.MAX); index++) {
                final int tier = index;
                buttons.child(new ButtonWidget<>().size(32, 18).overlay(Text.str(GTValues.VNF[tier]))
                        .onMousePressed((context, button) -> {
                            voltage.setValue(GTValues.VEX[tier]);
                            return true;
                        }));
            }
            tiers.child(buttons);
        }
        column.child(tiers);
        column.child(Text.lang("gtceu.creative.energy.amperage").asWidget());
        column.child(Flow.row().height(18).childPadding(2)
                .child(new ButtonWidget<>().size(18).overlay(Text.str("-"))
                        .onMousePressed((context, button) -> {
                            amps.setValue(Math.max(minAmps, amps.getIntValue() - 1));
                            return true;
                        }))
                .child(new TextFieldWidget().size(128, 18).setNumbers(minAmps, 67108864).value(amps))
                .child(new ButtonWidget<>().size(18).overlay(Text.str("+"))
                        .onMousePressed((context, button) -> {
                            amps.setValue(Math.min(67108864, amps.getIntValue() + 1));
                            return true;
                        })));
        parent.child(column);
    }

    public static long capacity(long voltage, int amps, int ticks) {
        return java.math.BigInteger.valueOf(voltage).multiply(java.math.BigInteger.valueOf(amps))
                .multiply(java.math.BigInteger.valueOf(ticks)).min(java.math.BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }
}
