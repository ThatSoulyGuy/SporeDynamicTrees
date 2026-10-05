package com.thatsoulyguy.sporedynamictrees;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only entry point: makes the mod list's "Config" button open NeoForge's generated config screen. */
@Mod(value = SporeDynamicTrees.MODID, dist = Dist.CLIENT)
public final class SporeDynamicTreesClient {
    public SporeDynamicTreesClient(ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
