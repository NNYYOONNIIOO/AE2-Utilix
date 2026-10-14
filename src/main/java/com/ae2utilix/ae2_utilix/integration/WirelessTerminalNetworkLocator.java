package com.ae2utilix.ae2_utilix.integration;

import ae2.api.networking.IGrid;
import ae2.items.tools.powered.WirelessTerminalItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Finds the AE network a player's wireless terminal is linked to.
 *
 * <p>Supergiant stores the linked access point in the terminal's own NBT, and
 * {@link WirelessTerminalItem#getLinkedGrid(ItemStack, net.minecraft.world.World, java.util.function.Consumer)}
 * already resolves that position back to a grid. That covers AE2's terminals as well as the
 * universal terminal, since the latter is a {@link WirelessTerminalItem} subclass.</p>
 */
public final class WirelessTerminalNetworkLocator {

    private WirelessTerminalNetworkLocator() {
    }

    /** The network of any linked wireless terminal the player carries, or null. */
    @Nullable
    public static IGrid findPlayerNetwork(@Nullable EntityPlayer player) {
        if (player == null || player.world == null || player.world.isRemote) {
            return null;
        }
        for (ItemStack stack : player.inventory.mainInventory) {
            IGrid grid = networkOf(stack, player);
            if (grid != null) {
                return grid;
            }
        }
        for (ItemStack stack : player.inventory.offHandInventory) {
            IGrid grid = networkOf(stack, player);
            if (grid != null) {
                return grid;
            }
        }
        return networkOfBaubles(player);
    }

    /**
     * Looks through the Baubles slots as well. Baubles is an optional dependency, so the
     * integration class is only touched reflectively and its absence changes nothing.
     */
    @Nullable
    private static IGrid networkOfBaubles(EntityPlayer player) {
        try {
            Class<?> integration = Class.forName("com.ae2utilix.ae2_utilix.integration.BaublesIntegration");
            Object result = integration.getMethod("findTerminalNetwork", EntityPlayer.class)
                    .invoke(null, player);
            return result instanceof IGrid ? (IGrid) result : null;
        } catch (Throwable ignored) {
            // Baubles is not installed, or the integration is disabled.
            return null;
        }
    }

    /** The network one terminal item is linked to, or null when it is not linked. */
    @Nullable
    public static IGrid networkOf(@Nullable ItemStack stack, @Nullable EntityPlayer player) {
        if (stack == null || stack.isEmpty() || player == null || player.world == null) {
            return null;
        }
        return networkOf(stack, player.world);
    }

    /** The network one terminal item is linked to, or null when it is not linked. */
    @Nullable
    public static IGrid networkOf(@Nullable ItemStack stack, @Nullable net.minecraft.world.World level) {
        if (stack == null || stack.isEmpty() || level == null) {
            return null;
        }
        try {
            if (!(stack.getItem() instanceof WirelessTerminalItem terminal)) {
                return null;
            }
            return terminal.getLinkedGrid(stack, level, null);
        } catch (Throwable ignored) {
            // A terminal from another mod that misbehaves simply does not take part.
            return null;
        }
    }
}