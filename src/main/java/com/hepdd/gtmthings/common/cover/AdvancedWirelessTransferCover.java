package com.hepdd.gtmthings.common.cover;

import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.IMuiCover;
import com.gregtechceu.gtceu.api.cover.filter.FilterHandler;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.common.mui.GTMuiWidgets;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import brachy.modularui.factory.SidedPosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widgets.layout.Flow;
import com.hepdd.gtmthings.api.misc.PersistentFilterHandler;

import java.util.List;

public class AdvancedWirelessTransferCover extends WirelessTransferCover implements IMuiCover {

    @SaveField
    protected final FilterHandler<FluidStack> filterHandlerFluid;
    @SaveField
    protected final FilterHandler<ItemStack> filterHandlerItem;

    public AdvancedWirelessTransferCover(CoverDefinition definition, ICoverable holder, Direction side, int type) {
        super(definition, holder, side, type);
        filterHandlerFluid = new PersistentFilterHandler<>(this, FluidStack.class);
        filterHandlerItem = new PersistentFilterHandler<>(this, ItemStack.class);
    }

    @Override
    protected boolean allowMultipleCovers() {
        return true;
    }

    @Override
    protected boolean testItem(ItemStack item) {
        return filterHandlerItem.test(item);
    }

    @Override
    protected boolean testFluid(FluidStack fluid) {
        return filterHandlerFluid.test(fluid);
    }

    @Override
    public List<ItemStack> getAdditionalDrops() {
        var drops = super.getAdditionalDrops();
        if (!filterHandlerItem.getFilterItem().isEmpty()) drops.add(filterHandlerItem.getFilterItem());
        if (!filterHandlerFluid.getFilterItem().isEmpty()) drops.add(filterHandlerFluid.getFilterItem());
        return drops;
    }

    @Override
    public void createCoverUIRows(Flow column, SidedPosGuiData data, PanelSyncManager manager, UISettings settings) {
        column.child(transferType == TRANSFER_ITEM ? GTMuiWidgets.createFilterRow(filterHandlerItem, data, manager, settings) : GTMuiWidgets.createFilterRow(filterHandlerFluid, data, manager, settings));
    }
}
