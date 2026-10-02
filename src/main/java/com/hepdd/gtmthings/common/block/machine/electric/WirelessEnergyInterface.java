package com.hepdd.gtmthings.common.block.machine.electric;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.common.data.GTItems;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

import com.hepdd.gtmthings.api.machine.IWirelessEnergyContainerHolder;
import com.hepdd.gtmthings.api.misc.WirelessEnergyContainer;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

import javax.annotation.ParametersAreNonnullByDefault;

import static com.hepdd.gtmthings.utils.TeamUtil.GetName;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class WirelessEnergyInterface extends TieredIOPartMachine implements com.gregtechceu.gtceu.api.machine.feature.IMuiMachine, IWirelessEnergyContainerHolder {

    private TickableSubscription updEnergySubs;

    @Getter
    @Setter
    @Nullable
    private WirelessEnergyContainer WirelessEnergyContainerCache;

    @SaveField
    public final NotifiableEnergyContainer energyContainer;

    public WirelessEnergyInterface(BlockEntityCreationInfo holder) {
        super(holder, GTValues.MAX, IO.IN);
        this.energyContainer = attachTrait(createEnergyContainer());
    }

    protected NotifiableEnergyContainer createEnergyContainer() {
        NotifiableEnergyContainer container;

        container = NotifiableEnergyContainer.receiverContainer(Long.MAX_VALUE,
                GTValues.VEX[tier], 67108864);
        container.setSideInputCondition(s -> s == getFrontFacing() && isWorkingEnabled());
        container.setCapabilityValidator(s -> s == null || s == getFrontFacing());

        return container;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        updateEnergySubscription();
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (updEnergySubs != null) {
            updEnergySubs.unsubscribe();
            updEnergySubs = null;
        }
    }

    private void updateEnergySubscription() {
        if (this.getUUID() != null) {
            updEnergySubs = subscribeServerTick(updEnergySubs, this::updateEnergy);
        } else if (updEnergySubs != null) {
            updEnergySubs.unsubscribe();
            updEnergySubs = null;
        }
    }

    private void updateEnergy() {
        var currentStored = energyContainer.getEnergyStored();
        if (currentStored <= 0) return;
        WirelessEnergyContainer container = getWirelessEnergyContainer();
        if (container == null) return;
        long changeEnergy = container.addEnergy(currentStored, this);
        if (changeEnergy > 0) energyContainer.setEnergyStored(currentStored - changeEnergy);
    }

    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return false;
    }

    @Override
    public InteractionResult onUseWithItem(com.gregtechceu.gtceu.utils.ExtendedUseOnContext context) {
        var state = context.getLevel().getBlockState(context.getClickedPos());
        var world = context.getLevel();
        var pos = context.getClickedPos();
        var player = context.getPlayer();
        var hand = context.getHand();
        if (isRemote()) return InteractionResult.PASS;
        ItemStack is = player.getItemInHand(hand);
        if (is.isEmpty()) return InteractionResult.PASS;
        if (is.is(GTItems.TOOL_DATA_STICK.asItem())) {
            setOwnerUUID(player.getUUID());
            setWirelessEnergyContainerCache(null);
            player.sendSystemMessage(Component.translatable("gtmthings.machine.wireless_energy_hatch.tooltip.bind", GetName(player)));
            updateEnergySubscription();
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean onLeftClick(Player player, InteractionHand hand, Direction direction) {
        if (isRemote()) return false;
        ItemStack is = player.getItemInHand(hand);
        if (is.isEmpty()) return false;
        if (is.is(GTItems.TOOL_DATA_STICK.asItem())) {
            setOwnerUUID(null);
            setWirelessEnergyContainerCache(null);
            player.sendSystemMessage(Component.translatable("gtmthings.machine.wireless_energy_hatch.tooltip.unbind"));
            updateEnergySubscription();
            return true;
        }
        return false;
    }

    @Override
    public void setOwnerUUID(UUID uuid) {
        super.setOwnerUUID(uuid);
        setWirelessEnergyContainerCache(null);
        updateEnergySubscription();
    }

    @Override
    public @Nullable UUID getUUID() {
        return this.getOwnerUUID();
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
}
