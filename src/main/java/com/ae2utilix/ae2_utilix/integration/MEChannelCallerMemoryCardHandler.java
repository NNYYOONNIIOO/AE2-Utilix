package com.ae2utilix.ae2_utilix.integration;

import ae2.api.implementations.items.IMemoryCard;
import com.ae2utilix.ae2_utilix.block.TileMEChannelCaller;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Handles memory cards before AE2's generic card interaction consumes them. */
public final class MEChannelCallerMemoryCardHandler {
    private MEChannelCallerMemoryCardHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event == null || event.getWorld().isRemote) return;
        ItemStack held = event.getEntityPlayer().getHeldItem(event.getHand());
        if (held.isEmpty() || !(held.getItem() instanceof IMemoryCard)) return;
        TileEntity tile = event.getWorld().getTileEntity(event.getPos());
        if (!(tile instanceof TileMEChannelCaller)) return;
        ((TileMEChannelCaller) tile).handleMemoryCard(event.getEntityPlayer(), held);
        event.setCanceled(true);
    }
}
