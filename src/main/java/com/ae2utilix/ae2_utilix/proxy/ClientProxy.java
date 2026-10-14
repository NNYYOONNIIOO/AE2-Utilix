package com.ae2utilix.ae2_utilix.proxy;

import com.ae2utilix.ae2_utilix.AE2Utilix;
import com.ae2utilix.ae2_utilix.Reference;
import com.ae2utilix.ae2_utilix.config.AE2UtilixConfig;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Client side proxy: everything that touches model loading lives here so the dedicated
 * server never references client-only classes.
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                com.ae2utilix.ae2_utilix.client.HighlightRenderer.class);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        if (AE2UtilixConfig.registerProductReturnCard) {
            registerItemModel(AE2Utilix.PRODUCT_RETURN_CARD, "product_return_card");
        }
        if (AE2UtilixConfig.registerPhaseCard) {
            registerItemModel(AE2Utilix.PHASE_CARD, "phase_card");
        }
        if (AE2UtilixConfig.registerMatterDecomposer) {
            registerItemModel(AE2Utilix.MATTER_DECOMPOSER, "matter_decomposer");
        }
        if (AE2UtilixConfig.registerCouplingStaff) {
            registerItemModel(AE2Utilix.COUPLING_STAFF, "coupling_staff");
        }
        if (AE2UtilixConfig.registerPhaseInterface) {
            Item item = Item.getItemFromBlock(AE2Utilix.BLOCK_PHASE_INTERFACE);
            if (item != null) {
                ModelLoader.setCustomModelResourceLocation(item, 0,
                        new ModelResourceLocation(AE2Utilix.MODID + ":phase_interface", "inventory"));
            }
        }
    }

    private static void registerItemModel(Item item, String path) {
        ModelLoader.setCustomModelResourceLocation(item, 0,
                new ModelResourceLocation(AE2Utilix.MODID + ":" + path, "inventory"));
    }
}