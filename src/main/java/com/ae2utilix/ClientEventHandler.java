package com.ae2utilix;

import com.ae2utilix.gui.GuiCrystalGrowthChamber;
import com.ae2utilix.client.DevicePackageModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import com.ae2utilix.client.FluidMarkModel;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = AE2Utilix.MODID, value = Side.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        if (AE2UtilixConfig.registerFluidMark) {
            ModelLoaderRegistry.registerLoader(new FluidMarkModel.Loader());
            ModelLoader.setCustomModelResourceLocation(AE2Utilix.FLUID_MARK, 0,
                    new ModelResourceLocation(FluidMarkModel.MODEL, "inventory"));
        }
        if (AE2UtilixConfig.registerPackerAndDevicePackage) {
            ModelLoaderRegistry.registerLoader(new DevicePackageModel.Loader());
            ModelLoader.setCustomModelResourceLocation(AE2Utilix.PACKER, 0,
                    new ModelResourceLocation(AE2Utilix.MODID + ":packer", "inventory"));
            ModelLoader.setCustomModelResourceLocation(AE2Utilix.DEVICE_PACKAGE, 0,
                    new ModelResourceLocation(DevicePackageModel.MODEL, "inventory"));
        }
        if (AE2UtilixConfig.registerMEChannelCaller) {
            Item caller = Item.getItemFromBlock(AE2Utilix.BLOCK_ME_CHANNEL_CALLER);
            if (caller != null) {
                ModelLoader.setCustomModelResourceLocation(caller, 0,
                        new ModelResourceLocation(AE2Utilix.MODID + ":me_channel_caller", "inventory"));
                ModelLoader.setCustomModelResourceLocation(caller, 3,
                        new ModelResourceLocation(AE2Utilix.MODID + ":me_channel_listener", "inventory"));
            }
        }
        if (AE2UtilixConfig.registerProductReturnCard) {
            ModelLoader.setCustomModelResourceLocation(AE2Utilix.PRODUCT_RETURN_CARD, 0,
                    new ModelResourceLocation(AE2Utilix.MODID + ":product_return_card", "inventory"));
        }
        if (AE2UtilixConfig.registerPhaseCard) {
            ModelLoader.setCustomModelResourceLocation(AE2Utilix.PHASE_CARD, 0,
                    new ModelResourceLocation(AE2Utilix.MODID + ":phase_card", "inventory"));
        }
        if (AE2UtilixConfig.registerCouplingStaff) {
            ModelLoader.setCustomModelResourceLocation(AE2Utilix.COUPLING_STAFF, 0,
                    new ModelResourceLocation(AE2Utilix.MODID + ":coupling_staff", "inventory"));
        }
        if (AE2UtilixConfig.registerParallelCard) {
            ModelLoader.setCustomModelResourceLocation(AE2Utilix.PARALLEL_CARD, 0,
                    new ModelResourceLocation(AE2Utilix.MODID + ":parallel_card", "inventory"));
        }
        if (AE2UtilixConfig.registerOverflowDestructionCard) {
            ModelLoader.setCustomModelResourceLocation(AE2Utilix.OVERFLOW_DESTRUCTION_CARD, 0,
                    new ModelResourceLocation(AE2Utilix.MODID + ":overflow_destruction_card", "inventory"));
        }
        if (AE2UtilixConfig.registerMatterDecomposer) {
            ModelLoader.setCustomModelResourceLocation(AE2Utilix.MATTER_DECOMPOSER, 0,
                    new ModelResourceLocation(AE2Utilix.MODID + ":matter_decomposer", "inventory"));
        }
        if (AE2UtilixConfig.registerFluixResonancePivotCore) {
            ModelLoader.setCustomModelResourceLocation(AE2Utilix.FLUIX_RESONANCE_PIVOT_CORE, 0,
                    new ModelResourceLocation(AE2Utilix.MODID + ":fluix_resonance_pivot_core", "inventory"));
        }
        if (AE2UtilixConfig.registerPhaseInterface) {
            Item phaseInterfaceItem = Item.getItemFromBlock(AE2Utilix.BLOCK_PHASE_INTERFACE);
            if (phaseInterfaceItem != null) {
                registerItemModelForMetadata(phaseInterfaceItem,
                        new ModelResourceLocation(AE2Utilix.MODID + ":phase_interface", "inventory"));
            }
        }
        if (AE2UtilixConfig.registerCommonInterface) {
            Item commonInterfaceAlternateItem = Item.getItemFromBlock(AE2Utilix.BLOCK_COMMON_INTERFACE_ALTERNATE);
            if (commonInterfaceAlternateItem != null) {
                registerItemModelForMetadata(commonInterfaceAlternateItem,
                        new ModelResourceLocation(AE2Utilix.MODID + ":common_interface", "inventory"));
            }
        }
        if (AE2UtilixConfig.registerCrystalGrowthChamber) {
            Item cgcItem = Item.getItemFromBlock(AE2Utilix.BLOCK_CRYSTAL_GROWTH_CHAMBER);
            if (cgcItem != null) {
                registerItemModelForMetadata(cgcItem,
                        new ModelResourceLocation(AE2Utilix.MODID + ":crystal_growth_chamber", "inventory"));
            }
        }
    }

    /**
     * Block item metadata stores orientation for several of our devices. The
     * package renderer resolves the original stack, so every orientation must
     * point at the same inventory model.
     */
    private static void registerItemModelForMetadata(Item item, ModelResourceLocation model) {
        for (int metadata = 0; metadata < 16; metadata++) {
            ModelLoader.setCustomModelResourceLocation(item, metadata, model);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (event.getGui() instanceof GuiCrystalGrowthChamber) {
            GuiCrystalGrowthChamber gui = (GuiCrystalGrowthChamber) event.getGui();
            gui.drawTooltipsLate(event.getMouseX(), event.getMouseY());
        }
    }
}
