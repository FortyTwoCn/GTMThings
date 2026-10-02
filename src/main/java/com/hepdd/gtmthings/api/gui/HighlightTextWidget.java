package com.hepdd.gtmthings.api.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;

import brachy.modularui.api.widget.Interactable;
import brachy.modularui.widgets.TextWidget;
import com.hepdd.gtmthings.client.ClientHighlights;

/** Transfer rows expose only a local highlight action; no chat commands are executed. */
public final class HighlightTextWidget extends TextWidget<HighlightTextWidget> implements Interactable {

    private final String action;

    public HighlightTextWidget(Component text, String action) {
        super(text);
        this.action = action;
        HoverEvent hover = text.getStyle().getHoverEvent();
        if (hover != null && hover.getAction() == HoverEvent.Action.SHOW_TEXT)
            tooltip(r -> r.addLine(hover.getValue(HoverEvent.Action.SHOW_TEXT)));
    }

    @Override
    public boolean canHoverThrough() {
        return false;
    }

    @Override
    public Result onMousePressed(int button) {
        if (button != 0) return Result.ACCEPT;
        String[] parts = action.split(";", 2);
        var level = Minecraft.getInstance().level;
        if (parts.length == 2 && level != null && level.dimension().location().equals(ResourceLocation.tryParse(parts[0]))) {
            try {
                ClientHighlights.highlight(BlockPos.of(Long.parseLong(parts[1])), 100_000);
                getPanel().closeIfOpen();
                return Result.SUCCESS;
            } catch (NumberFormatException ignored) {}
        }
        return Result.ACCEPT;
    }

    public static String findAction(Component text) {
        ClickEvent event = text.getStyle().getClickEvent();
        if (event != null && event.getAction() == ClickEvent.Action.CHANGE_PAGE && event.getValue().startsWith("gtmthings:highlight:"))
            return event.getValue().substring("gtmthings:highlight:".length());
        for (Component sibling : text.getSiblings()) {
            String found = findAction(sibling);
            if (found != null) return found;
        }
        return null;
    }
}
