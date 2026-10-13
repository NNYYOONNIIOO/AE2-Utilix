package com.ae2utilix.ae2_utilix.upgrade;

import net.minecraft.item.ItemStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remembers which machines accept which upgrade card family, so the cards can list their
 * supported machines in the tooltip. Supergiant's own {@code Upgrades} registry only stores
 * the association, not this grouped view.
 */
public final class ModUpgradeRegistry {

    private static final Map<String, Map<ItemStack, Integer>> SUPPORTED = new LinkedHashMap<>();

    private ModUpgradeRegistry() {
    }

    public static void registerItem(String upgradeTypeId, ItemStack machine, int maxSupported) {
        if (machine == null || machine.isEmpty()) {
            return;
        }
        SUPPORTED.computeIfAbsent(upgradeTypeId, key -> new LinkedHashMap<>())
                .put(machine, maxSupported);
    }

    public static Map<ItemStack, Integer> getSupported(String upgradeTypeId) {
        Map<ItemStack, Integer> supported = SUPPORTED.get(upgradeTypeId);
        return supported == null ? Collections.emptyMap() : supported;
    }
}