package com.hepdd.gtmthings.common.block.machine.trait;

import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableComputationContainer;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import com.hepdd.gtmthings.common.block.machine.multiblock.part.computation.WirelessOpticalComputationHatchMachine;

import java.util.Collection;
import java.util.List;

public class WirelessNotifiableComputationContainer extends NotifiableComputationContainer {

    public WirelessNotifiableComputationContainer(IO io, boolean transmitter) {
        super(io, transmitter);
    }

    private IOpticalComputationProvider linkedProvider() {
        var receiver = (WirelessOpticalComputationHatchMachine) getMachine();
        var pos = receiver.getTransmitterPos();
        var level = receiver.getLevel();
        if (pos == null || level == null || !level.hasChunkAt(pos)) return null;
        if (MetaMachine.getMachine(level, pos) instanceof WirelessOpticalComputationHatchMachine sender && sender.isTransmitter() && receiver.getBlockPos().equals(sender.getReceiverPos()) && sender.isFormed())
            return sender.getComputationContainer();
        return null;
    }

    @Override
    public int requestCWUt(int amount, boolean simulate, Collection<IOpticalComputationProvider> seen) {
        if (seen.contains(this) || amount <= 0) return 0;
        if (isTransmitter()) return super.requestCWUt(amount, simulate, seen);
        seen.add(this);
        var provider = linkedProvider();
        return provider == null ? 0 : provider.requestCWUt(amount, simulate, seen);
    }

    @Override
    public int getMaxCWUt(Collection<IOpticalComputationProvider> seen) {
        if (seen.contains(this)) return 0;
        if (isTransmitter()) return super.getMaxCWUt(seen);
        seen.add(this);
        var provider = linkedProvider();
        return provider == null ? 0 : provider.getMaxCWUt(seen);
    }

    @Override
    public boolean canBridge(Collection<IOpticalComputationProvider> seen) {
        if (seen.contains(this)) return false;
        if (isTransmitter()) return super.canBridge(seen);
        seen.add(this);
        var provider = linkedProvider();
        return provider == null || provider.canBridge(seen);
    }

    @Override
    public IOpticalComputationProvider getComputationProvider() {
        return isTransmitter() ? super.getComputationProvider() : linkedProvider();
    }

    @Override
    public List<Integer> handleRecipeInner(IO io, GTRecipe recipe, List<Integer> left, boolean simulate) {
        if (io != IO.IN || isTransmitter()) return left;
        long required = left.stream().mapToLong(Integer::longValue).sum();
        if (required > Integer.MAX_VALUE || required < 0) return left;
        int available = requestCWUt(Integer.MAX_VALUE, true);
        if (available < required) return left;
        boolean total = recipe != null && recipe.data.getBoolean("duration_is_total_cwu");
        int drawn = requestCWUt(total ? available : (int) required, simulate);
        if (total && !simulate) {
            var receiver = (WirelessOpticalComputationHatchMachine) getMachine();
            for (var controller : receiver.getControllers()) {
                if (controller instanceof IRecipeLogicMachine logic) logic.getRecipeLogic().setProgressDelta(drawn - 1);
            }
        }
        return drawn >= required ? List.of() : List.of((int) required - drawn);
    }
}
