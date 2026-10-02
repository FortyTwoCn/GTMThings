package com.hepdd.gtmthings.common.block.machine.multiblock.part.appeng;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.integration.ae2.gui.AEKeyStorageSyncHandler;
import com.gregtechceu.gtceu.integration.ae2.gui.AEStackDisplayWidget;
import com.gregtechceu.gtceu.integration.ae2.gui.ScrollPreservingGrid;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.trait.GridNodeHolder;
import com.gregtechceu.gtceu.integration.ae2.utils.KeyStorage;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.DynamicLinkedSyncHandler;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widget.scroll.VerticalScrollData;
import brachy.modularui.widgets.DynamicSyncedWidget;
import brachy.modularui.widgets.layout.Flow;
import com.hepdd.gtmthings.api.machine.trait.InaccessibleInfiniteHandler;
import com.hepdd.gtmthings.api.machine.trait.InaccessibleInfiniteTank;

import java.util.EnumSet;

public class MEOutputPartMachine extends TieredIOPartMachine implements IGridConnectedMachine, IMuiMachine {

    @SyncToClient
    private boolean isOnline;
    @SaveField
    protected final GridNodeHolder nodeHolder;
    protected final IActionSource actionSource;
    @SaveField
    private final KeyStorage internalBuffer = new KeyStorage();
    @SaveField
    private final KeyStorage internalTankBuffer = new KeyStorage();
    private TickableSubscription autoIOSubs;

    public MEOutputPartMachine(BlockEntityCreationInfo info) {
        super(info, GTValues.LuV, IO.OUT);
        var items = attachTrait(new InaccessibleInfiniteHandler(internalBuffer));
        var fluids = attachTrait(new InaccessibleInfiniteTank(internalTankBuffer));
        items.addChangedListener(this::updateInventorySubscription);
        fluids.addChangedListener(this::updateInventorySubscription);
        nodeHolder = attachTrait(new GridNodeHolder(this));
        actionSource = IActionSource.ofMachine(getMainNode()::getNode);
    }

    @Override
    public IManagedGridNode getMainNode() {
        return nodeHolder.getMainNode();
    }

    @Override
    public boolean isOnline() {
        return isOnline;
    }

    @Override
    public void setOnline(boolean online) {
        if (online != isOnline) {
            isOnline = online;
            syncDataHolder.markClientSyncFieldDirty("isOnline");
        }
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State state) {
        IGridConnectedMachine.super.onMainNodeStateChanged(state);
        updateInventorySubscription();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        scheduleForNextServerTick(this::updateInventorySubscription);
    }

    @Override
    public void onUnload() {
        if (autoIOSubs != null) {
            autoIOSubs.unsubscribe();
            autoIOSubs = null;
        }
        super.onUnload();
    }

    private void updateInventorySubscription() {
        if (getLevel() == null || isRemote()) return;
        if (isWorkingEnabled() && isOnline && (!internalBuffer.isEmpty() || !internalTankBuffer.isEmpty()))
            autoIOSubs = subscribeServerTick(autoIOSubs, this::autoIO);
        else if (autoIOSubs != null) {
            autoIOSubs.unsubscribe();
            autoIOSubs = null;
        }
    }

    private void autoIO() {
        if (!shouldSyncME()) return;
        if (updateMEStatus()) flushBuffers();
        updateInventorySubscription();
    }

    private void flushBuffers() {
        var grid = getMainNode().getGrid();
        if (grid == null) return;
        internalBuffer.insertInventory(grid.getStorageService().getInventory(), actionSource);
        internalTankBuffer.insertInventory(grid.getStorageService().getInventory(), actionSource);
    }

    @Override
    public void onMachineDestroyed() {
        // Keep pending output on the dropped machine, including when the ME network is full or absent.
        super.onMachineDestroyed();
    }

    @Override
    public void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        var tag = new net.minecraft.nbt.CompoundTag();
        if (getLevel() != null) {
            tag.put("items", internalBuffer.serializeNBT(getLevel().registryAccess()));
            tag.put("fluids", internalTankBuffer.serializeNBT(getLevel().registryAccess()));
        }
        components.set(com.hepdd.gtmthings.data.GTMTDataComponents.ME_BUFFER, net.minecraft.world.item.component.CustomData.of(tag));
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        var saved = input.get(com.hepdd.gtmthings.data.GTMTDataComponents.ME_BUFFER);
        if (saved == null || getLevel() == null) return;
        var tag = saved.copyTag();
        internalBuffer.storage.clear();
        internalTankBuffer.storage.clear();
        internalBuffer.deserializeNBT(getLevel().registryAccess(), tag.getList("items", net.minecraft.nbt.Tag.TAG_COMPOUND));
        internalTankBuffer.deserializeNBT(getLevel().registryAccess(), tag.getList("fluids", net.minecraft.nbt.Tag.TAG_COMPOUND));
        markAsChanged();
        updateInventorySubscription();
    }

    @Override
    public void onRotated(Direction oldFacing, Direction newFacing) {
        super.onRotated(oldFacing, newFacing);
        getMainNode().setExposedOnSides(EnumSet.of(newFacing));
    }

    @Override
    public void setWorkingEnabled(boolean working) {
        super.setWorkingEnabled(working);
        updateInventorySubscription();
    }

    @Override
    public void buildMainUI(ParentWidget<?> main, PosGuiData data, PanelSyncManager manager, UISettings settings) {
        BooleanSyncValue online = new BooleanSyncValue(this::isOnline);
        manager.syncValue("online", online);
        var column = Flow.column().widthRel(1).coverChildrenHeight().padding(5).childPadding(5);
        column.child(Text.dynamic(() -> Component.translatable(online.getBoolValue() ? "gtceu.gui.me_network.online" : "gtceu.gui.me_network.offline")).asWidget());
        addBufferUI(column, manager, "items", internalBuffer);
        addBufferUI(column, manager, "fluids", internalTankBuffer);
        main.child(column);
    }

    private void addBufferUI(Flow column, PanelSyncManager manager, String name, KeyStorage buffer) {
        var storage = new AEKeyStorageSyncHandler(buffer);
        manager.syncValue(name, storage);
        int[] savedScroll = { 0 };
        var dynamic = new DynamicLinkedSyncHandler<>(storage).widgetProvider((sync, value) -> {
            var entries = value.getValue();
            return new ScrollPreservingGrid(savedScroll).size(160, 65).scrollable(new VerticalScrollData())
                    .gridOfSizeWidth(entries.size(), 1, (x, y, i) -> i < entries.size() ? Flow.row().coverChildrenHeight()
                            .child(new AEStackDisplayWidget(entries, i))
                            .child(Text.dynamic(() -> Component.literal(FormattingUtil.formatNumbers(entries.get(i).amount()) + " ")
                                    .append(entries.get(i).what().getDisplayName())).asWidget().width(140)) :
                            null);
        });
        column.child(new DynamicSyncedWidget<>().size(160, 65).syncHandler(dynamic));
    }
}
