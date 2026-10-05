package com.thatsoulyguy.sporedynamictrees.mixin.spore;

import com.Harbinger.Spore.Sentities.Calamities.Howitzer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thatsoulyguy.sporedynamictrees.compat.LiftRules;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Keeps the Howitzer's stomp from flinging Dynamic Trees parts other than thick branches. */
@Mixin(Howitzer.class)
public abstract class HowitzerMixin {
    /**
     * Spore asks for the hardness at the stomp centre rather than at the block being tested, so the real
     * position is used here instead. Local ordinal 1 is the iterated position (ordinal 0 is the centre
     * parameter).
     */
    @WrapOperation(
            method = "damageStomp(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;DD)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getDestroySpeed(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"))
    private float sporedynamictrees$stompHardness(BlockState state, BlockGetter level, BlockPos ignoredCentre,
                                                  Operation<Float> original, @Local(ordinal = 1) BlockPos blockPos) {
        return LiftRules.isDynamicTreesPart(state)
                ? LiftRules.dynamicTreesLiftHardness(state, level, blockPos)
                : original.call(state, level, blockPos);
    }

    @ModifyArg(
            method = "damageStomp(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;DD)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/item/FallingBlockEntity;fall(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/entity/item/FallingBlockEntity;"),
            index = 2)
    private BlockState sporedynamictrees$liftedState(BlockState state) {
        return Objects.requireNonNullElse(LiftRules.liftedState(state), state);
    }
}
