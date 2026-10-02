package com.hepdd.gtmthings.common.item;

import com.gregtechceu.gtceu.api.item.component.IAddInformation;
import com.gregtechceu.gtceu.api.item.component.IComponentCapability;
import com.gregtechceu.gtceu.api.item.component.IItemComponent;
import com.gregtechceu.gtceu.api.mui.IItemUIHolder;
import com.gregtechceu.gtceu.common.data.item.GTDataComponents;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.IFluidTank;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PlayerInventoryGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.FluidSlotSyncHandler;
import brachy.modularui.value.sync.IntSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widgets.ToggleButton;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.slot.FluidSlot;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import com.hepdd.gtmthings.api.misc.CreativeFluidHandlerItemStack;
import com.hepdd.gtmthings.data.GTMTDataComponents;

import java.util.List;

public class CreativeFluidStats implements IItemComponent, IComponentCapability, IAddInformation, IItemUIHolder {

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("gtmthings.creative_tooltip"));
        FluidStack stored = getStored(stack);
        if (stored.isEmpty()) lines.add(Component.translatable("item.gtmthings.creative_fluid_cell.tooltip2"));
        else {
            lines.add(Component.translatable("item.gtmthings.creative_fluid_cell.tooltip1", stored.getHoverName()));
            if (getAccurate(stack)) lines.add(Component.translatable("item.gtmthings.creative_fluid_cell.tooltip3", getCapacity(stack)));
        }
    }

    @Override
    public void attachCapabilities(RegisterCapabilitiesEvent event, Item item) {
        event.registerItem(Capabilities.FluidHandler.ITEM, (stack, context) -> getStored(stack).isEmpty() ? null : new CreativeFluidHandlerItemStack(stack), item);
    }

    public static boolean getAccurate(ItemStack stack) {
        return stack.getOrDefault(GTMTDataComponents.ACCURATE, false);
    }

    public static int getCapacity(ItemStack stack) {
        return stack.getOrDefault(GTMTDataComponents.CAPACITY, 1000);
    }

    public static FluidStack getStored(ItemStack stack) {
        return stack.getOrDefault(GTDataComponents.FLUID_CONTENT, SimpleFluidContent.EMPTY).copy();
    }

    public static void setStored(ItemStack stack, FluidStack fluid) {
        if (fluid.isEmpty()) stack.remove(GTDataComponents.FLUID_CONTENT);
        else stack.set(GTDataComponents.FLUID_CONTENT, SimpleFluidContent.copyOf(fluid.copyWithAmount(1000)));
    }

    @Override
    public ModularPanel<?> buildUI(PlayerInventoryGuiData<?> data, PanelSyncManager syncManager, UISettings settings) {
        var accurate = new BooleanSyncValue(() -> getAccurate(data.getUsedItemStack()),
                value -> data.getUsedItemStack().set(GTMTDataComponents.ACCURATE, value)).allowC2S();
        var capacity = new IntSyncValue(() -> getCapacity(data.getUsedItemStack()),
                value -> data.getUsedItemStack().set(GTMTDataComponents.CAPACITY, Math.max(1, value))).allowC2S();
        syncManager.syncValue("accurate", accurate);
        syncManager.syncValue("capacity", capacity);
        syncManager.syncValue("selected_fluid", new FluidSlotSyncHandler(new SelectionTank(data)).phantom(true).controlsAmount(false));
        return ModularPanel.defaultPanel("creative_fluid_cell").bindPlayerInventory()
                .child(Flow.column().widthRel(1).coverChildrenHeight().top(6).padding(7).childPadding(4)
                        .child(Flow.row().coverChildrenHeight().childPadding(8)
                                .child(Text.lang("gtceu.creative.tank.fluid").asWidget())
                                .child(new FluidSlot().syncHandler("selected_fluid").background(GTGuiTextures.FLUID_SLOT)))
                        .child(Flow.row().coverChildrenHeight().childPadding(4)
                                .child(new ToggleButton().value(accurate).size(64, 18)
                                        .overlay(Text.lang("item.gtmthings.creative_fluid_cell.gui.button1")))
                                .child(new TextFieldWidget().setNumbers(1, Integer.MAX_VALUE).value(capacity).size(90, 18))));
    }

    private record SelectionTank(PlayerInventoryGuiData<?> data) implements IFluidTank {

        @Override
        public FluidStack getFluid() {
            return getStored(data.getUsedItemStack());
        }

        @Override
        public int getFluidAmount() {
            return getFluid().getAmount();
        }

        @Override
        public int getCapacity() {
            return 1000;
        }

        @Override
        public boolean isFluidValid(FluidStack stack) {
            return true;
        }

        @Override
        public int fill(FluidStack resource, IFluidHandler.FluidAction action) {
            if (resource.isEmpty()) return 0;
            if (action.execute()) setStored(data.getUsedItemStack(), resource);
            return resource.getAmount();
        }

        @Override
        public FluidStack drain(int amount, IFluidHandler.FluidAction action) {
            FluidStack fluid = getFluid();
            if (fluid.isEmpty() || amount <= 0) return FluidStack.EMPTY;
            if (action.execute()) setStored(data.getUsedItemStack(), FluidStack.EMPTY);
            return fluid.copyWithAmount(Math.min(amount, fluid.getAmount()));
        }

        @Override
        public FluidStack drain(FluidStack fluid, IFluidHandler.FluidAction action) {
            return FluidStack.isSameFluidSameComponents(fluid, getFluid()) ? drain(fluid.getAmount(), action) : FluidStack.EMPTY;
        }
    }
}
