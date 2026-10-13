package com.ae2utilix.integration;

import com.ae2utilix.AE2Utilix;
import com.ae2utilix.block.BlockMEChannelCaller;
import com.ae2utilix.block.TileMEChannelCaller;
import com.ae2utilix.item.ItemMEChannelCaller;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.GameRegistry;

import javax.annotation.Nullable;

/**
 * Every registration step that belongs to the ME channel caller, kept in one place.
 *
 * <p>The class is only touched when the feature is enabled, so switching the config option
 * off keeps the caller block, its item, its tile entity and its event handlers completely
 * out of the class loading path instead of half registering them.</p>
 */
public final class MEChannelCallerRegistration {

    @Nullable
    private static BlockMEChannelCaller block;

    private MEChannelCallerRegistration() {
    }

    /** The registered block, or null when the feature is disabled. */
    @Nullable
    public static BlockMEChannelCaller block() {
        return block;
    }

    /** Registers the block, item, tile entity, event handlers and the TOP probe. */
    public static void register() {
        BlockMEChannelCaller caller = new BlockMEChannelCaller();
        caller.setRegistryName(AE2Utilix.MODID, "me_channel_caller");
        caller.setUnlocalizedName(AE2Utilix.MODID + ".me_channel_caller");
        ItemMEChannelCaller item = new ItemMEChannelCaller(caller);
        item.setRegistryName(AE2Utilix.MODID, "me_channel_caller");
        registerBlockAndItem(caller, item);
        GameRegistry.registerTileEntity(TileMEChannelCaller.class,
                AE2Utilix.MODID + ":me_channel_caller");
        MinecraftForge.EVENT_BUS.register(MEChannelCallerNetwork.class);
        MinecraftForge.EVENT_BUS.register(MEChannelCallerMemoryCardHandler.class);
        block = caller;
        if (Loader.isModLoaded("theoneprobe")) {
            net.minecraftforge.fml.common.event.FMLInterModComms.sendFunctionMessage(
                    "theoneprobe", "getTheOneProbe",
                    "com.ae2utilix.integration.MEChannelCallerTOPIntegration");
        }
    }

    /** Client model registration, forwarded so this server safe class stays clean. */
    public static void registerModels() {
        MEChannelCallerClientModels.register(block);
    }

    private static void registerBlockAndItem(Block blockToRegister, Item item) {
        GameRegistry.findRegistry(Block.class).register(blockToRegister);
        GameRegistry.findRegistry(Item.class).register(item);
    }
}