package com.hepdd.gtmthings.common.block.machine.multiblock.part;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDistinctPart;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.machine.trait.ProgrammableCircuitSlotTrait;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.layout.Grid;
import brachy.modularui.widgets.slot.ModularSlot;
import brachy.modularui.widgets.slot.PhantomItemSlot;
import com.hepdd.gtmthings.api.gui.SelectionItemSlotSyncHandler;
import com.hepdd.gtmthings.api.misc.UnlimitedItemStackTransfer;
import lombok.Getter;

import java.util.List;
import java.util.function.IntFunction;

public class CreativeInputBusPartMachine extends TieredIOPartMachine implements IDistinctPart, IMuiMachine {

    private static final int ITEM_SIZE = 5;
    @Getter
    @SaveField
    private final NotifiableItemStackHandler inventory;
    @SaveField
    protected final ProgrammableCircuitSlotTrait circuitInventory;
    @SaveField
    private final CustomItemStackHandler creativeStorage;
    @SaveField
    @SyncToClient
    private boolean distinct;
    private TickableSubscription autoIOSubs;

    public CreativeInputBusPartMachine(BlockEntityCreationInfo info) {
        this(info, CustomItemStackHandler::new);
    }

    public CreativeInputBusPartMachine(BlockEntityCreationInfo info, IntFunction<CustomItemStackHandler> factory) {
        super(info, GTValues.MAX, IO.IN);
        inventory = attachTrait(new InfinityItemStackHandler(getInventorySize()));
        inventory.shouldDropInventoryInWorld(false);
        circuitInventory = attachTrait(new ProgrammableCircuitSlotTrait());
        creativeStorage = factory.apply(getInventorySize());
        creativeStorage.setOnContentsChanged(this::selectionChanged);
    }

    protected int getInventorySize() {
        return ITEM_SIZE * ITEM_SIZE;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        getHandlerList().setColor(getPaintingColor());
        getHandlerList().setDistinct(distinct);
        scheduleForNextServerTick(this::selectionChanged);
    }

    @Override
    public void onUnload() {
        if (autoIOSubs != null) {
            autoIOSubs.unsubscribe();
            autoIOSubs = null;
        }
        super.onUnload();
    }

    @Override
    public void onPaintingColorChanged(int color) {
        getHandlerList().setColor(color, true);
    }

    @Override
    public int tintColor(int index) {
        return index == 9 ? getRealColor() : -1;
    }

    @Override
    public boolean isDistinct() {
        return distinct;
    }

    @Override
    public boolean supportsDistinct() {
        return true;
    }

    @Override
    public void setDistinct(boolean value) {
        distinct = value;
        syncDataHolder.markClientSyncFieldDirty("distinct");
        getHandlerList().setDistinctAndNotify(value);
    }

    private boolean canSelect(int index, ItemStack item) {
        for (int i = 0; i < creativeStorage.getSlots(); i++) {
            if (i != index && !creativeStorage.getStackInSlot(i).isEmpty() && creativeStorage.getStackInSlot(i).is(item.getItem())) return false;
        }
        return true;
    }

    private void selectionChanged() {
        markAsChanged();
        if (getLevel() == null || isRemote()) return;
        refill();
        boolean selected = creativeStorage.toList().stream().anyMatch(stack -> !stack.isEmpty());
        if (selected) autoIOSubs = subscribeServerTick(autoIOSubs, () -> { if (getOffsetTimer() % 5 == 0) refill(); });
        else if (autoIOSubs != null) {
            autoIOSubs.unsubscribe();
            autoIOSubs = null;
        }
    }

    private void refill() {
        for (int i = 0; i < getInventorySize(); i++) {
            ItemStack selected = creativeStorage.getStackInSlot(i);
            ItemStack contents = selected.isEmpty() ? ItemStack.EMPTY : selected.copyWithCount(Integer.MAX_VALUE);
            if (!ItemStack.matches(contents, inventory.storage.getStackInSlot(i))) inventory.storage.setStackInSlot(i, contents);
        }
    }

    @Override
    public void buildMainUI(ParentWidget<?> main, PosGuiData data, PanelSyncManager manager, UISettings settings) {
        main.child(new Grid().coverChildren().center().margin(8)
                .gridOfSizeWidth(getInventorySize(), ITEM_SIZE, (x, y, i) -> new PhantomItemSlot()
                        .syncHandler(new SelectionItemSlotSyncHandler(new ModularSlot(creativeStorage, i)
                                .filter(stack -> canSelect(i, stack))))));
    }

    private static final class InfinityItemStackHandler extends NotifiableItemStackHandler {

        private InfinityItemStackHandler(int size) {
            super(size, IO.IN, IO.IN, UnlimitedItemStackTransfer::new);
        }

        @Override
        public List<SizedIngredient> handleRecipeInner(IO io, GTRecipe recipe, List<SizedIngredient> left, boolean simulate) {
            return super.handleRecipeInner(io, recipe, left, true);
        }
    }
}
