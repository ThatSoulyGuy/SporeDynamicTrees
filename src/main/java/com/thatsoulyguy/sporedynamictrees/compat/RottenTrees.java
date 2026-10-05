package com.thatsoulyguy.sporedynamictrees.compat;

import com.dtteam.dynamictrees.api.voxmap.BlockPosBounds;
import com.dtteam.dynamictrees.block.branch.BasicRootsBlock;
import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.block.soil.SoilBlock;
import com.dtteam.dynamictrees.tree.family.Family;
import com.dtteam.dynamictrees.tree.species.Species;
import com.thatsoulyguy.sporedynamictrees.SDTConfig;
import com.thatsoulyguy.sporedynamictrees.SporeDynamicTrees;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Rots Dynamic Trees trees into the {@code sporedynamictrees:rotten} family when Spore's infection reaches them.
 * <p>
 * Spore rots logs one block at a time by replacing them with {@code spore:rotten_log}. Doing that to a
 * Dynamic Trees branch makes Dynamic Trees void everything beyond the replaced block, and Dynamic Trees
 * handles trees made of mixed families poorly anyway. So the first infected branch converts the whole
 * connected tree at once: every branch becomes a rotten branch of the same radius, the tree's leaves are
 * removed without drops and its rooty soil decays. Replacing a branch with another branch never triggers the
 * Dynamic Trees break cascade.
 * <p>
 * A rotten tree is therefore rootless, and Dynamic Trees treats it like any other rootless tree: a sloppy break
 * of one of its branches (such as a Spore mob lifting a thick one) voids the whole tree rather than only the
 * part beyond the break, and {@code /dt clearorphaned} removes rotten trees.
 * <p>
 * Server thread only: Dynamic Trees keeps its destroy mode in a plain static field, and the caches below
 * are unsynchronised.
 */
public final class RottenTrees {
    private static final ResourceLocation ROTTEN_FAMILY = ResourceLocation.fromNamespaceAndPath(SporeDynamicTrees.MODID, "rotten");
    private static final long LOOKUP_RETRY_MILLIS = 10_000L;
    private static final long OVERSIZED_MEMORY_TICKS = 1200L;
    private static final Direction[] DIRECTIONS = Direction.values();

    /** Cached for good once found: families and their blocks survive Dynamic Trees reloads. */
    @Nullable
    private static BranchBlock cachedRottenBranch;
    private static long nextLookupMillis;

    /**
     * Branches seen while scanning trees that turned out too large to rot. A single spread can hit such a
     * tree hundreds of times, and each hit would otherwise repeat a scan of up to the configured limit.
     */
    private static LongOpenHashSet oversizedBranches = new LongOpenHashSet();
    @Nullable
    private static ResourceKey<Level> oversizedDimension;
    private static long oversizedExpiry;

    private RottenTrees() {
    }

    /**
     * Called at the head of Spore's {@code FoliageSpread.convertWood}.
     *
     * @return whether Spore's own conversion must be cancelled
     */
    public static boolean onConvertWood(Level level, BlockState argState, BlockPos pos) {
        BlockState current = level.getBlockState(pos);
        boolean branchInWorld = isTreeBranch(current);
        if (!branchInWorld && !isTreeBranch(argState)) {
            return false;
        }

        // Spore read argState before its other block conversions ran, so prefer what is in the world now.
        BlockState branchState = branchInWorld ? current : argState;
        boolean rotten = isRotten((BranchBlock) branchState.getBlock());
        if (!rotten && !branchState.is(BlockTags.LOGS)) {
            return false;
        }

        // From here on Spore must not run, as it would write rotten_log onto the tree. Nothing is left to
        // convert when the tree is already rotten or the branch has since been replaced.
        if (level.isClientSide() || rotten || !branchInWorld) {
            return true;
        }
        BranchBlock target = rottenBranch();
        if (target != null) {
            rotTree(level, pos, current, target);
        }
        return true;
    }

