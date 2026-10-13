package com.ae2utilix.integration;

import appeng.api.AEApi;
import appeng.api.features.ILocatable;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.features.IWirelessTermRegistry;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Finds the AE network a player's wireless terminal is linked to.
 *
 * <p>Every wireless terminal registers an {@link IWirelessTermHandler}, AE2's own ones as
 * well as addon terminals such as the universal terminal, and that handler keeps the
 * security station's serial as the item's encryption key. Resolving that serial through the
 * public locatable registry yields the network the terminal is bound to, which is what lets
 * a freshly placed caller bind to the network the player is carrying.</p>
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
            IGrid grid = networkOf(stack);
            if (grid != null) {
                return grid;
            }
        }
        for (ItemStack stack : player.inventory.offHandInventory) {
            IGrid grid = networkOf(stack);
            if (grid != null) {
                return grid;
            }
        }
        return null;
    }

    /** The network one terminal item is linked to, or null when it is not linked. */
    @Nullable
    public static IGrid networkOf(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        try {
            IWirelessTermRegistry registry = AEApi.instance().registries().wireless();
            if (registry == null || !registry.isWirelessTerminal(stack)) {
                return null;
            }
            IWirelessTermHandler handler = registry.getWirelessTerminalHandler(stack);
            if (handler == null) {
                return null;
            }
            String key = handler.getEncryptionKey(stack);
            if (key == null || key.isEmpty()) {
                return null;
            }
            long serial;
            try {
                serial = Long.parseLong(key);
            } catch (NumberFormatException badKey) {
                return null;
            }
            ILocatable located = AEApi.instance().registries().locatable().getLocatableBy(serial);
            if (!(located instanceof IActionHost)) {
                return null;
            }
            IGridNode node = ((IActionHost) located).getActionableNode();
            return node == null ? null : node.getGrid();
        } catch (Throwable ignored) {
            // A terminal from another mod that misbehaves simply does not take part.
            return null;
        }
    }
}