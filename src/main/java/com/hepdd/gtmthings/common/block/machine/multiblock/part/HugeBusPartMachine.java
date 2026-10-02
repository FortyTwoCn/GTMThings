package com.hepdd.gtmthings.common.block.machine.multiblock.part;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDistinctPart;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeHandlerList;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.common.machine.trait.ProgrammableCircuitSlotTrait;
import com.gregtechceu.gtceu.utils.GTTransferUtils;
import com.gregtechceu.gtceu.utils.ISubscription;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.InteractionSyncHandler;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.layout.Grid;
import brachy.modularui.widgets.slot.ItemSlot;
import brachy.modularui.widgets.slot.ModularSlot;
import brachy.modularui.widgets.slot.SlotGroup;
import com.hepdd.gtmthings.api.gui.ComponentListUI;
import com.hepdd.gtmthings.api.misc.UnlimitedItemStackTransfer;
import com.hepdd.gtmthings.api.transfer.UnlimitItemTransferHelper;
import com.hepdd.gtmthings.common.block.machine.trait.CatalystItemStackHandler;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

import static com.hepdd.gtmthings.utils.FormatUtil.formatNumber;

public class HugeBusPartMachine extends TieredIOPartMachine implements IDistinctPart, IMuiMachine {

    public static final int INV_MULTIPLE = 2;
    @Getter
    @SaveField
    protected final NotifiableItemStackHandler inventory;
    @Getter
    @SaveField
    protected final ProgrammableCircuitSlotTrait circuitInventory;
    @Getter
    @SaveField
    protected final CatalystItemStackHandler shareInventory;
    protected TickableSubscription autoIOSubs;
    protected ISubscription inventorySubs;
    private RecipeHandlerList mainHandlers, sharedHandlers;

    public HugeBusPartMachine(BlockEntityCreationInfo info, int tier, IO io, Object... args) {
        this(info, tier, io, 4, args);
    }

    public HugeBusPartMachine(BlockEntityCreationInfo info, int tier, IO io, int sharedSize, Object... args) {
        super(info, tier, io);
        inventory = attachTrait(createInventory());
        circuitInventory = attachTrait(new ProgrammableCircuitSlotTrait());
        circuitInventory.setEnabled(io == IO.IN);
        shareInventory = attachTrait(new CatalystItemStackHandler(sharedSize, IO.IN, IO.NONE));
    }

    public IO getIo() {
        return io;
    }

    protected int getInventorySize() {
        return getTier() < GTValues.EV ? 1 + getTier() : (1 + getTier()) * INV_MULTIPLE;
    }

    protected NotifiableItemStackHandler createInventory() {
        return new NotifiableItemStackHandler(getInventorySize(), io, io, UnlimitedItemStackTransfer::new) {

            @Override
            public boolean canCapOutput() {
                return true;
            }
        };
    }

    protected List<IRecipeHandler<?>> mainRecipeHandlers() {
        return new ArrayList<>(List.of(inventory, circuitInventory));
    }

    protected List<IRecipeHandler<?>> sharedRecipeHandlers() {
        return new ArrayList<>(List.of(shareInventory));
    }

    @Override
    protected RecipeHandlerList getHandlerList() {
        if (mainHandlers == null) mainHandlers = RecipeHandlerList.of(io, getPaintingColor(), mainRecipeHandlers());
        return mainHandlers;
    }

    private RecipeHandlerList sharedHandlerList() {
        if (sharedHandlers == null) sharedHandlers = RecipeHandlerList.of(IO.IN, getPaintingColor(), sharedRecipeHandlers());
        return sharedHandlers;
    }

    @Override
    public List<RecipeHandlerList> getRecipeHandlers() {
        return io == IO.IN ? List.of(getHandlerList(), sharedHandlerList()) : List.of(getHandlerList());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        getHandlerList().setDistinct(inventory.isDistinct());
        getHandlerList().setColor(getPaintingColor());
        sharedHandlerList().setColor(getPaintingColor());
        inventorySubs = inventory.addChangedListener(this::updateInventorySubscription);
        scheduleForNextServerTick(this::updateInventorySubscription);
    }

    @Override
    public void onUnload() {
        if (autoIOSubs != null) {
            autoIOSubs.unsubscribe();
            autoIOSubs = null;
        }
        if (inventorySubs != null) {
            inventorySubs.unsubscribe();
            inventorySubs = null;
        }
        super.onUnload();
    }

    @Override
    public void onPaintingColorChanged(int color) {
        getHandlerList().setColor(color, true);
        sharedHandlerList().setColor(color, true);
    }

    @Override
    public int tintColor(int index) {
        return index == 9 ? getRealColor() : -1;
    }

