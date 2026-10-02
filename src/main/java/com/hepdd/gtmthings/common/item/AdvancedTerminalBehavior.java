package com.hepdd.gtmthings.common.item;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.mui.IItemUIHolder;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PlayerInventoryGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.IntSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import com.hepdd.gtmthings.api.pattern.AdvancedBlockPattern;
import com.hepdd.gtmthings.utils.ItemData;

import java.util.Comparator;

/** Settings and construction are bound to the terminal actually used, including the offhand. */
public class AdvancedTerminalBehavior implements IItemUIHolder {

    private static final String[] KEYS = { "CoilTier", "RepeatCount", "NoHatchMode", "ReplaceCoilMode", "IsUseAE" };

    private static int setting(ItemStack stack, int index) {
        var tag = ItemData.read(stack);
        return tag.contains(KEYS[index]) ? tag.getInt(KEYS[index]) : index == 2 ? 1 : 0;
    }

    public static AutoBuildSetting getAutoBuildSetting(ItemStack stack) {
        return new AutoBuildSetting(Math.max(0, setting(stack, 0)), Math.clamp(setting(stack, 1), 0, 99),
                setting(stack, 2) != 0, setting(stack, 3) != 0, setting(stack, 4) != 0);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown() || !(MetaMachine.getMachine(context.getLevel(), context.getClickedPos()) instanceof MultiblockControllerMachine controller))
            return InteractionResult.PASS;
        if (!context.getLevel().isClientSide) {
            var options = getAutoBuildSetting(context.getItemInHand());
            boolean changed = false;
            for (var entry : controller.getStructurePatterns().entrySet()) {
                if (controller.getPatternState(entry.getKey()).isFormed() && !options.replaceCoils()) continue;
                changed |= new AdvancedBlockPattern(entry.getValue()).autoBuild(player, controller, options);
            }
            if (changed) {
                controller.invalidateAllStructures();
                controller.checkAndFormStructure();
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public ModularPanel<?> buildUI(PlayerInventoryGuiData<?> data, PanelSyncManager manager, UISettings settings) {
        var column = Flow.column().width(228).coverChildrenHeight().childPadding(4);
        column.child(Text.lang("item.gtmthings.advanced_terminal.setting.title").asWidget());
        for (int i = 0; i < KEYS.length; i++) {
            final int index = i;
            int max = i == 0 ? GTCEuAPI.HEATING_COILS.keySet().stream().mapToInt(c -> c.getTier() + 1).max().orElse(0) : i == 1 ? 99 : 1;
            var value = new IntSyncValue(() -> setting(data.getUsedItemStack(), index),
                    v -> ItemData.update(data.getUsedItemStack(), tag -> tag.putInt(KEYS[index], Math.clamp(v, 0, max)))).allowC2S();
            manager.syncValue(KEYS[i], value);
            var label = Text.lang("item.gtmthings.advanced_terminal.setting." + (i + 1)).asWidget().width(178);
            label.tooltip(t -> {
                t.addLine(Component.translatable("item.gtmthings.advanced_terminal.setting." + (index + 1) + ".tooltip"));
                if (index == 0) GTCEuAPI.HEATING_COILS.entrySet().stream().sorted(Comparator.comparingInt(e -> e.getKey().getTier()))
                        .forEach(e -> t.addLine(Component.literal((e.getKey().getTier() + 1) + ": ").append(e.getValue().get().getName())));
            });
            column.child(Flow.row().height(18).childPadding(4).child(label)
                    .child(new TextFieldWidget().size(40, 18).setNumbers(0, max).value(value)));
        }
        return ModularPanel.defaultPanel("advanced_terminal", 244, 140).child(column.margin(8));
    }

    public record AutoBuildSetting(int coilTier, int repeatCount, boolean noHatches, boolean replaceCoils, boolean useAE) {}
}
