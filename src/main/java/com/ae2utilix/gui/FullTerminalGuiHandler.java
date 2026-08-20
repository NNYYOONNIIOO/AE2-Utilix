package com.ae2utilix.gui;

import appeng.api.storage.ITerminalHost;
import appeng.client.gui.implementations.GuiPatternTerm;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.container.implementations.ContainerMEMonitorable;
import com.ae2utilix.integration.AE2FCRUCompat;
import com.ae2utilix.block.terminal.TileInterfaceTerminal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

import javax.annotation.Nullable;

public class FullTerminalGuiHandler implements IGuiHandler {

    public static final int GUI_STORAGE_TERMINAL = 10;
    public static final int GUI_CRAFTING_TERMINAL = 11;
    public static final int GUI_PATTERN_TERMINAL = 12;
    public static final int GUI_INTERFACE_TERMINAL = 13;
    public static final int GUI_COMMON_INTERFACE = 14;
    public static final int GUI_COMMON_IMPORT_BUS = 20;
    public static final int GUI_COMMON_EXPORT_BUS = 30;

    /**
     * AE2FC's fluid terminal is an extension of AE2's original pattern
     * terminal.  Use it for this block when AE2FC is installed, so the block
     * gets the native AE2 GUI while the server container retains AE2FC's
     * fluid-pattern encoder and JEI transfer implementation.
     */
    @Nullable
    private static Object createFluidPatternContainer(InventoryPlayer inventory, ITerminalHost host) {
        if (!AE2FCRUCompat.isLoaded()) {
            return null;
        }
        try {
            Class<?> type = Class.forName("com.glodblock.github.client.container.ContainerFluidPatternTerminal");
            Object container = type.getConstructor(InventoryPlayer.class, ITerminalHost.class).newInstance(inventory, host);
            if (!(container instanceof appeng.container.ContainerNull)
                    && !implementsInterface(container, "com.glodblock.github.interfaces.FCFluidPatternContainer")) {
                throw new IllegalStateException("AE2FC returned a container without FCFluidPatternContainer");
            }
            return container;
        } catch (ReflectiveOperationException | LinkageError e) {
            com.ae2utilix.AE2Utilix.LOGGER.warn("Unable to create AE2FC fluid pattern container; using AE2 pattern container", e);
            return null;
        }
    }

    @Nullable
    private static Object createFluidPatternGui(InventoryPlayer inventory, ITerminalHost host) {
        if (!AE2FCRUCompat.isLoaded()) {
            return null;
        }
        try {
            Class<?> type = Class.forName("com.glodblock.github.client.client.gui.GuiFluidPatternTerminal");
            return type.getConstructor(InventoryPlayer.class, ITerminalHost.class).newInstance(inventory, host);
        } catch (ReflectiveOperationException | LinkageError e) {
            com.ae2utilix.AE2Utilix.LOGGER.warn("Unable to create AE2FC fluid pattern GUI; using AE2 pattern GUI", e);
            return null;
        }
    }

    private static boolean implementsInterface(Object value, String interfaceName) throws ClassNotFoundException {
        return Class.forName(interfaceName).isInstance(value);
    }

    @Nullable
    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
        if (ID == GUI_COMMON_INTERFACE && te instanceof com.ae2utilix.block.TileCommonInterfaceAlternate) {
            return new ContainerCommonInterface(player.inventory, (com.ae2utilix.block.TileCommonInterfaceAlternate) te);
        }
        if (!(te instanceof ITerminalHost)) {
            return null;
        }

        ITerminalHost host = (ITerminalHost) te;
        switch (ID) {
            case GUI_STORAGE_TERMINAL:
                return new ContainerMEMonitorable(player.inventory, host);
            case GUI_CRAFTING_TERMINAL:
                return new ContainerFullCrafting(player.inventory, host);
            case GUI_PATTERN_TERMINAL:
                Object fluidPatternContainer = createFluidPatternContainer(player.inventory, host);
                if (fluidPatternContainer != null) {
                    return fluidPatternContainer;
                }
                // Never fall back to the ordinary AE2 container while AE2FC
                // is present: that path can encode a fluid recipe as a
                // normal encoded_pattern. The project's container retains
                // the AE2FC encoder as a safe fallback.
                return AE2FCRUCompat.isLoaded()
                        ? new ContainerFullPattern(player.inventory, host)
                        : new ContainerPatternTerm(player.inventory, host);
            case GUI_INTERFACE_TERMINAL:
                if (te instanceof TileInterfaceTerminal) {
                    return new ContainerFullInterface(player.inventory, (TileInterfaceTerminal) te);
                }
                return null;
            case GUI_COMMON_INTERFACE:
                if (te instanceof com.ae2utilix.block.TileCommonInterfaceAlternate) {
                    return new ContainerCommonInterface(player.inventory, (com.ae2utilix.block.TileCommonInterfaceAlternate) te);
                }
                return null;
            default:
                return null;
        }
    }

    @Nullable
    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
        if (ID == GUI_COMMON_INTERFACE && te instanceof com.ae2utilix.block.TileCommonInterfaceAlternate) {
            return new GuiCommonInterface(player.inventory, (com.ae2utilix.block.TileCommonInterfaceAlternate) te);
        }
        if (!(te instanceof ITerminalHost)) {
            return null;
        }

        ITerminalHost host = (ITerminalHost) te;
        switch (ID) {
            case GUI_STORAGE_TERMINAL:
                return new GuiMEMonitorable(player.inventory, host);
            case GUI_CRAFTING_TERMINAL:
                return new GuiFullCrafting(player.inventory, host);
            case GUI_PATTERN_TERMINAL:
                Object fluidPatternGui = createFluidPatternGui(player.inventory, host);
                if (fluidPatternGui != null) {
                    return fluidPatternGui;
                }
                // Keep the client/server container pair consistent with the
                // server fallback above and preserve AE2FC fluid encoding.
                return AE2FCRUCompat.isLoaded()
                        ? new GuiFullPattern(player.inventory, host)
                        : new GuiPatternTerm(player.inventory, host);
            case GUI_INTERFACE_TERMINAL:
                if (te instanceof TileInterfaceTerminal) {
                    return new GuiFullInterface(player.inventory, (TileInterfaceTerminal) te);
                }
                return null;
            case GUI_COMMON_INTERFACE:
                if (te instanceof com.ae2utilix.block.TileCommonInterfaceAlternate) {
                    return new GuiCommonInterface(player.inventory, (com.ae2utilix.block.TileCommonInterfaceAlternate) te);
                }
                return null;
            default:
                return null;
        }
    }
}
