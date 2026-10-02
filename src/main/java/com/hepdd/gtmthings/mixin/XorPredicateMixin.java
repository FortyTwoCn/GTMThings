package com.hepdd.gtmthings.mixin;

import com.gregtechceu.gtceu.api.multiblock.MultiPredicate;
import com.gregtechceu.gtceu.api.multiblock.PredicateContext;
import com.gregtechceu.gtceu.api.multiblock.XorPredicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** GT8 must allow a globally required exclusive hatch to occur after the first repeated slice. */
@Mixin(XorPredicate.class)
public abstract class XorPredicateMixin {

    @Inject(method = "testSliceMin", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtmthings$separateSliceAndGlobalMinimum(PredicateContext context, CallbackInfoReturnable<Boolean> result) {
        var predicate = (MultiPredicate) (Object) this;
        if (predicate.expand().stream().allMatch(base -> base.getMinSliceCount() <= 0)) result.setReturnValue(true);
    }
}
