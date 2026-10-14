package com.ae2utilix.ae2_utilix.client;

import com.ae2utilix.ae2_utilix.AE2Utilix;
import com.ae2utilix.ae2_utilix.network.NetworkHandler;
import com.ae2utilix.ae2_utilix.network.PacketSwitchDecomposerMode;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Sneak + mouse wheel switches the matter decomposer's mode.
 *
 * <p>The key handling lives on the client, so the actual mode change is sent to the server
 * where the held stack really lives.</p>
 */
@Mod.EventBusSubscriber(modid = AE2Utilix.MODID, value = Side.CLIENT)
public final class DecomposerScrollHandler {

    private DecomposerScrollHandler() {
    }

    @SubscribeEvent
    public static void onMouseEvent(MouseEvent event) {
        if (event.getDwheel() == 0) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null || !player.isSneaking()) {
            return;
        }

        ItemStack heldItem = player.getHeldItemMainhand();
        if (heldItem.getItem() != AE2Utilix.MATTER_DECOMPOSER) {
            return;
        }

        int direction = event.getDwheel() > 0 ? 1 : -1;
        NetworkHandler.CHANNEL.sendToServer(new PacketSwitchDecomposerMode(direction));
        event.setCanceled(true);
    }
}