package com.thatsoulyguy.sporedynamictrees;

import com.dtteam.dynamictrees.registry.NeoForgeRegistryHandler;
import com.mojang.logging.LogUtils;
import com.thatsoulyguy.sporedynamictrees.gametest.CompatGameTests;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(SporeDynamicTrees.MODID)
public final class SporeDynamicTrees {
    public static final String MODID = "sporedynamictrees";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SporeDynamicTrees(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, SDTConfig.SPEC);

        // Without a registry handler for our namespace, Dynamic Trees would register the rotten
        // family's branch block under "dynamictrees" instead of "sporedynamictrees".
        NeoForgeRegistryHandler.setup(MODID, modEventBus);

        // The game tests are a development tool and are left out of the release jar.
        if (!FMLEnvironment.production) {
            modEventBus.addListener(CompatGameTests::register);
        }
    }
}
