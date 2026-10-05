package com.thatsoulyguy.sporedynamictrees.mixin.spore;

import com.Harbinger.Spore.Sentities.FoliageSpread;
import com.thatsoulyguy.sporedynamictrees.compat.RottenTrees;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hands Spore's wood rotting over to {@link RottenTrees} whenever it reaches a Dynamic Trees branch. */
@Mixin(FoliageSpread.class)
public interface FoliageSpreadMixin {
    @Inject(
            method = "convertWood(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void sporedynamictrees$rotDynamicTrees(Level level, BlockState state, BlockPos pos, CallbackInfo ci) {
        if (RottenTrees.onConvertWood(level, state, pos)) {
            ci.cancel();
        }
    }
}
