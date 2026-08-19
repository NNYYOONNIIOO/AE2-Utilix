package com.ae2utilix;

import com.ae2utilix.block.BlockCrystalGrowthChamber;
import com.ae2utilix.block.BlockPhaseInterface;
import com.ae2utilix.block.BlockCommonInterfaceAlternate;
import com.ae2utilix.block.TileCrystalGrowthChamber;
import com.ae2utilix.block.TilePhaseInterface;
import com.ae2utilix.block.TileCommonInterfaceAlternate;
import com.ae2utilix.block.terminal.*;
import com.ae2utilix.gui.FullTerminalGuiHandler;
import com.ae2utilix.integration.FluidReturnHandler;
import com.ae2utilix.integration.GasReturnHandler;
import com.ae2utilix.recipe.CrystalGrowthRecipes;
import net.minecraftforge.fml.common.Loader;
import com.ae2utilix.item.ItemCouplingStaff;
import com.ae2utilix.item.ItemFluixResonancePivotCore;
import com.ae2utilix.item.ItemMatterDecomposer;
import com.ae2utilix.item.ItemOverflowDestructionCard;
import com.ae2utilix.item.ItemPhaseCard;
import com.ae2utilix.item.ItemParallelCard;
import com.ae2utilix.item.ItemDevicePackage;
import com.ae2utilix.item.ItemPacker;
import com.ae2utilix.item.ItemProductReturnCard;
import com.ae2utilix.parts.ItemPartBlockStorageBus;
import com.ae2utilix.parts.ItemPartCommonBus;
import com.ae2utilix.parts.PartCommonBus;
import com.ae2utilix.network.NetworkHandler;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.common.registry.GameRegistry;
import appeng.api.AEApi;
import appeng.api.config.Upgrades;
import appeng.block.AEBaseItemBlock;
import appeng.core.features.ActivityState;
import appeng.core.features.BlockStackSrc;
import appeng.tile.AEBaseTile;
import org.apache.logging.log4j.Logger;

@Mod(modid = AE2Utilix.MODID, name = AE2Utilix.NAME, version = AE2Utilix.VERSION,
        dependencies = "required-after:appliedenergistics2;after:mekeng")
public class AE2Utilix implements IGuiHandler {
    public static final String MODID = "ae2_utilix";
    public static final String NAME = "AE2\u5B9E\u7528\u5668\u68B0";
    public static final String VERSION = "1.2.0";

    @Mod.Instance(AE2Utilix.MODID)
    public static AE2Utilix INSTANCE;
    public static final SimpleNetworkWrapper NETWORK = NetworkHandler.CHANNEL;

    @SidedProxy(clientSide = "com.ae2utilix.ClientProxy", serverSide = "com.ae2utilix.CommonProxy")
    public static CommonProxy PROXY;

    public static final String UPGRADE_PRODUCT_RETURN = "ae2utilix:product_return_card";
    public static final String UPGRADE_PHASE_CARD = "ae2utilix:phase_card";
    public static final String UPGRADE_PARALLEL_CARD = "ae2utilix:parallel_card";
    public static final String UPGRADE_OVERFLOW_DESTRUCTION = "ae2utilix:overflow_destruction_card";

    public static final CreativeTabs AE2_UTILIX_TAB = new CreativeTabs("ae2_utilix") {
        @Override
        public ItemStack getTabIconItem() {
            if (AE2UtilixConfig.registerProductReturnCard) {
                return new ItemStack(PRODUCT_RETURN_CARD);
            }
            if (AE2UtilixConfig.registerFluidMark) {
                return new ItemStack(FLUID_MARK);
            }
            return ItemStack.EMPTY;
        }
    };

