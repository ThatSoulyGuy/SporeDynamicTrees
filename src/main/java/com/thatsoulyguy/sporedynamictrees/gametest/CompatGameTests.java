package com.thatsoulyguy.sporedynamictrees.gametest;

import com.Harbinger.Spore.Sentities.Calamities.Hohlfresser;
import com.Harbinger.Spore.Sentities.Calamities.Howitzer;
import com.Harbinger.Spore.Sentities.EvolvedInfected.Brute;
import com.Harbinger.Spore.Sentities.EvolvedInfected.Busser;
import com.Harbinger.Spore.Sentities.EvolvedInfected.Gargoyl;
import com.Harbinger.Spore.Sentities.FoliageSpread;
import com.Harbinger.Spore.Sentities.Hyper.Grober;
import com.Harbinger.Spore.Sentities.Hyper.Ogre;
import com.Harbinger.Spore.Sentities.Projectile.ThrownBlockProjectile;
import com.Harbinger.Spore.Sentities.Utility.InfestedConstruct;
import com.Harbinger.Spore.core.Sblocks;
import com.Harbinger.Spore.core.Sentities;
import com.dtteam.dynamictrees.block.branch.BasicRootsBlock;
import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.block.leaves.DynamicLeavesBlock;
import com.dtteam.dynamictrees.block.soil.AerialRootsSoilProperties;
import com.dtteam.dynamictrees.block.soil.SoilBlock;
import com.dtteam.dynamictrees.block.soil.SoilProperties;
import com.dtteam.dynamictrees.registry.DTRegistries;
import com.dtteam.dynamictrees.systems.nodemapper.NetVolumeNode;
import com.dtteam.dynamictrees.tree.family.Family;
import com.dtteam.dynamictrees.tree.family.UndergroundRootsFamily;
import com.thatsoulyguy.sporedynamictrees.SDTConfig;
import com.thatsoulyguy.sporedynamictrees.SporeDynamicTrees;
import com.thatsoulyguy.sporedynamictrees.compat.LiftRules;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/**
 * In-game checks of the Spore and Dynamic Trees hooks, run by the {@code runGameTestServer} task. They are
 * registered only in development and left out of the release jar.
 * <p>
 * Every test shares one empty 15x14x15 template ({@code data/sporedynamictrees/structure/compatgametests.empty.nbt})
 * and builds what it needs by hand, finishing within its first tick. Only {@code rot_at_build_limit} builds outside
 * the template, and it clears up after itself.
 */
public final class CompatGameTests {
    private static final String TEMPLATE = "empty";
    private static final ResourceLocation OAK = ResourceLocation.fromNamespaceAndPath("dynamictrees", "oak");
    private static final ResourceLocation ROTTEN = ResourceLocation.fromNamespaceAndPath(SporeDynamicTrees.MODID, "rotten");
    private static final ResourceLocation DIRT_SOIL = ResourceLocation.fromNamespaceAndPath("dynamictrees", "dirt");
    private static final ResourceLocation MANGROVE = ResourceLocation.fromNamespaceAndPath("dynamictrees", "mangrove");
    private static final ResourceLocation MANGROVE_COLLAR_SOIL =
            ResourceLocation.fromNamespaceAndPath("dynamictrees", "mangrove_aerial_roots");
    private static final ResourceLocation ROTTEN_BRANCH =
            ResourceLocation.fromNamespaceAndPath(SporeDynamicTrees.MODID, "rotten_branch");
    /** Mixin prefixes merged handler names (e.g. {@code handler$zzb000$...}) but keeps ours, which all contain this. */
    private static final String HANDLER_MARKER = "sporedynamictrees$";

    private CompatGameTests() {
    }

    public static void register(RegisterGameTestsEvent event) {
        event.register(CompatGameTests.class);
    }

