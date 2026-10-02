package com.hepdd.gtmthings.common.block.machine.multiblock.part;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IDataInfoProvider;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableLaserContainer;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.common.item.behavior.PortableScannerBehavior;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gregtechceu.gtceu.utils.ISubscription;

import net.minecraft.network.chat.Component;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

import static net.minecraft.ChatFormatting.*;

@ParametersAreNonnullByDefault
public class CreativeLaserHatchPartMachine extends TieredIOPartMachine implements IDataInfoProvider, com.gregtechceu.gtceu.api.machine.feature.IMuiMachine {

    @SaveField
    private NotifiableLaserContainer buffer;
    @Nullable
    protected ISubscription LaserListener;
    protected TickableSubscription explosionSubs;
    private Long maxEnergy;
    @SaveField
    private long voltage = 0;
    @SaveField
    @Getter
    private int amps = 256;
    @SaveField
    private int setTier = GTValues.IV;

    public static final String[] VNF = new String[] {
            BLUE + "IV",
            LIGHT_PURPLE + "LuV",
            RED + "ZPM",
            DARK_AQUA + "UV",
            DARK_RED + "UHV",
            GREEN + "UEV",
            DARK_GREEN + "UIV",
            YELLOW + "UXV",
            BLUE.toString() + BOLD + "OpV",
            RED.toString() + BOLD + "MAX" };

    public CreativeLaserHatchPartMachine(BlockEntityCreationInfo holder) {
        super(holder, GTValues.MAX, IO.IN);
        this.voltage = GTValues.VEX[setTier];
        this.maxEnergy = com.hepdd.gtmthings.api.gui.EnergyConfigurationUI.capacity(voltage, amps, 64);
        this.buffer = attachTrait(NotifiableLaserContainer.receiverContainer(this.maxEnergy, voltage, amps));
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (LaserListener != null) {
            LaserListener.unsubscribe();
            LaserListener = null;
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        LaserListener = buffer.addChangedListener(this::AddEngerySubscription);
        AddEngerySubscription();
    }

    protected void AddEngerySubscription() {
        explosionSubs = subscribeServerTick(explosionSubs, this::addEng);
    }

    protected void addEng() {
        if (buffer.getInputVoltage() != voltage || buffer.getInputAmperage() != amps) {
            maxEnergy = com.hepdd.gtmthings.api.gui.EnergyConfigurationUI.capacity(voltage, amps, 64);
            buffer.resetBasicInfo(maxEnergy, voltage, amps, 0, 0);
            buffer.setEnergyStored(0);
        }
        if (buffer.getEnergyStored() < this.maxEnergy) {
            buffer.setEnergyStored(this.maxEnergy);
        }
    }

    @Override
    public boolean canShared(com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine controller, String substructure) {
        return false;
    }

    @Override
    public void buildMainUI(brachy.modularui.widget.ParentWidget<?> parent, brachy.modularui.factory.PosGuiData data,
                            brachy.modularui.value.sync.PanelSyncManager syncManager, brachy.modularui.screen.UISettings settings) {
        com.hepdd.gtmthings.api.gui.EnergyConfigurationUI.build(parent, syncManager, () -> voltage, v -> {
            voltage = v;
            setTier = GTUtil.getTierByVoltage(v);
            markAsChanged();
        },
                () -> amps, a -> {
                    amps = a;
                    markAsChanged();
                }, GTValues.IV, 256);
    }

    @NotNull
    @Override
    public List<Component> getDataInfo(PortableScannerBehavior.DisplayMode mode) {
        if (mode == PortableScannerBehavior.DisplayMode.SHOW_ALL || mode == PortableScannerBehavior.DisplayMode.SHOW_ELECTRICAL_INFO) {
            return Collections.singletonList(Component.literal(
                    String.format("%d/%d EU", buffer.getEnergyStored(), buffer.getEnergyCapacity())));
        }
        return new ArrayList<>();
    }
}
