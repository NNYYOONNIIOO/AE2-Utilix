package com.ae2utilix.ae2_utilix.network;

import com.ae2utilix.ae2_utilix.AE2Utilix;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/** Forge SimpleNetworkWrapper channel for this mod's packets. */
public final class NetworkHandler {

    public static final SimpleNetworkWrapper CHANNEL = new SimpleNetworkWrapper(AE2Utilix.MODID);

    private static int nextId = 0;

    private NetworkHandler() {
    }

    public static void register() {
        CHANNEL.registerMessage(PacketHighlightBlock.Handler.class, PacketHighlightBlock.class, nextId++,
                Side.CLIENT);
    }
}