package com.hepdd.gtmthings.common.block.machine.multiblock.part;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDistinctPart;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.FluidSlotSyncHandler;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.layout.Grid;
import brachy.modularui.widgets.slot.FluidSlot;

import java.util.List;

public class CreativeInputHatchPartMachine extends TieredIOPartMachine implements IDistinctPart, IMuiMachine {

    private static final int SLOT_COUNT = 9;
    @SaveField
    public final NotifiableFluidTank tank;
    @SaveField
    private final CustomFluidTank[] creativeTanks;
    @SaveField
    @SyncToClient
    private boolean distinct;
    private TickableSubscription autoIOSubs;

    public CreativeInputHatchPartMachine(BlockEntityCreationInfo info) {
        super(info, GTValues.MAX, IO.IN);
        tank = attachTrait(new InfinityFluidTank());
        creativeTanks = new CustomFluidTank[SLOT_COUNT];
        for (int i = 0; i < SLOT_COUNT; i++) {
            int index = i;
            creativeTanks[i] = new CustomFluidTank(1, fluid -> canSelect(index, fluid));
            creativeTanks[i].setOnContentsChanged(this::selectionChanged);
        }
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

    private boolean canSelect(int index, FluidStack fluid) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (i != index && creativeTanks[i] != null && !creativeTanks[i].isEmpty() && creativeTanks[i].getFluid().is(fluid.getFluid())) return false;
        }
        return true;
    }

    private void selectionChanged() {
        markAsChanged();
        if (getLevel() == null || isRemote()) return;
        refill();
        boolean selected = false;
        for (var selection : creativeTanks) selected |= !selection.isEmpty();
        if (selected) autoIOSubs = subscribeServerTick(autoIOSubs, () -> { if (getOffsetTimer() % 5 == 0) refill(); });
        else if (autoIOSubs != null) {
            autoIOSubs.unsubscribe();
            autoIOSubs = null;
        }
    }

    private void refill() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            FluidStack selected = creativeTanks[i].getFluid();
            FluidStack contents = selected.isEmpty() ? FluidStack.EMPTY : selected.copyWithAmount(Integer.MAX_VALUE);
            if (!FluidStack.matches(contents, tank.getFluidInTank(i))) tank.setFluidInTank(i, contents);
        }
    }

    @Override
    public void buildMainUI(ParentWidget<?> main, PosGuiData data, PanelSyncManager manager, UISettings settings) {
        main.child(new Grid().coverChildren().center().margin(8)
                .gridOfSizeWidth(SLOT_COUNT, 3, (x, y, i) -> new FluidSlot()
                        .syncHandler(new FluidSlotSyncHandler(creativeTanks[i]).phantom(true).controlsAmount(false))));
    }

    private static final class InfinityFluidTank extends NotifiableFluidTank {

        private InfinityFluidTank() {
            super(SLOT_COUNT, Integer.MAX_VALUE, IO.IN);
        }

        @Override
        public List<SizedFluidIngredient> handleRecipeInner(IO io, GTRecipe recipe, List<SizedFluidIngredient> left, boolean simulate) {
            return super.handleRecipeInner(io, recipe, left, true);
        }
    }
}