    @GameTest(templateNamespace = SporeDynamicTrees.MODID, template = TEMPLATE)
    public static void mixins_apply(GameTestHelper helper) {
        Map<Class<?>, Integer> expectedHandlers = new LinkedHashMap<>();
        expectedHandlers.put(FoliageSpread.class, 1);
        expectedHandlers.put(Gargoyl.class, 2);
        expectedHandlers.put(Grober.class, 2);
        expectedHandlers.put(Howitzer.class, 2);
        expectedHandlers.put(Hohlfresser.class, 2);
        expectedHandlers.put(Brute.class, 3);
        expectedHandlers.put(Busser.class, 3);
        expectedHandlers.put(Ogre.class, 2);
        expectedHandlers.put(InfestedConstruct.class, 2);
        expectedHandlers.put(ThrownBlockProjectile.class, 2);

        expectedHandlers.forEach((target, expected) -> {
            Class<?> initialised = initialise(target);
            List<String> handlers = Arrays.stream(initialised.getDeclaredMethods())
                    .map(Method::getName)
                    .filter(name -> name.contains(HANDLER_MARKER))
                    .sorted()
                    .toList();
            SporeDynamicTrees.LOGGER.info("[gametest] {} carries {} merged handlers: {}",
                    target.getName(), handlers.size(), handlers);
            helper.assertTrue(handlers.size() >= expected,
                    target.getSimpleName() + " should carry " + expected + " merged handlers but has " + handlers);
        });
        helper.succeed();
    }

    @GameTest(templateNamespace = SporeDynamicTrees.MODID, template = TEMPLATE)
    public static void rot_whole_tree(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Family oak = family(helper, OAK);
        BranchBlock oakBranch = branch(helper, oak);
        SoilBlock rootyDirt = rootyDirt(helper);

        // A tree tapering from radius 5 to a radius 1 twig on top, with a second twig off the radius 2 section.
        BlockPos soilPos = helper.absolutePos(new BlockPos(7, 1, 7));
        BlockPos topTwig = soilPos.above(5);
        BlockPos sideTwig = soilPos.above(4).east();
        Map<BlockPos, Integer> branches = new LinkedHashMap<>();
        int[] trunkRadii = {5, 4, 3, 2, 1};
        for (int i = 0; i < trunkRadii.length; i++) {
            branches.put(soilPos.above(i + 1), trunkRadii[i]);
        }
        branches.put(sideTwig, 1);
        List<BlockPos> leaves = List.of(topTwig.above(), topTwig.north(), topTwig.south(), topTwig.west(),
                topTwig.east(), sideTwig.east(), sideTwig.north(), sideTwig.south());

        level.setBlock(soilPos, rootyDirt.defaultBlockState(), 3);
        branches.forEach((pos, radius) -> oakBranch.setRadius(level, pos, radius, null, 3));
        BlockState leavesState = oak.getCommonLeaves().getDynamicLeavesState(2);
        helper.assertTrue(leavesState.getBlock() instanceof DynamicLeavesBlock, "Oak has no dynamic leaves: " + leavesState);
        leaves.forEach(pos -> level.setBlock(pos, leavesState, 3));
        branches.forEach((pos, radius) -> assertBranch(helper, pos, oakBranch, radius));
        leaves.forEach(pos -> helper.assertTrue(level.getBlockState(pos).is(leavesState.getBlock()),
                "Leaves were not placed at " + helper.relativePos(pos)));

        BlockPos middle = soilPos.above(3);
        BlockState middleState = level.getBlockState(middle);
        // Control: Spore's own conversion would replace this branch with spore:rotten_log.
        helper.assertTrue(middleState.is(BlockTags.LOGS), "Oak branches should be in #minecraft:logs");
        new FoliageSpread() {
        }.convertWood(level, middleState, middle);

        BranchBlock rottenBranch = rottenBranch(helper);
        branches.forEach((pos, radius) -> assertBranch(helper, pos, rottenBranch, radius));
        for (BlockPos pos : leaves) {
            BlockState state = level.getBlockState(pos);
            helper.assertTrue(state.isAir(), "Leaves at " + helper.relativePos(pos) + " should be gone but found " + state);
        }
        BlockState soil = level.getBlockState(soilPos);
        helper.assertTrue(soil.is(Blocks.DIRT) && soil.is(rootyDirt.getPrimitiveSoilBlock()),
                "Rooty soil should have decayed to dirt but found " + soil);
        assertAbsent(helper, Sblocks.ROTTEN_LOG.get());
        assertAbsent(helper, Sblocks.ROTTEN_BRANCH.get());

        // An already rotten tree must be left exactly as it is.
        Map<BlockPos, BlockState> before = snapshot(helper);
        new FoliageSpread() {
        }.convertWood(level, level.getBlockState(middle), middle);
        Map<BlockPos, BlockState> after = snapshot(helper);
        before.forEach((pos, state) -> helper.assertTrue(state == after.get(pos),
                "A second convertWood changed " + helper.relativePos(pos) + " from " + state + " to " + after.get(pos)));
        helper.succeed();
    }

