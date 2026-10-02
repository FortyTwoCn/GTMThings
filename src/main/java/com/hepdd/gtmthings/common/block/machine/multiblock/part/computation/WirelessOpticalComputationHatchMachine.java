package com.hepdd.gtmthings.common.block.machine.multiblock.part.computation;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IDataStickInteractable;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import com.hepdd.gtmthings.api.capability.IGTMTJadeIF;
import com.hepdd.gtmthings.common.block.machine.trait.WirelessNotifiableComputationContainer;
import com.hepdd.gtmthings.utils.ItemData;
import lombok.Getter;

import javax.annotation.ParametersAreNonnullByDefault;

@Getter
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class WirelessOpticalComputationHatchMachine extends MultiblockPartMachine implements IDataStickInteractable, IGTMTJadeIF, IMuiMachine {

    private final boolean transmitter;

    @SaveField
    @SyncToClient
    private BlockPos transmitterPos;
    @SaveField
    @SyncToClient
    private BlockPos receiverPos;
    protected WirelessNotifiableComputationContainer computationContainer;

    public WirelessOpticalComputationHatchMachine(BlockEntityCreationInfo holder, boolean transmitter) {
        super(holder);
        this.transmitter = transmitter;
        this.computationContainer = attachTrait(new WirelessNotifiableComputationContainer(IO.IN, transmitter));
    }

    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return false;
    }

    @Override
    public boolean canShared(com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine controller, String substructure) {
        return false;
    }

    @Override
    public boolean isbinded() {
        return (this.transmitterPos != null || this.receiverPos != null);
    }

    @Override
    public String getBindPos() {
        if (this.isTransmitter() && this.receiverPos != null) {
            return this.receiverPos.toShortString();
        } else if (!this.isTransmitter() && this.transmitterPos != null) {
            return this.transmitterPos.toShortString();
        }
        return "";
    }

    private static final String KEY_TRANSMITTER = "wireless_computation_transmitter";
    private static final String KEY_RECEIVER = "wireless_computation_receiver";

    private void setTransmitterPos(BlockPos pos) {
        if (transmitterPos != null) {
            var level = getLevel();
            if (level != null) {
                if (level.hasChunkAt(transmitterPos) && MetaMachine.getMachine(level, transmitterPos) instanceof WirelessOpticalComputationHatchMachine machine) {
                    machine.receiverPos = null;
                    machine.syncDataHolder.markClientSyncFieldDirty("receiverPos");
                }
            }
        }
        transmitterPos = pos;
        syncDataHolder.markClientSyncFieldDirty("transmitterPos");
    }

    private void setReceiverPos(BlockPos pos) {
        if (receiverPos != null) {
            var level = getLevel();
            if (level != null) {
                if (level.hasChunkAt(receiverPos) && MetaMachine.getMachine(level, receiverPos) instanceof WirelessOpticalComputationHatchMachine machine) {
                    machine.transmitterPos = null;
                    machine.syncDataHolder.markClientSyncFieldDirty("transmitterPos");
                }
            }
        }
        receiverPos = pos;
        syncDataHolder.markClientSyncFieldDirty("receiverPos");
    }

    private static CompoundTag createPos(BlockPos pos) {
        CompoundTag posTag = new CompoundTag();
        posTag.putInt("x", pos.getX());
        posTag.putInt("y", pos.getY());
        posTag.putInt("z", pos.getZ());
        return posTag;
    }

    private static BlockPos getPos(CompoundTag tag) {
        return new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }

    @Override
    public InteractionResult onDataStickShiftUse(Player player, ItemStack dataStick) {
        if (isRemote()) return InteractionResult.SUCCESS;

        CompoundTag tag = ItemData.read(dataStick);
        BlockPos currentPos = getBlockPos();
        if (isTransmitter()) {
            tag.put(KEY_TRANSMITTER, createPos(currentPos));
            player.sendSystemMessage(Component.translatable("gtmthings.machine.wireless_computation_transmitter_hatch.tobind"));
        } else {
            tag.put(KEY_RECEIVER, createPos(currentPos));
            player.sendSystemMessage(Component.translatable("gtmthings.machine.wireless_computation_receiver_hatch.tobind"));
        }
        tag.putString("wireless_computation_dimension", getLevel().dimension().location().toString());
        ItemData.update(dataStick, data -> data.merge(tag));
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onDataStickUse(Player player, ItemStack dataStick) {
        if (isRemote()) return InteractionResult.sidedSuccess(true);

        CompoundTag tag = ItemData.read(dataStick);
        String dimension = tag.getString("wireless_computation_dimension");
        if (!dimension.isEmpty() && !dimension.equals(getLevel().dimension().location().toString())) return InteractionResult.PASS;

        if (isTransmitter() && tag.contains(KEY_RECEIVER, 10)) {
            BlockPos otherPos = getPos(tag.getCompound(KEY_RECEIVER));
            if (bindWith(otherPos, player)) {
                return InteractionResult.SUCCESS;
            }
        } else if (!isTransmitter() && tag.contains(KEY_TRANSMITTER, 10)) {
            BlockPos otherPos = getPos(tag.getCompound(KEY_TRANSMITTER));
            if (bindWith(otherPos, player)) {
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.PASS;
    }

    private boolean bindWith(BlockPos otherPos, Player player) {
        Level level = getLevel();
        if (level == null || !level.hasChunkAt(otherPos) || otherPos.equals(this.getBlockPos())) return false;

        MetaMachine otherMachine = MetaMachine.getMachine(level, otherPos);
        if (otherMachine instanceof WirelessOpticalComputationHatchMachine otherWoch) {
            if (this.isTransmitter() == otherWoch.isTransmitter()) {
                return false;
            }
            if (isTransmitter()) {
                this.setReceiverPos(otherPos);
                otherWoch.setTransmitterPos(this.getBlockPos());
            } else {
                this.setTransmitterPos(otherPos);
                otherWoch.setReceiverPos(this.getBlockPos());
            }

            player.sendSystemMessage(Component.translatable("gtmthings.machine.wireless_computation_hatch.binded"));
            return true;
        }
        return false;
    }

    @Override
    public void onMachineDestroyed() {
        setTransmitterPos(null);
        setReceiverPos(null);
        super.onMachineDestroyed();
    }
}
