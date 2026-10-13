package com.ae2utilix.ae2_utilix.registry;

/**
 * Central place where this mod's items and blocks are registered.
 *
 * <p>Features are added here one by one as they are ported; each addition is guarded by its
 * own switch in {@code AE2UtilixConfig} so a disabled feature also stays unloaded.</p>
 */
public final class ModRegistry {

    private ModRegistry() {
    }

    /** Items, blocks and tile entities. */
    public static void register() {
    }

    /** Crafting recipes that cannot be expressed as plain JSON. */
    public static void registerRecipes() {
    }
}