    @Override
    public boolean isDistinct() {
        return inventory.isDistinct();
    }

    @Override
    public boolean supportsDistinct() {
        return io == IO.IN;
    }

    @Override
    public void setDistinct(boolean distinct) {
        getHandlerList().setDistinctAndNotify(io == IO.IN && distinct);
    }

    protected void refundAll() {
        if (getLevel() != null && GTTransferUtils.hasAdjacentItemHandler(getLevel(), getBlockPos(), getFrontFacing())) {
            setWorkingEnabled(false);
            exportToNearby(inventory, getFrontFacing());
        }
    }

    @Override
    public void onNeighborChanged(Block block, BlockPos from, boolean moving) {
        super.onNeighborChanged(block, from, moving);
        updateInventorySubscription();
    }

    @Override
    public void onRotated(Direction oldFacing, Direction newFacing) {
        super.onRotated(oldFacing, newFacing);
        updateInventorySubscription();
    }

    protected void updateInventorySubscription() {
        if (getLevel() == null || isRemote()) return;
        if (isWorkingEnabled() && (io == IO.IN || !inventory.isEmpty()) && GTTransferUtils.hasAdjacentItemHandler(getLevel(), getBlockPos(), getFrontFacing())) {
            autoIOSubs = subscribeServerTick(autoIOSubs, this::autoIO);
        } else if (autoIOSubs != null) {
            autoIOSubs.unsubscribe();
            autoIOSubs = null;
        }
    }

    protected void autoIO() {
        if (getOffsetTimer() % 5 != 0) return;
        if (isWorkingEnabled()) {
            if (io == IO.OUT) exportToNearby(inventory, getFrontFacing());
            else if (io == IO.IN) inventory.importFromNearby(getFrontFacing());
        }
        updateInventorySubscription();
    }

    public void exportToNearby(NotifiableItemStackHandler source, Direction... facings) {
        if (source.isEmpty() || getLevel() == null) return;
        for (Direction facing : facings) UnlimitItemTransferHelper.exportToTarget(source, Integer.MAX_VALUE, item -> true,
                getLevel(), getBlockPos().relative(facing), facing.getOpposite());
    }

    @Override
    public void setWorkingEnabled(boolean working) {
        super.setWorkingEnabled(working);
        updateInventorySubscription();
    }

    protected List<Component> displayText() {
        List<Component> lines = new ArrayList<>();
        int count = 0;
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                lines.add(stack.getHoverName().copy().withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(" " + formatNumber(stack.getCount())).withStyle(ChatFormatting.AQUA)));
                count++;
            }
        }
        if (lines.isEmpty()) lines.add(Component.translatable("gtmthings.machine.huge_item_bus.tooltip.3"));
        lines.addFirst(Component.translatable("gtmthings.machine.huge_item_bus.tooltip.2", count, inventory.getSlots())
                .withStyle(ChatFormatting.GREEN));
        return lines;
    }

    protected void addSharedInventoryUI(Flow column, PanelSyncManager manager) {
        column.child(Text.lang("gui.gtmthings.share_inventory.title").asWidget()
                .tooltip(r -> r.addLine(Component.translatable("gui.gtmthings.share_inventory.desc.0"))
                        .addLine(Component.translatable("gui.gtmthings.share_inventory.desc.1"))
                        .addLine(Component.translatable("gui.gtmthings.share_inventory.desc.2"))));
        SlotGroup group = new SlotGroup("shared_items", shareInventory.getSlots());
        column.child(new Grid().coverChildren().gridOfSizeWidth(shareInventory.getSlots(), 3,
                (x, y, i) -> i < shareInventory.getSlots() ? new ItemSlot().slot(new ModularSlot(shareInventory.storage, i).slotGroup(group)) : null));
    }

    protected int contentsDisplayHeight() {
        return 95;
    }

    @Override
    public void buildMainUI(ParentWidget<?> main, PosGuiData data, PanelSyncManager manager, UISettings settings) {
        Flow column = Flow.column().widthRel(1).coverChildrenHeight().padding(5).childPadding(4);
        column.child(ComponentListUI.create("contents", manager, this::displayText, contentsDisplayHeight()));
        if (io == IO.IN) {
            column.child(new ButtonWidget<>().widthRel(1).height(18).overlay(Text.lang("gui.gtmthings.refund"))
                    .tooltip(r -> r.addLine(Component.translatable("gtmthings.machine.huge_item_bus.tooltip.1")))
                    .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouse -> { if (!mouse.isClient()) refundAll(); })));
            addSharedInventoryUI(column, manager);
        }
        main.child(column);
    }
}