    public static final ItemProductReturnCard PRODUCT_RETURN_CARD = new ItemProductReturnCard();
    public static final ItemPhaseCard PHASE_CARD = new ItemPhaseCard();
    public static final ItemParallelCard PARALLEL_CARD = new ItemParallelCard();
    public static final ItemOverflowDestructionCard OVERFLOW_DESTRUCTION_CARD = new ItemOverflowDestructionCard();
    public static final com.ae2utilix.item.ItemFluidMark FLUID_MARK = new com.ae2utilix.item.ItemFluidMark();
    public static final ItemCouplingStaff COUPLING_STAFF = new ItemCouplingStaff();
    public static final ItemMatterDecomposer MATTER_DECOMPOSER = new ItemMatterDecomposer();
    public static final ItemFluixResonancePivotCore FLUIX_RESONANCE_PIVOT_CORE = new ItemFluixResonancePivotCore();
    public static final ItemPacker PACKER = new ItemPacker();
    public static final ItemDevicePackage DEVICE_PACKAGE = new ItemDevicePackage();
    public static final ItemPartBlockStorageBus BLOCK_STORAGE_BUS = new ItemPartBlockStorageBus();
    public static final ItemPartCommonBus COMMON_IMPORT_BUS = new ItemPartCommonBus(false);
    public static final ItemPartCommonBus COMMON_EXPORT_BUS = new ItemPartCommonBus(true);

    public static final BlockPhaseInterface BLOCK_PHASE_INTERFACE = new BlockPhaseInterface();
    public static final BlockCommonInterfaceAlternate BLOCK_COMMON_INTERFACE_ALTERNATE = new BlockCommonInterfaceAlternate();
    public static final BlockCrystalGrowthChamber BLOCK_CRYSTAL_GROWTH_CHAMBER = new BlockCrystalGrowthChamber();

    public static final BlockStorageTerminal BLOCK_STORAGE_TERMINAL = new BlockStorageTerminal();
    public static final BlockCraftingTerminal BLOCK_CRAFTING_TERMINAL = new BlockCraftingTerminal();
    public static final BlockPatternTerminal BLOCK_PATTERN_TERMINAL = new BlockPatternTerminal();
    public static final BlockInterfaceTerminal BLOCK_INTERFACE_TERMINAL = new BlockInterfaceTerminal();

    public static Logger LOGGER;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        INSTANCE = this;
        LOGGER = event.getModLog();

        AE2UtilixConfig.load(event.getSuggestedConfigurationFile());

        NetworkHandler.register();

        net.minecraftforge.fml.common.network.NetworkRegistry.INSTANCE.registerGuiHandler(this, this);

        if (AE2UtilixConfig.registerPhaseInterface) {
            BLOCK_PHASE_INTERFACE.setRegistryName(MODID, "phase_interface");
            BLOCK_PHASE_INTERFACE.setUnlocalizedName(MODID + ".phase_interface");
            Item itemBlock = new AEBaseItemBlock(BLOCK_PHASE_INTERFACE);
            itemBlock.setRegistryName(MODID, "phase_interface");
            registerBlockAndItem(BLOCK_PHASE_INTERFACE, itemBlock);
            GameRegistry.registerTileEntity(TilePhaseInterface.class, MODID + ":phase_interface");
        }

        if (AE2UtilixConfig.registerCommonInterface) {
            BLOCK_COMMON_INTERFACE_ALTERNATE.setRegistryName(MODID, "common_interface");
            BLOCK_COMMON_INTERFACE_ALTERNATE.setUnlocalizedName(MODID + ".common_interface");
            Item itemBlockCommonInterfaceAlternate = new AEBaseItemBlock(BLOCK_COMMON_INTERFACE_ALTERNATE);
            itemBlockCommonInterfaceAlternate.setRegistryName(MODID, "common_interface");
            registerBlockAndItem(BLOCK_COMMON_INTERFACE_ALTERNATE, itemBlockCommonInterfaceAlternate);
            GameRegistry.registerTileEntity(TileCommonInterfaceAlternate.class, MODID + ":common_interface");
        }

        if (AE2UtilixConfig.registerCrystalGrowthChamber) {
            BLOCK_CRYSTAL_GROWTH_CHAMBER.setRegistryName(MODID, "crystal_growth_chamber");
            BLOCK_CRYSTAL_GROWTH_CHAMBER.setUnlocalizedName(MODID + ".crystal_growth_chamber");
            Item itemBlockCGC = new ItemBlock(BLOCK_CRYSTAL_GROWTH_CHAMBER);
            itemBlockCGC.setRegistryName(MODID, "crystal_growth_chamber");
            registerBlockAndItem(BLOCK_CRYSTAL_GROWTH_CHAMBER, itemBlockCGC);
            GameRegistry.registerTileEntity(TileCrystalGrowthChamber.class, MODID + ":crystal_growth_chamber");
        }

