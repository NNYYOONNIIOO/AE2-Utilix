package com.ae2utilix.ae2_utilix.integration;

import com.ae2utilix.ae2_utilix.AE2Utilix;
import com.ae2utilix.ae2_utilix.block.BlockMEChannelCaller;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

/**
 * Client only half of the channel caller registration.
 *
 * <p>It sits in its own class because it references client side classes; keeping it apart
 * lets {@link MEChannelCallerRegistration} stay loadable on a dedicated server.</p>
 */
@SideOnly(Side.CLIENT)
public final class MEChannelCallerClientModels {

    private MEChannelCallerClientModels() {
    }

    public static void register(@Nullable BlockMEChannelCaller callerBlock) {
        if (callerBlock == null) {
            return;
        }
        Item caller = Item.getItemFromBlock(callerBlock);
        if (caller == null) {
            return;
        }
        ModelLoader.setCustomModelResourceLocation(caller, 0,
                new ModelResourceLocation(AE2Utilix.MODID + ":me_channel_caller", "inventory"));
        ModelLoader.setCustomModelResourceLocation(caller, 3,
                new ModelResourceLocation(AE2Utilix.MODID + ":me_channel_listener", "inventory"));
    }
}