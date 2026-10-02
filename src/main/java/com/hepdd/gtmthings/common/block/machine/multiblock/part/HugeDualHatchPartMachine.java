package com.hepdd.gtmthings.common.block.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.utils.GTTransferUtils;
import com.gregtechceu.gtceu.utils.ISubscription;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.value.sync.FluidSlotSyncHandler;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.layout.Grid;
import brachy.modularui.widgets.slot.FluidSlot;
import com.hepdd.gtmthings.common.block.machine.trait.CatalystFluidStackHandler;
import com.hepdd.gtmthings.utils.FormatUtil;
import lombok.Getter;

import java.util.List;

public class HugeDualHatchPartMachine extends HugeBusPartMachine {

    @Getter
    @SaveField
    protected final NotifiableFluidTank tank;
    @Getter
    @SaveField
    protected final CatalystFluidStackHandler shareTank;
    private ISubscription tankSubs;
    private boolean hasFluidTransfer, hasItemTransfer;

    public HugeDualHatchPartMachine(BlockEntityCreationInfo info, int tier, IO io, Object... args) {
        super(info, tier, io, 9, args);
        tank = attachTrait(new NotifiableFluidTank(getTankInventorySize(), Integer.MAX_VALUE, io) {

            @Override
            public boolean canCapOutput() {
                return true;
            }
        });
        shareTank = attachTrait(new CatalystFluidStackHandler(9, 16000, IO.IN, IO.NONE));
    }

    protected int getTankInventorySize() {
        return getTier() + 1;
    }

    @Override
    protected List<IRecipeHandler<?>> mainRecipeHandlers() {
        var handlers = super.mainRecipeHandlers();
        handlers.add(tank);
        return handlers;
    }

    @Override
    protected List<IRecipeHandler<?>> sharedRecipeHandlers() {
        var handlers = super.sharedRecipeHandlers();
        handlers.add(shareTank);
        return handlers;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        tankSubs = tank.addChangedListener(this::updateInventorySubscription);
    }

    @Override
    public void onUnload() {
        if (tankSubs != null) {
            tankSubs.unsubscribe();
            tankSubs = null;
        }
        super.onUnload();
    }

    @Override
    protected void refundAll() {
        super.refundAll();
        if (getLevel() != null && GTTransferUtils.hasAdjacentFluidHandler(getLevel(), getBlockPos(), getFrontFacing())) {
            setWorkingEnabled(false);
            tank.exportToNearby(getFrontFacing());
        }
    }

    @Override
    protected void updateInventorySubscription() {
        if (getLevel() == null || isRemote() || tank == null) return;
        hasItemTransfer = GTTransferUtils.hasAdjacentItemHandler(getLevel(), getBlockPos(), getFrontFacing());
        hasFluidTransfer = GTTransferUtils.hasAdjacentFluidHandler(getLevel(), getBlockPos(), getFrontFacing());
        if (isWorkingEnabled() && (io == IO.IN || !tank.isEmpty() || !inventory.isEmpty()) && (hasItemTransfer || hasFluidTransfer)) autoIOSubs = subscribeServerTick(autoIOSubs, this::autoIO);
        else if (autoIOSubs != null) {
            autoIOSubs.unsubscribe();
            autoIOSubs = null;
        }
    }

    @Override
    protected void autoIO() {
        if (getOffsetTimer() % 5 != 0) return;
        if (isWorkingEnabled()) {
            if (io == IO.OUT) {
                if (hasItemTransfer) exportToNearby(inventory, getFrontFacing());
                if (hasFluidTransfer) tank.exportToNearby(getFrontFacing());
            } else if (io == IO.IN) {
                if (hasItemTransfer) inventory.importFromNearby(getFrontFacing());
                if (hasFluidTransfer) tank.importFromNearby(getFrontFacing());
            }
        }
        updateInventorySubscription();
    }

    @Override
    protected List<Component> displayText() {
        List<Component> lines = super.displayText();
        int count = 0;
        for (int i = 0; i < tank.getTanks(); i++) {
            FluidStack fluid = tank.getFluidInTank(i);
            if (!fluid.isEmpty()) {
                lines.add(fluid.getHoverName().copy().withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(" " + (fluid.getAmount() < 1000 ? fluid.getAmount() + "mB" : FormatUtil.formatNumber(fluid.getAmount() / 1000L) + "B")).withStyle(ChatFormatting.AQUA)));
                count++;
            }
        }
        lines.add(1, Component.translatable("gtmthings.machine.huge_dual_hatch.tooltip.2", count, tank.getTanks())
                .withStyle(ChatFormatting.GREEN));
        return lines;
    }

    @Override
    protected int contentsDisplayHeight() {
        return 65;
    }

    @Override
    protected void addSharedInventoryUI(Flow column, PanelSyncManager manager) {
        super.addSharedInventoryUI(column, manager);
        column.child(Text.lang("gui.gtceu.share_tank.title").asWidget()
                .tooltip(r -> r.addLine(Component.translatable("gui.gtceu.share_tank.desc.0"))
                        .addLine(Component.translatable("gui.gtceu.share_inventory.desc.1"))));
        column.child(new Grid().coverChildren().gridOfSizeWidth(shareTank.getTanks(), 3,
                (x, y, i) -> new FluidSlot().syncHandler(new FluidSlotSyncHandler(shareTank.getStorages()[i]))));
    }
}
