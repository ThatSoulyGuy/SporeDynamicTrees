package com.thatsoulyguy.sporedynamictrees.mixin.spore;

import com.Harbinger.Spore.Sentities.Utility.InfestedConstruct;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thatsoulyguy.sporedynamictrees.compat.LiftRules;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Limits the Infested Construct's thrown blocks to thick Dynamic Trees branches, thrown as their primitive log. */
@Mixin(InfestedConstruct.class)
public abstract class InfestedConstructMixin {
    @WrapOperation(
            method = "getBlock()Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getDestroySpeed(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"))
    private float sporedynamictrees$pickHardness(BlockState state, BlockGetter level, BlockPos pos,
                                                 Operation<Float> original) {
        return LiftRules.isDynamicTreesPart(state)
                ? LiftRules.dynamicTreesLiftHardness(state, level, pos)
                : original.call(state, level, pos);
    }

    @ModifyReturnValue(method = "getBlock()Lnet/minecraft/world/level/block/state/BlockState;", at = @At("RETURN"))
    private BlockState sporedynamictrees$liftPicked(BlockState picked) {
        return picked == null ? null : Objects.requireNonNullElse(LiftRules.liftedState(picked), picked);
    }
}
