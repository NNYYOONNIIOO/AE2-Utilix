package com.ae2utilix.ae2_utilix.upgrade;

import net.minecraft.item.ItemStack;

/**
 * Marks an item added by this mod as an upgrade card.
 *
 * <p>The Forge build reused AE2's {@code Upgrades} enum, but Supergiant replaced that enum
 * with the static {@code Upgrades} registry, so upgrade identity is now carried by this
 * interface instead.</p>
 */
public interface IAE2UtilixUpgradeModule {

    /** Unique id of this upgrade family, used for the supported-machine tooltip. */
    String getUpgradeTypeId();

    /** How many copies of this card one machine may hold. */
    int getMaxInstalled();

    /** True when the given stack is a card of this family. */
    boolean matchesType(ItemStack stack);
}