package com.thatsoulyguy.sporedynamictrees.mixin.spore;

import com.Harbinger.Spore.Sentities.Projectile.ThrownBlockProjectile;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thatsoulyguy.sporedynamictrees.compat.LiftRules;
import java.util.Objects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Safety net for thrown blocks created outside the patched pick-ups (addons, commands, old saves): a thick
 * Dynamic Trees branch becomes its primitive log, and any other Dynamic Trees part becomes air.
 */
@Mixin(ThrownBlockProjectile.class)
public abstract class ThrownBlockProjectileMixin {
    // The constructor writes DAMAGE first (ordinal 0) and then STATE (ordinal 1).
    @ModifyArg(
            method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Ljava/lang/Float;Lnet/minecraft/world/level/block/state/BlockState;Ljava/util/function/Predicate;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/syncher/SynchedEntityData;set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;)V",
                    ordinal = 1),
            index = 1)
    private Object sporedynamictrees$sanitiseConstructedState(Object value) {
        return value instanceof BlockState state
                ? Objects.requireNonNullElse(LiftRules.liftedState(state), Blocks.AIR.defaultBlockState())
                : value;
    }

    @ModifyExpressionValue(
            method = "readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/NbtUtils;readBlockState(Lnet/minecraft/core/HolderGetter;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState sporedynamictrees$sanitiseLoadedState(BlockState state) {
        return Objects.requireNonNullElse(LiftRules.liftedState(state), Blocks.AIR.defaultBlockState());
    }
}
