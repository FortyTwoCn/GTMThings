package com.hepdd.gtmthings.common.block.machine.multiblock.part;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IDataInfoProvider;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.common.item.behavior.PortableScannerBehavior;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CreativeEnergyHatchPartMachine extends TieredIOPartMachine implements IDataInfoProvider, com.gregtechceu.gtceu.api.machine.feature.IMuiMachine {

    @SaveField
    public final NotifiableEnergyContainer energyContainer;
    @SaveField
    private long maxEnergy;
    @SaveField
    private long voltage = 0;
    @SaveField
    @Getter
    private int amps = 1;
    @SaveField
    private int setTier = 0;

    public CreativeEnergyHatchPartMachine(BlockEntityCreationInfo holder) {
        super(holder, GTValues.MAX, IO.IN);
        this.energyContainer = attachTrait(createEnergyContainer());
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    protected NotifiableEnergyContainer createEnergyContainer() {
        NotifiableEnergyContainer container;
        this.voltage = GTValues.VEX[setTier];
        this.maxEnergy = com.hepdd.gtmthings.api.gui.EnergyConfigurationUI.capacity(this.voltage, this.amps, 1);
        container = new InfinityEnergyContainer(this, this.maxEnergy, this.voltage, this.amps, 0L, 0L);
        container.setEnergyStored(this.maxEnergy);
        return container;
    }

    @Override
    public void loadAdditional(@NotNull CompoundTag tag, net.minecraft.core.HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        updateEnergyContainer();
    }

    @Override
    public void buildMainUI(brachy.modularui.widget.ParentWidget<?> parent, brachy.modularui.factory.PosGuiData data,
                            brachy.modularui.value.sync.PanelSyncManager syncManager, brachy.modularui.screen.UISettings settings) {
        com.hepdd.gtmthings.api.gui.EnergyConfigurationUI.build(parent, syncManager, () -> voltage, this::setVoltage,
                () -> amps, this::setAmps, GTValues.ULV, 1);
    }

    private void setVoltage(long voltage) {
        this.voltage = Math.max(8, voltage);
        this.setTier = GTUtil.getTierByVoltage(this.voltage);
        this.maxEnergy = com.hepdd.gtmthings.api.gui.EnergyConfigurationUI.capacity(this.voltage, this.amps, 1);
        updateMachine();
    }

    private void setAmps(int amps) {
        this.amps = Math.max(1, Math.min(67108864, amps));
        this.maxEnergy = com.hepdd.gtmthings.api.gui.EnergyConfigurationUI.capacity(this.voltage, this.amps, 1);
        updateMachine();
    }

    private void updateEnergyContainer() {
        this.energyContainer.resetBasicInfo(this.maxEnergy, this.voltage, this.amps, 0, 0);
        this.energyContainer.setEnergyStored(this.maxEnergy);
    }

    private void updateMachine() {
        updateEnergyContainer();
        markAsChanged();
        if (!isRemote()) for (var controller : getControllers()) controller.invalidateAllStructures();
    }

    //////////////////////////////////////
    // ********** Misc **********//
    //////////////////////////////////////

    @Override
    public int tintColor(int index) {
        if (index == 2) {
            return GTValues.VC[getTier()];
        }
        return super.tintColor(index);
    }

    @NotNull
    @Override
    public List<Component> getDataInfo(PortableScannerBehavior.DisplayMode mode) {
        if (mode == PortableScannerBehavior.DisplayMode.SHOW_ALL || mode == PortableScannerBehavior.DisplayMode.SHOW_ELECTRICAL_INFO) {
            return Collections.singletonList(Component.literal(
                    String.format("%d/%d EU", energyContainer.getEnergyStored(), energyContainer.getEnergyCapacity())));
        }
        return new ArrayList<>();
    }

    private static class InfinityEnergyContainer extends NotifiableEnergyContainer {

        public InfinityEnergyContainer(MetaMachine machine, long maxCapacity, long maxInputVoltage, long maxInputAmperage, long maxOutputVoltage, long maxOutputAmperage) {
            super(maxCapacity, maxInputVoltage, maxInputAmperage, maxOutputVoltage, maxOutputAmperage);
        }

        @Override
        public List<EnergyStack> handleRecipeInner(IO io, GTRecipe recipe, List<EnergyStack> left, boolean simulate) {
            return super.handleRecipeInner(io, recipe, left, true);
        }

        @Override
        public long changeEnergy(long energyToAdd) {
            long oldEnergyStored = getEnergyStored();
            long newEnergyStored = (getEnergyCapacity() - oldEnergyStored < energyToAdd) ? getEnergyCapacity() : (oldEnergyStored + energyToAdd);
            if (newEnergyStored < 0) newEnergyStored = 0;
            return newEnergyStored - oldEnergyStored;
        }

        @Override
        public void checkOutputSubscription() {}

        @Override
        public void serverTick() {}

        @Override
        public long acceptEnergyFromNetwork(Direction side, long voltage, long amperage) {
            return 0;
        }

        @Override
        public boolean outputsEnergy(Direction side) {
            return false;
        }

        @Override
        public boolean inputsEnergy(Direction side) {
            return false;
        }
    }
}
