package com.thatsoulyguy.sporedynamictrees;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common configuration. The accessors are called from mixin hooks that can run before the
 * config file is loaded, so they fall back to the default values instead of throwing.
 */
public final class SDTConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.IntValue MIN_LIFTABLE_BRANCH_RADIUS = BUILDER
            .comment(
                    "Smallest Dynamic Trees branch radius that Spore mobs may lift or throw.",
                    "Liftable branches are picked up as their tree family's primitive log (an oak branch becomes an oak log).",
                    "Thinner branches, trunk shells, rooty soil and roots are never lifted.",
                    "8 is the radius of a full block.")
            .defineInRange("minLiftableBranchRadius", 8, 1, 24);

    private static final ModConfigSpec.IntValue MAX_ROT_TREE_SIZE = BUILDER
            .comment(
                    "Largest Dynamic Trees tree, in branch blocks, that Spore's infection can rot in one go.",
                    "Larger trees are left untouched rather than half-rotted; this guards against runaway work on huge or merged trees.")
            .defineInRange("maxRotTreeSize", 4096, 64, 65536);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private SDTConfig() {
    }

    public static int minLiftableBranchRadius() {
        return read(MIN_LIFTABLE_BRANCH_RADIUS);
    }

    public static int maxRotTreeSize() {
        return read(MAX_ROT_TREE_SIZE);
    }

    private static int read(ModConfigSpec.IntValue value) {
        if (!SPEC.isLoaded()) {
            return value.getDefault();
        }
        try {
            return value.get();
        } catch (IllegalStateException e) {
            // The config was unloaded between the check and the read.
            return value.getDefault();
        }
    }
}