    @GameTest(templateNamespace = SporeDynamicTrees.MODID, template = TEMPLATE)
    public static void rot_mangrove_collar(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Family mangrove = family(helper, MANGROVE);
        BranchBlock mangroveBranch = branch(helper, mangrove);
        BranchBlock roots = mangrove instanceof UndergroundRootsFamily rootsFamily ? rootsFamily.getRoots().orElse(null) : null;
        helper.assertTrue(roots instanceof BasicRootsBlock, "The mangrove family has no roots block: " + roots);
        SoilBlock collar = soil(helper, MANGROVE_COLLAR_SOIL);
        helper.assertTrue(collar instanceof AerialRootsSoilProperties.RootSoilBlock,
                MANGROVE_COLLAR_SOIL + " is not a root collar: " + collar);

        // A mature mangrove: covered roots under a radius 8 root collar, then a trunk tapering to a twig.
        BlockPos rootsPos = helper.absolutePos(new BlockPos(7, 1, 7));
        BlockPos collarPos = rootsPos.above();
        Map<BlockPos, Integer> trunk = new LinkedHashMap<>();
        int[] trunkRadii = {8, 4, 2, 1};
        for (int i = 0; i < trunkRadii.length; i++) {
            trunk.put(collarPos.above(i + 1), trunkRadii[i]);
        }
        // Covered roots keep the collar structurally stable.
        level.setBlock(rootsPos, roots.getStateForRadius(8).setValue(BasicRootsBlock.LAYER, BasicRootsBlock.Layer.COVERED), 3);
        level.setBlock(collarPos, collar.defaultBlockState(), 3);
        trunk.forEach((pos, radius) -> mangroveBranch.setRadius(level, pos, radius, null, 3));
        trunk.forEach((pos, radius) -> assertBranch(helper, pos, mangroveBranch, radius));

        BlockState collarState = level.getBlockState(collarPos);
        // Control: a plain SoilBlock.doDecay would leave a living mangrove branch under the rotten trunk.
        helper.assertTrue(collarState.is(collar) && collar.getDecayBlockState(collarState, level, collarPos).is(mangroveBranch),
                "The root collar should decay into a mangrove branch, but it is " + collarState);

        BlockPos middle = collarPos.above(2);
        BlockState middleState = level.getBlockState(middle);
        helper.assertTrue(middleState.is(BlockTags.LOGS), "Mangrove branches should be in #minecraft:logs");
        new FoliageSpread() {
        }.convertWood(level, middleState, middle);

        BranchBlock rottenBranch = rottenBranch(helper);
        trunk.forEach((pos, radius) -> assertBranch(helper, pos, rottenBranch, radius));
        assertBranch(helper, collarPos, rottenBranch, 8);
        helper.assertTrue(level.getBlockState(rootsPos).is(roots),
                "The mangrove roots should be left alone, but found " + level.getBlockState(rootsPos));
        assertAbsent(helper, mangroveBranch);
        assertAbsent(helper, Sblocks.ROTTEN_LOG.get());
        helper.succeed();
    }

    @GameTest(templateNamespace = SporeDynamicTrees.MODID, template = TEMPLATE)
    public static void rot_at_build_limit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BranchBlock oakBranch = branch(helper, family(helper, OAK));
        BranchBlock rottenBranch = rottenBranch(helper);