    private static void rotTree(Level level, BlockPos origin, BlockState originState, BranchBlock rottenBranch) {
        if (isRememberedOversized(level, origin)) {
            return;
        }
        Tree tree = collectTree(level, origin, originState);
        if (tree == null) {
            return;
        }

        // A rooty soil reports the species of the branch above it, so read species before converting.
        Map<Family, Species> speciesByFamily = new HashMap<>();
        for (Soil soil : tree.soils) {
            Species species = soil.block().getSpecies(soil.state(), level, soil.pos());
            if (species.isValid()) {
                speciesByFamily.putIfAbsent(species.getFamily(), species);
            }
        }

        // Dynamic Trees grows leaves around twigs, so those are the end points its leaf removal starts from.
        Map<LeafGroup, List<BlockPos>> twigsByGroup = new LinkedHashMap<>();
        for (Node node : tree.branches) {
            Family family = node.branch().getFamily();
            if (node.radius() == family.getPrimaryThickness()) {
                twigsByGroup.computeIfAbsent(new LeafGroup(family, ChunkPos.asLong(node.pos())), group -> new ArrayList<>())
                        .add(node.pos());
            }
        }

        int maxRadius = rottenBranch.getMaxRadius();
        for (Node node : tree.branches) {
            if (level.getBlockState(node.pos()).getBlock() == node.branch()) {
                rottenBranch.setRadius(level, node.pos(), Math.min(node.radius(), maxRadius), null, 3);
            }
        }

        twigsByGroup.forEach((group, twigs) -> {
            Species species = speciesByFamily.get(group.family());
            clearLeaves(level, origin, group.family(), species != null ? species : group.family().getCommonSpecies(), twigs);
        });

        for (Soil soil : tree.soils) {
            BlockState state = level.getBlockState(soil.pos());
            if (!(state.getBlock() instanceof SoilBlock soilBlock)) {
                continue;
            }
            BlockState decayed = soilBlock.getDecayBlockState(state, level, soil.pos());
            if (isTreeBranch(decayed)) {
                // A mangrove root collar decays into a branch of its own family; rot it too so that the tree
                // stays a single family. The collar is not a branch, so this cannot start a break cascade.
                int radius = ((BranchBlock) decayed.getBlock()).getRadius(decayed);
                rottenBranch.setRadius(level, soil.pos(), Math.min(radius, maxRadius), null, 3);
            } else {
                soilBlock.doDecay(level, soil.pos(), state);
            }
        }

        SporeDynamicTrees.LOGGER.debug("Rotted a Dynamic Trees tree of {} branches at {}", tree.branches.size(), origin);
    }

    /**
     * Finds every branch face-connected to {@code origin}, plus the rooty soils beneath them, without changing
     * the world or loading chunks. Trunk shells are not followed: the thick rotten branch keeps them.
     *
     * @return the tree, or {@code null} if there is nothing to convert, it is larger than the configured limit
     *         or it is not entirely loaded
     */
    @Nullable
    private static Tree collectTree(Level level, BlockPos origin, BlockState originState) {
        BranchBlock originBranch = (BranchBlock) originState.getBlock();
        int originRadius = originBranch.getRadius(originState);
        if (originRadius <= 0) {
            return null;
        }
        Tree tree = new Tree(origin);
        tree.addBranch(new Node(origin, originBranch, originRadius));

        int maxBranches = SDTConfig.maxRotTreeSize();
        LongOpenHashSet visited = new LongOpenHashSet();
        visited.add(origin.asLong());

        // The branch list doubles as the breadth-first queue.
        for (int i = 0; i < tree.branches.size(); i++) {
            BlockPos nodePos = tree.branches.get(i).pos();
            for (Direction direction : DIRECTIONS) {
                BlockPos next = nodePos.relative(direction);
                long key = next.asLong();
                if (visited.contains(key) || level.isOutsideBuildHeight(next)) {
                    continue;
                }
                if (!level.isLoaded(next)) {
                    // Rotting only the loaded part would leave a mixed-family tree. This is not remembered, so
                    // Spore's next hit retries once the chunk is loaded.
                    return null;
                }
                BlockState state = level.getBlockState(next);
                Block block = state.getBlock();
                if (block instanceof BranchBlock branch) {
                    visited.add(key);
                    if (!isConvertible(branch)) {
                        continue;
                    }
                    int radius = branch.getRadius(state);
                    if (radius <= 0) {
                        continue;
                    }
                    if (tree.branches.size() >= maxBranches) {
                        // Better to leave the tree alone than to produce a half-rotten one.
                        SporeDynamicTrees.LOGGER.debug("Not rotting the tree at {}: it has more than {} branches",
                                origin, maxBranches);
                        rememberOversized(level, visited);
                        return null;
                    }
                    tree.addBranch(new Node(next, branch, radius));
                } else if (block instanceof SoilBlock soil
                        && next.relative(soil.getTrunkDirection(level, next)).equals(nodePos)) {
                    tree.addSoil(new Soil(next, soil, state));
                }
            }
        }
        // Converting reaches one block past the tree: thick branches place trunk shells all around them
        // (diagonals included) and every block change updates its neighbours.
        return areChunksLoaded(level, tree.min.offset(-1, 0, -1), tree.max.offset(1, 0, 1)) ? tree : null;
    }