        if (AE2UtilixConfig.registerFullTerminals) {
            registerTerminalBlock(BLOCK_STORAGE_TERMINAL, TileStorageTerminal.class);
            registerTerminalBlock(BLOCK_CRAFTING_TERMINAL, TileCraftingTerminal.class);
            registerTerminalBlock(BLOCK_PATTERN_TERMINAL, TilePatternTerminal.class);
            registerTerminalBlock(BLOCK_INTERFACE_TERMINAL, TileInterfaceTerminal.class);
        }

        registerItemIf(AE2UtilixConfig.registerProductReturnCard, PRODUCT_RETURN_CARD);
        registerItemIf(AE2UtilixConfig.registerPhaseCard, PHASE_CARD);
        registerItemIf(AE2UtilixConfig.registerParallelCard, PARALLEL_CARD);
        registerItemIf(AE2UtilixConfig.registerOverflowDestructionCard, OVERFLOW_DESTRUCTION_CARD);
        registerItemIf(AE2UtilixConfig.registerFluidMark, FLUID_MARK);
        registerItemIf(AE2UtilixConfig.registerCouplingStaff, COUPLING_STAFF);
        registerItemIf(AE2UtilixConfig.registerMatterDecomposer, MATTER_DECOMPOSER);
        registerItemIf(AE2UtilixConfig.registerFluixResonancePivotCore, FLUIX_RESONANCE_PIVOT_CORE);
        if (AE2UtilixConfig.registerPackerAndDevicePackage) {
            registerItemIf(true, PACKER);
            registerItemIf(true, DEVICE_PACKAGE);
        }
        registerItemIf(AE2UtilixConfig.registerBlockStorageBus, BLOCK_STORAGE_BUS);
        if (AE2UtilixConfig.registerCommonBuses) {
            registerItemIf(true, COMMON_IMPORT_BUS);
            registerItemIf(true, COMMON_EXPORT_BUS);
        }

        PROXY.preInit();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        if (AE2UtilixConfig.registerPhaseInterface) {
            AEBaseTile.registerTileItem(TilePhaseInterface.class,
                    new BlockStackSrc(BLOCK_PHASE_INTERFACE, 0, ActivityState.Enabled));
        }
        if (AE2UtilixConfig.registerCommonInterface) {
            AEBaseTile.registerTileItem(TileCommonInterfaceAlternate.class,
                    new BlockStackSrc(BLOCK_COMMON_INTERFACE_ALTERNATE, 0, ActivityState.Enabled));
        }
        if (AE2UtilixConfig.registerFullTerminals) {
            AEBaseTile.registerTileItem(TileStorageTerminal.class,
                    new BlockStackSrc(BLOCK_STORAGE_TERMINAL, 0, ActivityState.Enabled));
            AEBaseTile.registerTileItem(TileCraftingTerminal.class,
                    new BlockStackSrc(BLOCK_CRAFTING_TERMINAL, 0, ActivityState.Enabled));
            AEBaseTile.registerTileItem(TilePatternTerminal.class,
                    new BlockStackSrc(BLOCK_PATTERN_TERMINAL, 0, ActivityState.Enabled));
            AEBaseTile.registerTileItem(TileInterfaceTerminal.class,
                    new BlockStackSrc(BLOCK_INTERFACE_TERMINAL, 0, ActivityState.Enabled));
        }

        if ((AE2UtilixConfig.registerCommonInterface || AE2UtilixConfig.registerPhaseInterface)
                && Loader.isModLoaded("ae2bettermagnetcard")) {
            com.ae2utilix.integration.BMCInteractionHandler.register();
        }

