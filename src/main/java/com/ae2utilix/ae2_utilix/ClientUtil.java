package com.ae2utilix.ae2_utilix;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

/** Small helper for action bar messages, shared by the ported tools. */
public final class ClientUtil {

    private ClientUtil() {
    }

    public static void sendActionBar(EntityPlayerMP player, String key, Object... args) {
        ITextComponent text = new TextComponentTranslation(key, args);
        player.sendStatusMessage(text, true);
    }
}