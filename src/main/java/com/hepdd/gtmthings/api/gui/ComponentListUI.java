package com.hepdd.gtmthings.api.gui;

import com.gregtechceu.gtceu.common.mui.GTByteBufAdapters;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.value.sync.DynamicLinkedSyncHandler;
import brachy.modularui.value.sync.GenericListSyncHandler;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ScrollWidget;
import brachy.modularui.widget.scroll.VerticalScrollData;
import brachy.modularui.widgets.dynamic.DynamicWidget;
import brachy.modularui.widgets.layout.Flow;

import java.util.List;
import java.util.function.Supplier;

/** Server-generated rich text synchronized through the component codec. */
public final class ComponentListUI {

    public static ScrollWidget<?> create(String name, PanelSyncManager manager, Supplier<List<Component>> text, int height) {
        var lines = GenericListSyncHandler.<FriendlyByteBuf, Component>builder()
                .getter(text).adapter(GTByteBufAdapters.COMPONENT).build();
        manager.syncValue(name, lines);
        var dynamic = new DynamicLinkedSyncHandler<>(lines).widgetProvider((sync, value) -> {
            Flow column = Flow.column().widthRel(1).coverChildrenHeight().childPadding(3);
            for (Component component : value.getValue()) {
                String action = HighlightTextWidget.findAction(component);
                column.child(action == null ? Text.of(component).asWidget().widthRel(1) : new HighlightTextWidget(component, action).widthRel(1));
            }
            return column;
        });
        return new ScrollWidget<>(new VerticalScrollData()).widthRel(1).height(height).padding(5)
                .child(new DynamicWidget<>().widthRel(1).coverChildrenHeight().syncHandler(dynamic));
    }

    private ComponentListUI() {}
}
