package com.thatsoulyguy.sporedynamictrees.mixin.spore;

import com.Harbinger.Spore.Sentities.EvolvedInfected.Busser;
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
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Limits the transporter Busser's block pick-up to thick Dynamic Trees branches, carried as their primitive log. */
@Mixin(Busser.class)
public abstract class BusserMixin {
    @WrapOperation(
            method = "selectBlock()Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getDestroySpeed(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"))
    private float sporedynamictrees$pickHardness(BlockState state, BlockGetter level, BlockPos pos,
                                                 Operation<Float> original) {
        return LiftRules.isDynamicTreesPart(state)
                ? LiftRules.dynamicTreesLiftHardness(state, level, pos)
                : original.call(state, level, pos);
    }

    @ModifyReturnValue(method = "selectBlock()Lnet/minecraft/world/level/block/state/BlockState;", at = @At("RETURN"))
    private BlockState sporedynamictrees$liftPicked(BlockState picked) {
        return picked == null ? null : Objects.requireNonNullElse(LiftRules.liftedState(picked), picked);
    }

    /** Also covers carried blocks loaded from saves made before this mod was installed. */
    @ModifyVariable(
            method = "setCarriedBlock(Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("HEAD"),
            argsOnly = true)
    private BlockState sporedynamictrees$sanitiseCarried(BlockState state) {
        return state == null ? null : LiftRules.liftedState(state);
    }
}