        if (AE2UtilixConfig.registerCrystalGrowthChamber) {
            MinecraftForge.EVENT_BUS.register(com.ae2utilix.integration.CGCMemoryCardHandler.class);
        }

        PROXY.init();

        if (AE2UtilixConfig.registerFluixResonancePivotCore && Loader.isModLoaded("baubles")) {
            com.ae2utilix.integration.BaublesIntegration.init();
            LOGGER.info("Baubles detected, registering bauble integration...");
        }

        LOGGER.info("{} v{} loaded!", NAME, VERSION);
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        if (AE2UtilixConfig.registerCrystalGrowthChamber) {
            CrystalGrowthRecipes.init();
        }

        if (AE2UtilixConfig.registerPhaseInterface) {
            Upgrades.CRAFTING.registerItem(new ItemStack(BLOCK_PHASE_INTERFACE), 1);
            Upgrades.PATTERN_EXPANSION.registerItem(new ItemStack(BLOCK_PHASE_INTERFACE), 3);
            Upgrades.MAGNET.registerItem(new ItemStack(BLOCK_PHASE_INTERFACE), 1);
        }
        if (AE2UtilixConfig.registerCrystalGrowthChamber) {
            Upgrades.SPEED.registerItem(new ItemStack(BLOCK_CRYSTAL_GROWTH_CHAMBER), 5);
        }

        // Block Storage Bus upgrades: Capacity, Fuzzy, Inverter
        if (AE2UtilixConfig.registerBlockStorageBus) {
            Upgrades.CAPACITY.registerItem(new ItemStack(BLOCK_STORAGE_BUS), 5);
            Upgrades.FUZZY.registerItem(new ItemStack(BLOCK_STORAGE_BUS), 1);
            Upgrades.INVERTER.registerItem(new ItemStack(BLOCK_STORAGE_BUS), 1);
        }

        // The common interface exposes four AE2 capacity-card slots.
        if (AE2UtilixConfig.registerCommonInterface) {
            Upgrades.CAPACITY.registerItem(new ItemStack(BLOCK_COMMON_INTERFACE_ALTERNATE), 4);
            // The common interface can request missing configured resources from AE2 crafting CPUs.
            Upgrades.CRAFTING.registerItem(new ItemStack(BLOCK_COMMON_INTERFACE_ALTERNATE), 1);
        }

        // Common import/export buses use the standard non-crafting upgrade
        // set shared by AE2's item buses.  Registering the item stacks is required
        // by PartUpgradeable's StackUpgradeInventory; without it, cards are
        // rejected even when a GUI slot is present.
        if (AE2UtilixConfig.registerCommonBuses) {
            registerCommonBusUpgrades(COMMON_IMPORT_BUS);
            registerCommonBusUpgrades(COMMON_EXPORT_BUS);
        }

        AEApi.instance().definitions().blocks().iface().maybeItem()
                .ifPresent(i -> Upgrades.MAGNET.registerItem(new ItemStack(i), 1));
        AEApi.instance().definitions().parts().iface().maybeItem()
                .ifPresent(i -> Upgrades.MAGNET.registerItem(new ItemStack(i), 1));

        if (AE2UtilixConfig.registerProductReturnCard) {
            registerUpgradeTarget(UPGRADE_PRODUCT_RETURN, "appliedenergistics2:interface", 1);
            registerUpgradeTarget(UPGRADE_PRODUCT_RETURN, "appliedenergistics2:part:207", 1);
            if (AE2UtilixConfig.registerPhaseInterface) {
                registerUpgradeTarget(UPGRADE_PRODUCT_RETURN, BLOCK_PHASE_INTERFACE, 1);
            }
        }
        if (AE2UtilixConfig.registerPhaseCard) {
            registerUpgradeTarget(UPGRADE_PHASE_CARD, "appliedenergistics2:interface", 1);
            registerUpgradeTarget(UPGRADE_PHASE_CARD, "appliedenergistics2:part:207", 1);
            if (AE2UtilixConfig.registerPhaseInterface) {
                registerUpgradeTarget(UPGRADE_PHASE_CARD, BLOCK_PHASE_INTERFACE, 1);
            }
        }
        if (AE2UtilixConfig.registerParallelCard && AE2UtilixConfig.registerCrystalGrowthChamber) {
            registerUpgradeTarget(UPGRADE_PARALLEL_CARD, BLOCK_CRYSTAL_GROWTH_CHAMBER, 5);
        }

