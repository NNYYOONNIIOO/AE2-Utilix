package com.ae2utilix.ae2_utilix;

import com.ae2utilix.ae2_utilix.config.AE2UtilixConfig;
import com.ae2utilix.ae2_utilix.proxy.IProxy;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Cleanroom port of AE2 Utilix, built against Applied Energistics 2 - Supergiant.
 *
 * <p>The Forge version targeted ae2uel, whose API lives in {@code appeng.*}. Supergiant
 * renamed the package to {@code ae2.*} and replaced parts of its storage API, so every
 * feature in this port is adapted rather than copied verbatim.</p>
 */
@Mod(modid = Reference.MOD_ID, name = Reference.MOD_NAME, version = Reference.VERSION)
public class AE2Utilix {

    public static final String MODID = Reference.MOD_ID;
    public static final Logger LOGGER = LogManager.getLogger(Reference.MOD_NAME);

    /** Upgrade card family ids, used to group the supported-machine tooltip. */
    public static final String UPGRADE_PRODUCT_RETURN = "ae2utilix:product_return_card";
    public static final String UPGRADE_PHASE_CARD = "ae2utilix:phase_card";
    public static final String UPGRADE_PARALLEL_CARD = "ae2utilix:parallel_card";
    public static final String UPGRADE_OVERFLOW_DESTRUCTION = "ae2utilix:overflow_destruction_card";

    /** Ported items. Blocks and tile entities are added here as they are migrated. */
    public static final com.ae2utilix.ae2_utilix.item.ItemMatterDecomposer MATTER_DECOMPOSER =
            new com.ae2utilix.ae2_utilix.item.ItemMatterDecomposer();
    public static final com.ae2utilix.ae2_utilix.item.ItemProductReturnCard PRODUCT_RETURN_CARD =
            new com.ae2utilix.ae2_utilix.item.ItemProductReturnCard();
    public static final com.ae2utilix.ae2_utilix.item.ItemPhaseCard PHASE_CARD =
            new com.ae2utilix.ae2_utilix.item.ItemPhaseCard();

    /** Creative tab holding every item this mod adds. */
    public static final CreativeTabs AE2_UTILIX_TAB = new CreativeTabs(MODID) {
        @Override
        public ItemStack createIcon() {
            return ItemStack.EMPTY;
        }
    };

    @SidedProxy(modId = Reference.MOD_ID,
            clientSide = "com.ae2utilix.ae2_utilix.proxy.ClientProxy",
            serverSide = "com.ae2utilix.ae2_utilix.proxy.CommonProxy")
    public static IProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        AE2UtilixConfig.load(event.getSuggestedConfigurationFile());
        LOGGER.info("{} v{} preInit (Cleanroom build)", Reference.MOD_NAME, Reference.VERSION);
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }
}