    /** Removes, without drops, the family's leaves around twigs that have just been converted. */
    private static void clearLeaves(Level level, BlockPos origin, Family family, @Nullable Species species,
                                    List<BlockPos> twigs) {
        // destroyLeaves dereferences the species family's branch and its leaf cluster without null checks.
        if (species == null || !species.isValid() || species.getFamily() != family
                || species.getLeavesProperties().getCellKit().getLeafCluster() == null) {
            return;
        }
        BranchBlock familyBranch = family.getBranch().orElse(null);
        if (familyBranch == null) {
            return;
        }

        // destroyLeaves reads blocks up to two leaf ranges around the twigs; it must not load chunks.
        BlockPosBounds reach = family.expandLeavesBlockBounds(family.expandLeavesBlockBounds(new BlockPosBounds(twigs)));
        if (!areChunksLoaded(level, reach.getMin(), reach.getMax())) {
            return;
        }
        // With no twigs of the family left in range, every compatible leaf near them is removed. The drops
        // are collected into the throwaway list instead of being spawned.
        familyBranch.destroyLeaves(level, origin, species, ItemStack.EMPTY, twigs, new HashMap<>(), new ArrayList<>());
    }

    /** The rotten family's branch block, or {@code null} while it is unavailable (e.g. its tree pack failed). */
    @Nullable
    private static BranchBlock rottenBranch() {
        if (cachedRottenBranch == null && Util.getMillis() >= nextLookupMillis) {
            Family family = Family.REGISTRY.get(ROTTEN_FAMILY);
            cachedRottenBranch = family.isValid() ? family.getBranch().orElse(null) : null;
            if (cachedRottenBranch == null) {
                // Retry later rather than caching the miss; the lookup scans the whole family registry.
                nextLookupMillis = Util.getMillis() + LOOKUP_RETRY_MILLIS;
                SporeDynamicTrees.LOGGER.debug("Dynamic Trees family {} is unavailable; not rotting trees", ROTTEN_FAMILY);
            }
        }
        return cachedRottenBranch;
    }

    private static boolean isRememberedOversized(Level level, BlockPos pos) {
        return level.getGameTime() < oversizedExpiry
                && level.dimension().equals(oversizedDimension)
                && oversizedBranches.contains(pos.asLong());
    }

    private static void rememberOversized(Level level, LongOpenHashSet scanned) {
        if (level.getGameTime() >= oversizedExpiry || !level.dimension().equals(oversizedDimension)) {
            oversizedBranches = new LongOpenHashSet();
            oversizedDimension = level.dimension();
        }
        // Accumulate, as each scan stops at the limit and may only have covered part of the tree.
        oversizedBranches.addAll(scanned);
        oversizedExpiry = level.getGameTime() + OVERSIZED_MEMORY_TICKS;
    }

    private static boolean isTreeBranch(BlockState state) {
        Block block = state.getBlock();
        return block instanceof BranchBlock && !(block instanceof BasicRootsBlock);
    }

    private static boolean isConvertible(BranchBlock branch) {
        return !(branch instanceof BasicRootsBlock) && !isRotten(branch);
    }

    private static boolean isRotten(BranchBlock branch) {
        return ROTTEN_FAMILY.equals(branch.getFamily().getRegistryName());
    }

    private static boolean areChunksLoaded(Level level, BlockPos min, BlockPos max) {
        int maxChunkX = SectionPos.blockToSectionCoord(max.getX());
        int maxChunkZ = SectionPos.blockToSectionCoord(max.getZ());
        for (int chunkX = SectionPos.blockToSectionCoord(min.getX()); chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(min.getZ()); chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static final class Tree {
        final List<Node> branches = new ArrayList<>();
        final List<Soil> soils = new ArrayList<>();
        /** Horizontal bounds of every collected position; only X and Z are meaningful. */
        BlockPos min;
        BlockPos max;

        Tree(BlockPos origin) {
            min = origin;
            max = origin;
        }

        void addBranch(Node node) {
            include(node.pos());
            branches.add(node);
        }

        void addSoil(Soil soil) {
            include(soil.pos());
            soils.add(soil);
        }

        private void include(BlockPos pos) {
            min = new BlockPos(Math.min(min.getX(), pos.getX()), 0, Math.min(min.getZ(), pos.getZ()));
            max = new BlockPos(Math.max(max.getX(), pos.getX()), 0, Math.max(max.getZ(), pos.getZ()));
        }
    }

    private record Node(BlockPos pos, BranchBlock branch, int radius) {
    }

    private record Soil(BlockPos pos, SoilBlock block, BlockState state) {
    }

    /**
     * Twigs are grouped by chunk as well as family, so that an unloaded chunk near one part of the tree only
     * skips the leaf removal for the twigs next to it.
     */
    private record LeafGroup(Family family, long chunk) {
    }
}
