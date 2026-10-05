package com.thatsoulyguy.sporedynamictrees.mixin.spore;

import com.Harbinger.Spore.Sentities.Calamities.Hohlfresser;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thatsoulyguy.sporedynamictrees.compat.LiftRules;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Keeps the Hohlfresser's area crumble from dropping Dynamic Trees parts other than thick branches. */
@Mixin(Hohlfresser.class)
public abstract class HohlfresserMixin {
    @WrapOperation(
            method = "tryAndCrumbleBlocks()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getDestroySpeed(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"))
    private float sporedynamictrees$crumbleHardness(BlockState state, BlockGetter level, BlockPos pos,
                                                    Operation<Float> original) {
        return LiftRules.isDynamicTreesPart(state)
                ? LiftRules.dynamicTreesLiftHardness(state, level, pos)
                : original.call(state, level, pos);
    }

    @ModifyArg(
            method = "tryAndCrumbleBlocks()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/item/FallingBlockEntity;fall(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/entity/item/FallingBlockEntity;"),
            index = 2)
    private BlockState sporedynamictrees$liftedState(BlockState state) {
        return Objects.requireNonNullElse(LiftRules.liftedState(state), state);
    }
}
