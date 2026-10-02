package com.hepdd.gtmthings.mixin;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;

import com.hepdd.gtmthings.api.machine.trait.ProgrammableCircuitHandler;
import com.hepdd.gtmthings.common.item.VirtualItemProviderBehavior;
import com.hepdd.gtmthings.data.CustomItems;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SimpleTieredMachine.class)
public abstract class SimpleTieredMachineMixin {

    @Inject(method = "<init>(Lcom/gregtechceu/gtceu/api/blockentity/BlockEntityCreationInfo;ILcom/gregtechceu/gtceu/api/machine/trait/recipe/RecipeLogic;Lit/unimi/dsi/fastutil/ints/Int2IntFunction;)V", at = @At("TAIL"), remap = false)
    private void gtmthings$attachProgrammingHandler(BlockEntityCreationInfo info, int tier, RecipeLogic logic,
                                                    Int2IntFunction tankScaling, CallbackInfo ci) {
        SimpleTieredMachine machine = (SimpleTieredMachine) (Object) this;
        machine.attachPersistentTrait("gtmthingsProgrammableCircuit", new ProgrammableCircuitHandler(machine));
        machine.importItems.setFilter(stack -> !stack.is(CustomItems.VIRTUAL_ITEM_PROVIDER.asItem()) || VirtualItemProviderBehavior.getVirtualItem(stack).isEmpty());
    }
}
