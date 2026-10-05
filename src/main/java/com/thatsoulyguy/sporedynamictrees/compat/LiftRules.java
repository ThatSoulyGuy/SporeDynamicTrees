package com.thatsoulyguy.sporedynamictrees.compat;

import com.dtteam.dynamictrees.block.branch.BasicRootsBlock;
import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.block.branch.SurfaceRootBlock;
import com.dtteam.dynamictrees.block.branch.TrunkShellBlock;
import com.dtteam.dynamictrees.block.soil.SoilBlock;
import com.thatsoulyguy.sporedynamictrees.SDTConfig;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Decides which Dynamic Trees blocks Spore mobs may lift or throw, and what they turn into once lifted.
 * <p>
 * Spore removes a block from the world before spawning the entity that carries it, so exclusion has to
 * happen where Spore picks the block: its hardness check. Only thick branches are liftable, and they travel
 * as their family's primitive log. Used on both logical sides (Busser and thrown blocks also tick on the
 * client), so this class must stay stateless.
 */
public final class LiftRules {
    /** Fails every Spore hardness gate ({@code >= 0} or {@code > 0}), so the block is never picked. */
    private static final float UNLIFTABLE = -1F;

    private LiftRules() {
    }

    /** Whether the state is a Dynamic Trees block Spore must not handle as a plain block. */
    public static boolean isDynamicTreesPart(BlockState state) {
        Block block = state.getBlock();
        return block instanceof BranchBlock
                || block instanceof TrunkShellBlock
                || block instanceof SoilBlock
                || block instanceof SurfaceRootBlock;
    }

    /** Whether the state is a branch thick enough to be lifted (roots never are). */
    public static boolean isLiftableBranch(BlockState state) {
        return liftableLog(state) != null;
    }

    /**
     * The hardness Spore should see for a Dynamic Trees part: the primitive log's hardness for a liftable
     * branch, so Spore's own hardness limits treat it like that log, and {@link #UNLIFTABLE} otherwise.
     */
    public static float dynamicTreesLiftHardness(BlockState state, BlockGetter level, BlockPos pos) {
        Block log = liftableLog(state);
        return log != null ? log.defaultBlockState().getDestroySpeed(level, pos) : UNLIFTABLE;
    }

    /**
     * The state a lifted block should carry: the primitive log for a liftable branch, {@code null} for any
     * other Dynamic Trees part, and the input unchanged for everything else.
     */
    @Nullable
    public static BlockState liftedState(BlockState state) {
        if (!isDynamicTreesPart(state)) {
            return state;
        }
        Block log = liftableLog(state);
        return log != null ? log.defaultBlockState() : null;
    }

    @Nullable
    private static Block liftableLog(BlockState state) {
        // Roots are branches too, but their "primitive log" is the family's primitive roots block.
        if (!(state.getBlock() instanceof BranchBlock branch) || branch instanceof BasicRootsBlock) {
            return null;
        }
        if (branch.getRadius(state) < SDTConfig.minLiftableBranchRadius()) {
            return null;
        }
        return branch.getPrimitiveLog().orElse(null);
    }
}