        // Register Overflow Destruction Card for all storage cells
        if (AE2UtilixConfig.registerOverflowDestructionCard) {
            AEApi.instance().definitions().items().cell1k().maybeItem()
                    .ifPresent(i -> { Upgrades.CAPACITY.registerItem(new ItemStack(i), 1); });
        AEApi.instance().definitions().items().cell4k().maybeItem()
                .ifPresent(i -> { Upgrades.CAPACITY.registerItem(new ItemStack(i), 1); });
        AEApi.instance().definitions().items().cell16k().maybeItem()
                .ifPresent(i -> { Upgrades.CAPACITY.registerItem(new ItemStack(i), 1); });
        AEApi.instance().definitions().items().cell64k().maybeItem()
                .ifPresent(i -> { Upgrades.CAPACITY.registerItem(new ItemStack(i), 1); });
        AEApi.instance().definitions().items().fluidCell1k().maybeItem()
                .ifPresent(i -> { Upgrades.CAPACITY.registerItem(new ItemStack(i), 1); });
        AEApi.instance().definitions().items().fluidCell4k().maybeItem()
                .ifPresent(i -> { Upgrades.CAPACITY.registerItem(new ItemStack(i), 1); });
        AEApi.instance().definitions().items().fluidCell16k().maybeItem()
                .ifPresent(i -> { Upgrades.CAPACITY.registerItem(new ItemStack(i), 1); });
            AEApi.instance().definitions().items().fluidCell64k().maybeItem()
                    .ifPresent(i -> { Upgrades.CAPACITY.registerItem(new ItemStack(i), 1); });
        }

