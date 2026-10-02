package com.hepdd.gtmthings.common.block.machine.electric;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.cover.filter.Filter;
import com.gregtechceu.gtceu.api.cover.filter.FilterHandler;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.WorkableTieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.IDataInfoProvider;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.common.data.machines.GTMachineUtils;
import com.gregtechceu.gtceu.common.item.behavior.PortableScannerBehavior;
import com.gregtechceu.gtceu.common.mui.GTMuiWidgets;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;
import com.gregtechceu.gtceu.utils.GTTransferUtils;
import com.gregtechceu.gtceu.utils.ISubscription;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.factory.SidedPosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.IntSyncValue;
import brachy.modularui.value.sync.InteractionSyncHandler;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.ToggleButton;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.layout.Grid;
import brachy.modularui.widgets.slot.ItemSlot;
import brachy.modularui.widgets.slot.ModularSlot;
import brachy.modularui.widgets.slot.SlotGroup;
import com.hepdd.gtmthings.api.capability.IDigitalMiner;
import com.hepdd.gtmthings.api.gui.ComponentListUI;
import com.hepdd.gtmthings.common.block.machine.trait.miner.DigitalMinerLogic;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class DigitalMiner extends WorkableTieredMachine implements IDigitalMiner, IDataInfoProvider, com.gregtechceu.gtceu.api.machine.feature.IMuiMachine {

    @SaveField
    @Getter
    private final FilterHandler<ItemStack> filterInventory;
    @Getter
    @SaveField
    private int minerRadius, minHeight = 0, maxHeight = 64;
    @Getter
    @SaveField
    private int silkLevel;
    private TickableSubscription autoOutputSubs;
    private ISubscription exportItemSubs;
    private boolean initialized;

    public DigitalMiner(BlockEntityCreationInfo info, int tier, Object... args) {
        super(info, tier, false, new DigitalMinerLogic(tier), GTMachineUtils.defaultTankSizeFunction);
        minerRadius = Math.min(32, 8 << tier);
        filterInventory = new com.hepdd.gtmthings.api.misc.PersistentFilterHandler<>(this, ItemStack.class).onFilterLoaded(filter -> filterChanged())
                .onFilterUpdated(filter -> filterChanged()).onFilterRemoved(this::filterChanged);
    }

    @Override
    public DigitalMinerLogic getRecipeLogic() {
        return (DigitalMinerLogic) super.getRecipeLogic();
    }

    public Filter<ItemStack> getItemFilter() {
        return filterInventory.isFilterPresent() ? filterInventory.getFilter() : null;
    }

    public void setMinerRadius(int radius) {
        minerRadius = Math.clamp(radius, 1, 8 << getTier());
        markAsChanged();
    }

    public void setMinHeight(int height) {
        minHeight = Math.clamp(height, getLevel().getMinBuildHeight(), getLevel().getMaxBuildHeight() - 1);
        markAsChanged();
    }

    public void setMaxHeight(int height) {
        maxHeight = Math.clamp(height, getLevel().getMinBuildHeight(), getLevel().getMaxBuildHeight() - 1);
        markAsChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // Rehydrating the saved filter must not reset the persisted mining queue.
        var filter = getItemFilter();
        initialized = true;
        getRecipeLogic().configure(minerRadius, minHeight, maxHeight, silkLevel, filter);
        exportItemSubs = exportItems.addChangedListener(this::updateAutoOutputSubscription);
        scheduleForNextServerTick(this::updateAutoOutputSubscription);
    }

    @Override
    public void onUnload() {
        initialized = false;
        if (autoOutputSubs != null) {
            autoOutputSubs.unsubscribe();
            autoOutputSubs = null;
        }
        if (exportItemSubs != null) {
            exportItemSubs.unsubscribe();
            exportItemSubs = null;
        }
        super.onUnload();
    }

    @Override
    public void onMachineDestroyed() {
        if (filterInventory.isFilterPresent()) Block.popResource(getLevel(), getBlockPos(), filterInventory.getFilterItem());
        super.onMachineDestroyed();
    }

    @Override
    public void onNeighborChanged(Block block, BlockPos from, boolean moving) {
        super.onNeighborChanged(block, from, moving);
        updateAutoOutputSubscription();
    }

    private void updateAutoOutputSubscription() {
        if (getLevel() == null || isRemote()) return;
        if (!exportItems.isEmpty() && GTTransferUtils.hasAdjacentItemHandler(getLevel(), getBlockPos(), getFrontFacing()))
            autoOutputSubs = subscribeServerTick(autoOutputSubs, () -> {
                if (getOffsetTimer() % 5 == 0) exportItems.exportToNearby(getFrontFacing());
                updateAutoOutputSubscription();
            });
        else if (autoOutputSubs != null) {
            autoOutputSubs.unsubscribe();
            autoOutputSubs = null;
        }
    }

    @Override
    public boolean drainInput(boolean simulate) {
        long required = GTValues.VEX[getTier() - 1] * (silkLevel == 1 ? 4 : 1);
        if (energyContainer.getEnergyStored() < required) return false;
        if (!simulate) energyContainer.removeEnergy(required);
        return true;
    }

    private void resetRecipe() {
        if (isRemote()) return;
        getRecipeLogic().resetRecipeLogic(minerRadius, minHeight, maxHeight, silkLevel, getItemFilter());
        markAsChanged();
    }

    private void filterChanged() {
        if (initialized) resetRecipe();
    }

    public void setSilk(boolean enabled) {
        silkLevel = enabled ? 1 : 0;
        resetRecipe();
    }

    private List<Component> displayText() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.gtmthings.digital_miner.queued", getRecipeLogic().getOreAmount()));
        if (getRecipeLogic().isDone()) lines.add(Component.translatable("gtceu.multiblock.large_miner.done").withStyle(ChatFormatting.GREEN));
        else if (getRecipeLogic().isWorking()) lines.add(Component.translatable("gtceu.multiblock.large_miner.working").withStyle(ChatFormatting.GOLD));
        else if (!isWorkingEnabled()) lines.add(Component.translatable("gtceu.multiblock.work_paused"));
        if (getRecipeLogic().isInventoryFull()) lines.add(Component.translatable("gtceu.multiblock.large_miner.invfull").withStyle(ChatFormatting.RED));
        if (!drainInput(true)) lines.add(Component.translatable("gtceu.multiblock.large_miner.needspower").withStyle(ChatFormatting.RED));
        if (getRecipeLogic().getBestFailureReason() != null && drainInput(true) && !getRecipeLogic().isInventoryFull()) lines.add(getRecipeLogic().getBestFailureReason());
        return lines;
    }

    @Override
    public void buildMainUI(ParentWidget<?> main, PosGuiData data, PanelSyncManager manager, UISettings settings) {
        var radius = new IntSyncValue(this::getMinerRadius, this::setMinerRadius).allowC2S();
        var min = new IntSyncValue(this::getMinHeight, this::setMinHeight).allowC2S();
        var max = new IntSyncValue(this::getMaxHeight, this::setMaxHeight).allowC2S();
        var silk = new BooleanSyncValue(() -> silkLevel == 1, this::setSilk).allowC2S();
        manager.syncValue("radius", radius);
        manager.syncValue("minHeight", min);
        manager.syncValue("maxHeight", max);
        manager.syncValue("silk", silk);
        Flow column = Flow.column().width(170).coverChildrenHeight().padding(4).childPadding(4);
        column.child(ComponentListUI.create("status", manager, this::displayText, 55));
        var sided = new SidedPosGuiData(data.getPlayer(), getBlockPos(), getFrontFacing());
        column.child(GTMuiWidgets.createFilterRow(filterInventory, sided, manager, settings));
        column.child(Flow.row().height(18).child(Text.lang("gui.gtmthings.digital_miner.radius").asWidget().width(70))
                .child(GTMuiWidgets.createIntInputWithButtons(radius, () -> 1, () -> 8 << getTier()).width(90)));
        column.child(Flow.row().height(18).child(Text.lang("gui.gtmthings.digital_miner.min_height").asWidget().width(70))
                .child(GTMuiWidgets.createIntInputWithButtons(min, () -> getLevel().getMinBuildHeight(), () -> getLevel().getMaxBuildHeight() - 1).width(90)));
        column.child(Flow.row().height(18).child(Text.lang("gui.gtmthings.digital_miner.max_height").asWidget().width(70))
                .child(GTMuiWidgets.createIntInputWithButtons(max, () -> getLevel().getMinBuildHeight(), () -> getLevel().getMaxBuildHeight() - 1).width(90)));
        column.child(Flow.row().coverChildrenHeight().childPadding(5)
                .child(new ButtonWidget<>().size(75, 18).overlay(Text.lang("gui.gtmthings.digital_miner.reset"))
                        .tooltip(r -> r.addLine(Component.translatable("gui.gtmthings.digital_miner.reset.desc")))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouse -> { if (!mouse.isClient()) resetRecipe(); })))
                .child(new ToggleButton().value(silk).size(75, 18).overlay(Text.lang("gui.gtmthings.digital_miner.silk"))
                        .tooltip(r -> r.addLine(Component.translatable("gui.gtmthings.digital_miner.silk.desc")))));
        SlotGroup slots = new SlotGroup("output", exportItems.getSlots());
        column.child(new Grid().coverChildren().gridOfSizeWidth(exportItems.getSlots(), 9,
                (x, y, i) -> new ItemSlot().slot(new ModularSlot(exportItems.storage, i).slotGroup(slots).accessibility(false, true))));
        main.child(column);
    }

    @Override
    protected InteractionResult onScrewdriverClick(ExtendedUseOnContext context) {
        if (isRemote()) return InteractionResult.SUCCESS;
        var player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (isActive()) player.sendSystemMessage(Component.translatable("gtceu.multiblock.large_miner.errorradius"));
        else {
            int radius = getRecipeLogic().getCurrentRadius();
            getRecipeLogic().setCurrentRadius(radius == 1 ? getRecipeLogic().getMaximumRadius() : player.isShiftKeyDown() ? Math.max(1, Math.round(radius / 2f)) : Math.max(1, radius - 1));
            getRecipeLogic().resetArea(true);
            int area = IDigitalMiner.getWorkingArea(getRecipeLogic().getCurrentRadius());
            player.sendSystemMessage(Component.translatable("gtceu.universal.tooltip.working_area", area, area));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public List<Component> getDataInfo(PortableScannerBehavior.DisplayMode mode) {
        if (mode == PortableScannerBehavior.DisplayMode.SHOW_ALL || mode == PortableScannerBehavior.DisplayMode.SHOW_MACHINE_INFO) {
            int area = IDigitalMiner.getWorkingArea(getRecipeLogic().getCurrentRadius());
            return List.of(Component.translatable("gtceu.universal.tooltip.working_area", area, area));
        }
        return List.of();
    }
}
