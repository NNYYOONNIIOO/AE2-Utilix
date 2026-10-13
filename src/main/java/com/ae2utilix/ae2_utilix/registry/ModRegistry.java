package com.ae2utilix.ae2_utilix.registry;

import com.ae2utilix.ae2_utilix.AE2Utilix;
import com.ae2utilix.ae2_utilix.config.AE2UtilixConfig;
import net.minecraft.item.Item;
import net.minecraftforge.fml.common.registry.GameRegistry;

/**
 * Central place where this mod's items and blocks are registered.
 *
 * <p>Features are added here one by one as they are ported; each addition is guarded by its
 * own switch in {@link AE2UtilixConfig} so a disabled feature also stays unloaded.</p>
 */
public final class ModRegistry {

    private ModRegistry() {
    }

    /** Items, blocks and tile entities. */
    public static void register() {
        if (AE2UtilixConfig.registerMatterDecomposer) {
            registerItem(AE2Utilix.MATTER_DECOMPOSER);
        }
        if (AE2UtilixConfig.registerProductReturnCard) {
            registerItem(AE2Utilix.PRODUCT_RETURN_CARD);
        }
        if (AE2UtilixConfig.registerPhaseCard) {
            registerItem(AE2Utilix.PHASE_CARD);
        }
    }

    /** Crafting recipes that cannot be expressed as plain JSON. */
    public static void registerRecipes() {
    }

    private static void registerItem(Item item) {
        GameRegistry.findRegistry(Item.class).register(item);
    }
}