        if ((AE2UtilixConfig.registerProductReturnCard || AE2UtilixConfig.registerPhaseCard)
                && FluidReturnHandler.hasAE2FC()) {
            LOGGER.info("AE2FluidCraft-Rework detected, registering integration...");
            ae2utilix$registerAE2FCUpgrade("ae2fc:dual_interface");
            ae2utilix$registerAE2FCUpgrade("ae2fc:part_dual_interface");
            ae2utilix$registerAE2FCUpgrade("ae2fc:trio_interface");
            ae2utilix$registerAE2FCUpgrade("ae2fc:part_trio_interface");

            if (GasReturnHandler.hasGasSupport()) {
                LOGGER.info("Mekanism + AE2FC gas integration detected.");
            }
        }
    }

    private void registerTerminalBlock(BlockFullTerminal block, Class<? extends net.minecraft.tileentity.TileEntity> tileClass) {
        Item itemBlock = new ItemBlock(block);
        itemBlock.setRegistryName(block.getRegistryName());

        GameRegistry.findRegistry(Block.class).register(block);
        GameRegistry.findRegistry(Item.class).register(itemBlock);

        GameRegistry.registerTileEntity(tileClass, MODID + ":" + block.getRegistryName().getResourcePath());
    }

    private void registerBlockAndItem(Block block, Item item) {
        GameRegistry.findRegistry(Block.class).register(block);
        GameRegistry.findRegistry(Item.class).register(item);
    }

    private void registerItemIf(boolean enabled, Item item) {
        if (enabled) {
            GameRegistry.findRegistry(Item.class).register(item);
        }
    }

    private void registerUpgradeTarget(String upgradeTypeId, String itemId, int maxSupported) {
        Item item = Item.getByNameOrId(itemId);
        if (item != null) {
            AE2UtilixUpgrades.registerItem(upgradeTypeId, new ItemStack(item), maxSupported);
        }
    }

    private void registerCommonBusUpgrades(Item busItem) {
        ItemStack bus = new ItemStack(busItem);
        Upgrades.FUZZY.registerItem(bus, 1);
        Upgrades.REDSTONE.registerItem(bus, 1);
        Upgrades.CAPACITY.registerItem(bus, 2);
        Upgrades.SPEED.registerItem(bus, 4);
        // The common bus currently has no crafting-request tracker, so do not
        // advertise a crafting card whose behavior it cannot implement.
    }

    private void registerUpgradeTarget(String upgradeTypeId, Block block, int maxSupported) {
        Item item = Item.getItemFromBlock(block);
        if (item != null) {
            AE2UtilixUpgrades.registerItem(upgradeTypeId, new ItemStack(item), maxSupported);
        }
    }

    private void ae2utilix$registerAE2FCUpgrade(String itemId) {
        Item item = Item.getByNameOrId(itemId);
        if (item != null) {
            Upgrades.CRAFTING.registerItem(new ItemStack(item), 1);
            Upgrades.PATTERN_EXPANSION.registerItem(new ItemStack(item), 3);
            Upgrades.MAGNET.registerItem(new ItemStack(item), 1);
            AE2UtilixUpgrades.registerItem(UPGRADE_PRODUCT_RETURN, new ItemStack(item), 1);
            AE2UtilixUpgrades.registerItem(UPGRADE_PHASE_CARD, new ItemStack(item), 1);
        }
    }

    @Override
    public Object getServerGuiElement(int ID, net.minecraft.entity.player.EntityPlayer player, net.minecraft.world.World world, int x, int y, int z) {
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(new net.minecraft.util.math.BlockPos(x, y, z));
        if (ID >= FullTerminalGuiHandler.GUI_COMMON_IMPORT_BUS
                && ID < FullTerminalGuiHandler.GUI_COMMON_IMPORT_BUS + 6) {
            int sideIndex = ID - FullTerminalGuiHandler.GUI_COMMON_IMPORT_BUS;
            if (sideIndex < 0 || sideIndex >= net.minecraft.util.EnumFacing.values().length) return null;
            com.ae2utilix.parts.PartCommonBus bus = PartCommonBus.findPart(te,
                    net.minecraft.util.EnumFacing.values()[sideIndex]);
            return bus == null ? null : new com.ae2utilix.gui.ContainerCommonBus(player.inventory, bus);
        }
        if (ID >= FullTerminalGuiHandler.GUI_COMMON_EXPORT_BUS
                && ID < FullTerminalGuiHandler.GUI_COMMON_EXPORT_BUS + 6) {
            int sideIndex = ID - FullTerminalGuiHandler.GUI_COMMON_EXPORT_BUS;
            if (sideIndex < 0 || sideIndex >= net.minecraft.util.EnumFacing.values().length) return null;
            com.ae2utilix.parts.PartCommonBus bus = PartCommonBus.findPart(te,
                    net.minecraft.util.EnumFacing.values()[sideIndex]);
            return bus == null ? null : new com.ae2utilix.gui.ContainerCommonBus(player.inventory, bus);
        }
        if (te instanceof TileCrystalGrowthChamber) {
            return new com.ae2utilix.gui.ContainerCrystalGrowthChamber(player.inventory,
                    (com.ae2utilix.block.TileCrystalGrowthChamber) te);
        }
        if (te instanceof TileCommonInterfaceAlternate && ID == FullTerminalGuiHandler.GUI_COMMON_INTERFACE) {
            return new com.ae2utilix.gui.ContainerCommonInterface(player.inventory,
                    (TileCommonInterfaceAlternate) te);
        }
        if (te instanceof appeng.api.storage.ITerminalHost) {
            appeng.api.storage.ITerminalHost host = (appeng.api.storage.ITerminalHost) te;
            appeng.container.AEBaseContainer container = null;
            switch (ID) {
                case FullTerminalGuiHandler.GUI_STORAGE_TERMINAL:
                    container = new appeng.container.implementations.ContainerMEMonitorable(player.inventory, host);
                    break;
                case FullTerminalGuiHandler.GUI_CRAFTING_TERMINAL:
                    container = new com.ae2utilix.gui.ContainerFullCrafting(player.inventory, host);
                    break;
                case FullTerminalGuiHandler.GUI_PATTERN_TERMINAL:
                    container = new com.ae2utilix.gui.ContainerFullPattern(player.inventory, host);
                    break;
                case FullTerminalGuiHandler.GUI_INTERFACE_TERMINAL:
                    if (te instanceof com.ae2utilix.block.terminal.TileInterfaceTerminal) {
                        container = new com.ae2utilix.gui.ContainerFullInterface(player.inventory,
                                (com.ae2utilix.block.terminal.TileInterfaceTerminal) te);
                    }
                    break;
            }
            if (container != null) {
                appeng.container.ContainerOpenContext context = new appeng.container.ContainerOpenContext(te);
                context.setWorld(world);
                context.setX(x);
                context.setY(y);
                context.setZ(z);
                context.setSide(appeng.api.util.AEPartLocation.INTERNAL);
                container.setOpenContext(context);
                return container;
            }
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int ID, net.minecraft.entity.player.EntityPlayer player, net.minecraft.world.World world, int x, int y, int z) {
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(new net.minecraft.util.math.BlockPos(x, y, z));
        if (ID >= FullTerminalGuiHandler.GUI_COMMON_IMPORT_BUS
                && ID < FullTerminalGuiHandler.GUI_COMMON_IMPORT_BUS + 6) {
            int sideIndex = ID - FullTerminalGuiHandler.GUI_COMMON_IMPORT_BUS;
            if (sideIndex < 0 || sideIndex >= net.minecraft.util.EnumFacing.values().length) return null;
            com.ae2utilix.parts.PartCommonBus bus = PartCommonBus.findPart(te,
                    net.minecraft.util.EnumFacing.values()[sideIndex]);
            return bus == null ? null : new com.ae2utilix.gui.GuiCommonBus(player.inventory, bus);
        }
        if (ID >= FullTerminalGuiHandler.GUI_COMMON_EXPORT_BUS
                && ID < FullTerminalGuiHandler.GUI_COMMON_EXPORT_BUS + 6) {
            int sideIndex = ID - FullTerminalGuiHandler.GUI_COMMON_EXPORT_BUS;
            if (sideIndex < 0 || sideIndex >= net.minecraft.util.EnumFacing.values().length) return null;
            com.ae2utilix.parts.PartCommonBus bus = PartCommonBus.findPart(te,
                    net.minecraft.util.EnumFacing.values()[sideIndex]);
            return bus == null ? null : new com.ae2utilix.gui.GuiCommonBus(player.inventory, bus);
        }
        if (te instanceof com.ae2utilix.block.TileCrystalGrowthChamber) {
            return new com.ae2utilix.gui.GuiCrystalGrowthChamber(player.inventory,
                    (com.ae2utilix.block.TileCrystalGrowthChamber) te);
        }
        if (te instanceof TileCommonInterfaceAlternate && ID == FullTerminalGuiHandler.GUI_COMMON_INTERFACE) {
            return new com.ae2utilix.gui.GuiCommonInterface(player.inventory,
                    (TileCommonInterfaceAlternate) te);
        }
        if (te instanceof appeng.api.storage.ITerminalHost) {
            appeng.api.storage.ITerminalHost host = (appeng.api.storage.ITerminalHost) te;
            switch (ID) {
                case FullTerminalGuiHandler.GUI_STORAGE_TERMINAL:
                    return new appeng.client.gui.implementations.GuiMEMonitorable(player.inventory, host);
                case FullTerminalGuiHandler.GUI_CRAFTING_TERMINAL:
                    return new com.ae2utilix.gui.GuiFullCrafting(player.inventory, host);
                case FullTerminalGuiHandler.GUI_PATTERN_TERMINAL:
                    return new com.ae2utilix.gui.GuiFullPattern(player.inventory, host);
                case FullTerminalGuiHandler.GUI_INTERFACE_TERMINAL:
                    if (te instanceof com.ae2utilix.block.terminal.TileInterfaceTerminal) {
                        return new com.ae2utilix.gui.GuiFullInterface(player.inventory,
                                (com.ae2utilix.block.terminal.TileInterfaceTerminal) te);
                    }
                    return null;
            }
        }
        return null;
    }
}