        // Above the test area, in its loaded chunk: the twig's upper neighbour is outside the world, which must
        // not count as an unloaded chunk.
        BlockPos twig = helper.absolutePos(new BlockPos(7, 1, 7)).atY(level.getMaxBuildHeight() - 1);
        BlockPos trunk = twig.below();
        BlockPos soilPos = trunk.below();
        level.setBlock(soilPos, rootyDirt(helper).defaultBlockState(), 3);
        oakBranch.setRadius(level, trunk, 2, null, 3);
        oakBranch.setRadius(level, twig, 1, null, 3);
        assertBranch(helper, twig, oakBranch, 1);

        new FoliageSpread() {
        }.convertWood(level, level.getBlockState(trunk), trunk);

        boolean rotted = level.getBlockState(trunk).is(rottenBranch) && level.getBlockState(twig).is(rottenBranch);
        String found = level.getBlockState(trunk) + " / " + level.getBlockState(twig);
        // This area is outside the test bounds, so clear it without letting Dynamic Trees react.
        for (BlockPos pos : List.of(twig, trunk, soilPos)) {
            rottenBranch.setBlockStateIgnored(level, pos, Blocks.AIR.defaultBlockState(), 3);
        }
        helper.assertTrue(rotted, "A tree touching the build limit should rot, but found " + found);
        helper.succeed();
    }

    @GameTest(templateNamespace = SporeDynamicTrees.MODID, template = TEMPLATE)
    public static void rotten_drops(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(7, 1, 7));
        ItemStack axe = new ItemStack(Items.IRON_AXE);
        Item rottenLog = Sblocks.ROTTEN_LOG.get().asItem();

        // Spore's rotten_log loot table means to drop itself only with Silk Touch, but its predicate uses the
        // pre-1.20.5 format, which 1.21.1 ignores, so it drops itself for any tool. Rotten trees match that.
        List<ItemStack> logDrops = Block.getDrops(Sblocks.ROTTEN_LOG.get().defaultBlockState(), level, pos, null, null, axe);
        helper.assertTrue(logDrops.stream().anyMatch(stack -> stack.is(rottenLog)),
                "spore:rotten_log broken with a plain axe should drop itself, but dropped " + logDrops);

        NetVolumeNode.Volume volume = new NetVolumeNode.Volume(3 * NetVolumeNode.Volume.VOXELSPERLOG);
        List<ItemStack> treeDrops = family(helper, ROTTEN).getCommonSpecies().getBranchesDrops(level, volume, axe);
        int logs = treeDrops.stream().filter(stack -> stack.is(rottenLog)).mapToInt(ItemStack::getCount).sum();
        helper.assertTrue(logs > 0, "Three logs of rotten branches should drop spore:rotten_log, but dropped " + treeDrops);
        SporeDynamicTrees.LOGGER.info("[gametest] spore:rotten_log drops {}; three logs of rotten branches drop {}",
                logDrops, treeDrops);
        helper.succeed();
    }

    @GameTest(templateNamespace = SporeDynamicTrees.MODID, template = TEMPLATE)
    public static void lift_rules(GameTestHelper helper) {
        helper.assertTrue(SDTConfig.minLiftableBranchRadius() == 8,
                "These checks assume the default minLiftableBranchRadius of 8, not " + SDTConfig.minLiftableBranchRadius());
        ServerLevel level = helper.getLevel();
        BranchBlock oakBranch = branch(helper, family(helper, OAK));
        BlockPos pos = helper.absolutePos(new BlockPos(7, 1, 7));

        oakBranch.setRadius(level, pos, 2, null, 3);
        assertUnliftable(helper, "radius 2 oak branch", level.getBlockState(pos), pos);

        oakBranch.setRadius(level, pos, 8, null, 3);
        BlockState thick = level.getBlockState(pos);
        BlockState oakLog = Blocks.OAK_LOG.defaultBlockState();
        helper.assertTrue(LiftRules.isLiftableBranch(thick), "A radius 8 oak branch should be liftable");
        float hardness = LiftRules.dynamicTreesLiftHardness(thick, level, pos);
        helper.assertTrue(hardness == 2.0F && hardness == oakLog.getDestroySpeed(level, pos),
                "A radius 8 oak branch should report oak log hardness 2.0, not " + hardness);
        helper.assertTrue(LiftRules.liftedState(thick) == oakLog,
                "A radius 8 oak branch should lift as minecraft:oak_log, not " + LiftRules.liftedState(thick));

        BlockState rottenThick = rottenBranch(helper).getStateForRadius(12);
        BlockState rottenLog = Sblocks.ROTTEN_LOG.get().defaultBlockState();
        helper.assertTrue(LiftRules.liftedState(rottenThick) == rottenLog,
                "A radius 12 rotten branch should lift as spore:rotten_log, not " + LiftRules.liftedState(rottenThick));
        helper.assertTrue(LiftRules.dynamicTreesLiftHardness(rottenThick, level, pos) == rottenLog.getDestroySpeed(level, pos),
                "A radius 12 rotten branch should report spore:rotten_log hardness");

        assertUnliftable(helper, "rooty dirt", rootyDirt(helper).defaultBlockState(), pos);
        assertUnliftable(helper, "trunk shell", DTRegistries.TRUNK_SHELL.get().defaultBlockState(), pos);

        BlockState stone = Blocks.STONE.defaultBlockState();
        helper.assertFalse(LiftRules.isDynamicTreesPart(stone), "Stone is not a Dynamic Trees part");
        helper.assertFalse(LiftRules.isLiftableBranch(stone), "Stone is not a liftable branch");
        helper.assertTrue(LiftRules.liftedState(stone) == stone, "Stone must lift unchanged");
        helper.succeed();
    }

    @GameTest(templateNamespace = SporeDynamicTrees.MODID, template = TEMPLATE)
    public static void brute_pickup(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BranchBlock oakBranch = branch(helper, family(helper, OAK));
        SoilBlock rootyDirt = rootyDirt(helper);
        Brute brute = helper.spawnWithNoFreeWill(Sentities.BRUTE.get(), new BlockPos(7, 2, 7));

        // Brute.blocky() takes the first block in its inflated bounding box whose hardness is in [0, 5).
        BlockPos branchPos = brute.blockPosition();
        BlockPos soilPos = branchPos.below();
        AABB reach = brute.getBoundingBox().inflate(0.2);
        List<BlockPos> scanned = BlockPos.betweenClosedStream(reach).map(BlockPos::immutable).toList();
        helper.assertTrue(scanned.contains(branchPos) && scanned.contains(soilPos),
                "The Brute's pick-up area " + reach + " should cover " + branchPos + " and " + soilPos);
        // Barriers are never picked, so the trunk and its rooty soil are the only candidates.
        for (BlockPos pos : scanned) {
            if (!pos.equals(branchPos) && !pos.equals(soilPos)) {
                level.setBlock(pos, Blocks.BARRIER.defaultBlockState(), 3);
            }
        }
        level.setBlock(soilPos, rootyDirt.defaultBlockState(), 3);

        oakBranch.setRadius(level, branchPos, 2, null, 3);
        // Control: without the hardness hook, Spore's own [0, 5) check would accept the thin branch. (Rooty
        // soil reports hardness 20, so Spore already skips it.)
        assertSporePickable(helper, "radius 2 oak branch", branchPos);
        BlockState picked = brute.blocky();
        helper.assertTrue(picked == null, "The Brute should not pick a radius 2 branch or rooty soil, but picked " + picked);
        assertBranch(helper, branchPos, oakBranch, 2);
        helper.assertTrue(level.getBlockState(soilPos).is(rootyDirt), "The rooty soil should not have been picked");

        oakBranch.setRadius(level, branchPos, 8, null, 3);
        picked = brute.blocky();
        helper.assertTrue(picked == Blocks.OAK_LOG.defaultBlockState(),
                "The Brute should pick a radius 8 branch up as minecraft:oak_log, but picked " + picked);
        helper.assertTrue(level.getBlockState(branchPos).isAir(),
                "The picked branch should be gone but found " + level.getBlockState(branchPos));

        brute.discard();
        helper.succeed();
    }

    private static Class<?> initialise(Class<?> type) {
        try {
            return Class.forName(type.getName(), true, type.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            throw new GameTestAssertException("Could not initialise " + type.getName() + ": " + e);
        }
    }

    private static Family family(GameTestHelper helper, ResourceLocation id) {
        Family family = Family.REGISTRY.getOptional(id).filter(Family::isValid).orElse(null);
        helper.assertTrue(family != null, "Dynamic Trees family " + id + " is not registered");
        return family;
    }

    private static BranchBlock branch(GameTestHelper helper, Family family) {
        BranchBlock branch = family.getBranch().orElse(null);
        helper.assertTrue(branch != null, "Family " + family.getRegistryName() + " has no branch block");
        return branch;
    }

    private static BranchBlock rottenBranch(GameTestHelper helper) {
        BranchBlock branch = branch(helper, family(helper, ROTTEN));
        helper.assertTrue(ROTTEN_BRANCH.equals(BuiltInRegistries.BLOCK.getKey(branch)),
                "The rotten family's branch is registered as " + BuiltInRegistries.BLOCK.getKey(branch));
        return branch;
    }

    private static SoilBlock rootyDirt(GameTestHelper helper) {
        return soil(helper, DIRT_SOIL);
    }

    private static SoilBlock soil(GameTestHelper helper, ResourceLocation id) {
        SoilBlock soil = SoilProperties.REGISTRY.getOptional(id).flatMap(SoilProperties::getBlock).orElse(null);
        helper.assertTrue(soil != null, "Dynamic Trees soil " + id + " has no rooty block");
        return soil;
    }

    private static void assertBranch(GameTestHelper helper, BlockPos pos, BranchBlock expected, int radius) {
        BlockState state = helper.getLevel().getBlockState(pos);
        helper.assertTrue(state.is(expected) && expected.getRadius(state) == radius,
                "Expected " + BuiltInRegistries.BLOCK.getKey(expected) + " of radius " + radius + " at "
                        + helper.relativePos(pos) + " but found " + state);
    }

    private static void assertUnliftable(GameTestHelper helper, String label, BlockState state, BlockPos pos) {
        helper.assertTrue(LiftRules.isDynamicTreesPart(state), label + " should count as a Dynamic Trees part");
        helper.assertFalse(LiftRules.isLiftableBranch(state), label + " should not be liftable");
        float hardness = LiftRules.dynamicTreesLiftHardness(state, helper.getLevel(), pos);
        helper.assertTrue(hardness == -1F, label + " should report lift hardness -1, not " + hardness);
        helper.assertTrue(LiftRules.liftedState(state) == null,
                label + " should have no lifted state, not " + LiftRules.liftedState(state));
    }

    private static void assertSporePickable(GameTestHelper helper, String label, BlockPos pos) {
        ServerLevel level = helper.getLevel();
        float hardness = level.getBlockState(pos).getDestroySpeed(level, pos);
        helper.assertTrue(hardness >= 0F && hardness < 5F,
                label + " should pass Spore's unpatched hardness check, but its hardness is " + hardness);
    }

    private static void assertAbsent(GameTestHelper helper, Block block) {
        BlockPos.betweenClosedStream(helper.getBounds())
                .filter(pos -> helper.getLevel().getBlockState(pos).is(block))
                .findFirst()
                .ifPresent(pos -> helper.fail("Found " + BuiltInRegistries.BLOCK.getKey(block) + " at " + helper.relativePos(pos)));
    }

    private static Map<BlockPos, BlockState> snapshot(GameTestHelper helper) {
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        BlockPos.betweenClosedStream(helper.getBounds())
                .forEach(pos -> states.put(pos.immutable(), helper.getLevel().getBlockState(pos)));
        return states;
    }
}
