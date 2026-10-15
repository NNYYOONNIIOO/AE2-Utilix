package com.ae2utilix.ae2_utilix.registry;

import com.ae2utilix.ae2_utilix.AE2Utilix;
import com.ae2utilix.ae2_utilix.config.AE2UtilixConfig;
import net.minecraft.item.Item;
import net.minecraftforge.fml.common.registry.GameRegistry;

/**
 * Central place where this mod's items and blocks are registered.
 *
 * <p>Features are added here one by one as they are ported; each addition is guarded by its
 * own switch in {@link AE2UtilixConfig} so a disabled feature also stays unloaded.</p>
 */
public final class ModRegistry {

    private ModRegistry() {
    }

    /** Items, blocks and tile entities. */
    public static void register() {
        if (AE2UtilixConfig.registerMatterDecomposer) {
            registerItem(AE2Utilix.MATTER_DECOMPOSER);
        }
        if (AE2UtilixConfig.registerProductReturnCard) {
            registerItem(AE2Utilix.PRODUCT_RETURN_CARD);
        }
        if (AE2UtilixConfig.registerPhaseCard) {
            registerItem(AE2Utilix.PHASE_CARD);
        }
        if (AE2UtilixConfig.registerPhaseInterface) {
            registerBlock(AE2Utilix.BLOCK_PHASE_PATTERN_PROVIDER, "phased_pattern_provider");
            registerTile("phased_pattern_provider",
                    com.ae2utilix.ae2_utilix.block.TilePhasedPatternProvider.class);
        }
        if (AE2UtilixConfig.registerCouplingStaff) {
            registerItem(AE2Utilix.COUPLING_STAFF);
        }
        if (AE2UtilixConfig.registerCrystalGrowthChamber) {
            registerBlock(AE2Utilix.BLOCK_CRYSTAL_GROWTH_CHAMBER, "crystal_growth_chamber");
            registerTile("crystal_growth_chamber", com.ae2utilix.ae2_utilix.block.TileCrystalGrowthChamber.class);
        }
        if (AE2UtilixConfig.registerBlockStorageBus) {
            registerItem(AE2Utilix.BLOCK_STORAGE_BUS);
        }
        if (AE2UtilixConfig.registerFullTerminals) {
            registerBlock(AE2Utilix.BLOCK_STORAGE_TERMINAL, "storage_terminal");
            registerTile("storage_terminal",
                    com.ae2utilix.ae2_utilix.block.terminal.TileStorageTerminal.class);
            registerBlock(AE2Utilix.BLOCK_CRAFTING_TERMINAL, "crafting_terminal");
            registerTile("crafting_terminal",
                    com.ae2utilix.ae2_utilix.block.terminal.TileCraftingTerminal.class);
            registerBlock(AE2Utilix.BLOCK_PATTERN_TERMINAL, "pattern_terminal");
            registerTile("pattern_terminal",
                    com.ae2utilix.ae2_utilix.block.terminal.TilePatternTerminal.class);
            registerBlock(AE2Utilix.BLOCK_INTERFACE_TERMINAL, "interface_terminal");
            registerTile("interface_terminal",
                    com.ae2utilix.ae2_utilix.block.terminal.TilePatternAccessTerminal.class);
        }
        if (AE2UtilixConfig.registerMEChannelCaller) {
            // The whole caller lives behind this one call, so a disabled option keeps its
            // block, item, tile entity and event handlers out of the class loading path.
            com.ae2utilix.ae2_utilix.integration.MEChannelCallerRegistration.register();
        }
    }

    /** Crafting recipes that cannot be expressed as plain JSON. */
    public static void registerRecipes() {
    }

    private static void registerItem(Item item) {
        GameRegistry.findRegistry(Item.class).register(item);
    }

    /**
     * Registers an AE2 style block together with its item form.
     *
     * <p>Supergiant's own blocks use {@code BlockDefinition}, but that class assigns the
     * registry name in its constructor and would require an AE2-owned creative tab. This
     * port keeps its own tab, so the block and its item are registered explicitly.</p>
     */
    private static void registerBlock(net.minecraft.block.Block block, String path) {
        block.setRegistryName(AE2Utilix.MODID, path);
        block.setTranslationKey(AE2Utilix.MODID + "." + path);
        block.setCreativeTab(AE2Utilix.AE2_UTILIX_TAB);
        GameRegistry.findRegistry(net.minecraft.block.Block.class).register(block);
        net.minecraft.item.Item itemBlock = block instanceof ae2.block.AEBaseBlock
                ? new ae2.block.AEBaseBlockItem(block)
                : new net.minecraft.item.ItemBlock(block);
        itemBlock.setRegistryName(AE2Utilix.MODID, path);
        itemBlock.setTranslationKey(AE2Utilix.MODID + "." + path);
        itemBlock.setCreativeTab(AE2Utilix.AE2_UTILIX_TAB);
        registerItem(itemBlock);
    }

    private static void registerTile(String path, Class<? extends net.minecraft.tileentity.TileEntity> tileClass) {
        GameRegistry.registerTileEntity(tileClass, new net.minecraft.util.ResourceLocation(AE2Utilix.MODID, path));
    }
}