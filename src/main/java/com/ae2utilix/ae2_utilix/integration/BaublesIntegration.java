package com.ae2utilix.ae2_utilix.integration;

import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;
import ae2.api.networking.IGrid;
import net.minecraft.entity.player.EntityPlayer;

import javax.annotation.Nullable;

/**
 * Reads the Baubles slots for a linked wireless terminal.
 *
 * <p>Only the terminal lookup is ported: the fluix resonance pivot core, which supplied the
 * original bauble-charging behaviour, is not part of this port's feature set. The class is
 * reached reflectively by {@link WirelessTerminalNetworkLocator}, so its absence (Baubles not
 * installed) changes nothing.</p>
 */
public final class BaublesIntegration {

    private BaublesIntegration() {
    }

    /**
     * Network of a wireless terminal sitting in the player's bauble slots, or null when
     * none of them is a linked terminal.
     */
    @Nullable
    public static IGrid findTerminalNetwork(EntityPlayer player) {
        if (player == null) {
            return null;
        }
        try {
            IBaublesItemHandler handler = BaublesApi.getBaublesHandler(player);
            if (handler == null) {
                return null;
            }
            for (int i = 0; i < handler.getSlots(); i++) {
                IGrid grid = WirelessTerminalNetworkLocator.networkOf(handler.getStackInSlot(i), player.world);
                if (grid != null) {
                    return grid;
                }
            }
        } catch (Throwable ignored) {
            // A bauble slot that misbehaves simply does not take part in the search.
        }
        return null;
    }
}