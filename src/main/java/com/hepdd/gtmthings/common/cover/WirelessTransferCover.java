package com.hepdd.gtmthings.common.cover;

import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.WorkableTieredMachine;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.common.machine.electric.PumpMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.common.machine.storage.CrateMachine;
import com.gregtechceu.gtceu.common.machine.storage.DrumMachine;
import com.gregtechceu.gtceu.common.machine.storage.QuantumChestMachine;
import com.gregtechceu.gtceu.common.machine.storage.QuantumTankMachine;
import com.gregtechceu.gtceu.common.machine.trait.AutoOutputTrait;
import com.gregtechceu.gtceu.utils.GTTransferUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;

import com.hepdd.gtmthings.api.transfer.UnlimitItemTransferHelper;
import com.hepdd.gtmthings.common.block.machine.multiblock.part.HugeBusPartMachine;
import com.hepdd.gtmthings.common.block.machine.multiblock.part.HugeDualHatchPartMachine;
import com.hepdd.gtmthings.utils.ItemData;

public class WirelessTransferCover extends CoverBehavior {

    public static final int TRANSFER_ITEM = 1, TRANSFER_FLUID = 2;
    @SaveField
    protected final int transferType;
    @SaveField
    private String dimensionId = "";
    @SaveField
    protected BlockPos targetPos;
    @SaveField
    protected Direction facing;
    protected ServerLevel targetLever;
    private TickableSubscription subscription;

    public WirelessTransferCover(CoverDefinition definition, ICoverable holder, Direction side, int type) {
        super(definition, holder, side);
        transferType = type;
    }

    protected boolean allowMultipleCovers() {
        return false;
    }

    @Override
    public boolean canAttach() {
        MetaMachine machine = MetaMachine.getMachine(coverHolder.getLevel(), coverHolder.getBlockPos());
        boolean supports;
        if (machine instanceof WorkableTieredMachine workable) {
            supports = transferType == TRANSFER_ITEM ? workable.exportItems.getSlots() > 0 : workable.exportFluids.getTanks() > 0;
        } else if (machine instanceof PumpMachine || machine instanceof QuantumTankMachine || machine instanceof DrumMachine) {
            supports = transferType == TRANSFER_FLUID;
        } else if (machine instanceof QuantumChestMachine || machine instanceof CrateMachine) {
            supports = transferType == TRANSFER_ITEM;
        } else if (machine instanceof HugeBusPartMachine huge) {
            supports = huge.getIo() == IO.OUT && (transferType == TRANSFER_ITEM || huge instanceof HugeDualHatchPartMachine);
        } else supports = (transferType == TRANSFER_ITEM && machine instanceof ItemBusPartMachine bus && bus.getInventory().handlerIO != IO.IN) || (transferType == TRANSFER_FLUID && machine instanceof FluidHatchPartMachine hatch && hatch.tank.handlerIO != IO.IN);
        if (!supports || machine == null) return false;
        return allowMultipleCovers() || machine.getCoverContainer().getCovers().stream()
                .noneMatch(cover -> cover instanceof WirelessTransferCover wireless && wireless.transferType == transferType);
    }

    @Override
    public void onAttached(ItemStack item, ServerPlayer player) {
        super.onAttached(item, player);
        var tag = ItemData.read(item);
        dimensionId = tag.getString("dimensionid");
        if (!dimensionId.isEmpty()) {
            targetPos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
            facing = Direction.byName(tag.getString("facing"));
        }
        MetaMachine machine = MetaMachine.getMachine(coverHolder.getLevel(), coverHolder.getBlockPos());
        if (machine instanceof SimpleTieredMachine simple) {
            simple.getTraitOptional(AutoOutputTrait.class).ifPresent(output -> {
                if (transferType == TRANSFER_ITEM) output.setAllowAutoOutputItems(false);
                else output.setAllowAutoOutputFluids(false);
            });
        } else if (machine instanceof ItemBusPartMachine bus && transferType == TRANSFER_ITEM) bus.setWorkingEnabled(false);
        else if (machine instanceof FluidHatchPartMachine hatch && transferType == TRANSFER_FLUID) hatch.setWorkingEnabled(false);
        markAsChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!coverHolder.isRemote()) subscription = coverHolder.subscribeServerTick(subscription, this::update);
    }

    @Override
    public void onUnload() {
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
        targetLever = null;
        super.onUnload();
    }

    @Override
    public void onRemoved() {
        onUnload();
        super.onRemoved();
    }

    protected void GetLevel() {
        targetLever = null;
        var level = coverHolder.getLevel();
        var id = ResourceLocation.tryParse(dimensionId);
        if (level != null && !level.isClientSide && id != null && level.getServer() != null)
            targetLever = level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    protected boolean testItem(ItemStack stack) {
        return true;
    }

    protected boolean testFluid(FluidStack fluid) {
        return true;
    }

    private void update() {
        if (coverHolder.getOffsetTimer() % 5 != 0) return;
        GetLevel();
        if (targetLever == null || targetPos == null || facing == null || !targetLever.hasChunkAt(targetPos)) return;
        if (transferType == TRANSFER_ITEM) {
            var source = coverHolder.getItemHandlerCap(attachedSide, false);
            if (source != null) UnlimitItemTransferHelper.exportToTarget(source, Integer.MAX_VALUE, this::testItem, targetLever, targetPos, facing);
        } else {
            var source = coverHolder.getFluidHandlerCap(attachedSide, false);
            var target = targetLever.getCapability(Capabilities.FluidHandler.BLOCK, targetPos, facing);
            if (source != null && target != null) GTTransferUtils.transferFluidsFiltered(source, target, this::testFluid, Integer.MAX_VALUE);
        }
    }
}
