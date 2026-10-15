package com.ae2utilix.ae2_utilix.gui;

import ae2.client.gui.me.common.GuiMEStorage;
import ae2.container.GuiIds;
import ae2.container.me.common.ContainerMEStorage;
import ae2.core.gui.locator.GuiHostLocators;
import com.ae2utilix.ae2_utilix.block.terminal.TileCraftingTerminal;
import com.ae2utilix.ae2_utilix.block.terminal.TileFullTerminal;
import com.ae2utilix.ae2_utilix.block.terminal.TilePatternAccessTerminal;
import com.ae2utilix.ae2_utilix.block.terminal.TilePatternTerminal;
import ae2.client.gui.style.GuiStyleManager;
import net.minecraft.entity.player.EntityPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Opens AE2's native terminal containers and screens for the block form terminals.
 *
 * <p>Each block terminal is a plain tile entity implementing the same host interfaces as
 * the matching AE2 panel, so this class only has to pick the right container and screen
 * pair. That keeps the terminal logic itself in AE2 rather than duplicated here.</p>
 */
public final class FullTerminalGuiHandler {

    private FullTerminalGuiHandler() {
    }

    /** Server side container for the terminal in the given tile. */
    @Nullable
    public static Object createServerContainer(EntityPlayer player, @Nullable TileFullTerminal tile) {
        if (tile == null) {
            return null;
        }
        ae2.container.AEBaseContainer container = createContainer(player, tile);
        if (container == null) {
            return null;
        }
        container.setLocator(GuiHostLocators.forTile(tile));
        container.setGuiTitle(null);
        return container;
    }

    /**
     * Client side screen for a block terminal. The container is rebuilt here because
     * Forge calls this instead of the server's method on the client; AE2's own GUI
     * handler does the same for its part terminals.
     */
    @Nullable
    public static Object createClientGui(EntityPlayer player, @Nullable TileFullTerminal tile) {
        if (tile == null) {
            return null;
        }
        ae2.container.AEBaseContainer container = createContainer(player, tile);
        if (container == null) {
            return null;
        }
        container.setLocator(GuiHostLocators.forTile(tile));
        container.setGuiTitle(null);
        if (tile instanceof TileCraftingTerminal) {
            return new ae2.client.gui.me.items.GuiCraftingTerm(
                    (ae2.container.me.items.ContainerCraftingTerm) container, player.inventory, null,
                    GuiStyleManager.loadStyleDoc("/screens/terminals/crafting_terminal.json"));
        }
        if (tile instanceof TilePatternTerminal) {
            return new ae2.client.gui.me.patternencode.GuiPatternEncodingTerm(
                    (ae2.container.me.patternencode.ContainerPatternEncodingTerm) container, player.inventory, null,
                    GuiStyleManager.loadStyleDoc("/screens/terminals/pattern_encoding_terminal.json"));
        }
        if (tile instanceof TilePatternAccessTerminal) {
            return new ae2.client.gui.me.patternaccess.GuiPatternAccessTerm(
                    (ae2.container.me.patternaccess.ContainerPatternAccessTerm) container, player.inventory, null,
                    GuiStyleManager.loadStyleDoc("/screens/terminals/pattern_access_terminal.json"));
        }
        return meStorageScreen(container, player);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object meStorageScreen(ae2.container.AEBaseContainer container, EntityPlayer player) {
        return new GuiMEStorage((ContainerMEStorage) container, player.inventory, null,
                GuiStyleManager.loadStyleDoc("/screens/terminals/terminal.json"));
    }

    @Nullable
    private static ae2.container.AEBaseContainer createContainer(EntityPlayer player, TileFullTerminal tile) {
        if (tile instanceof TileCraftingTerminal) {
            return new ae2.container.me.items.ContainerCraftingTerm(player.inventory, (TileCraftingTerminal) tile);
        }
        if (tile instanceof TilePatternTerminal) {
            return new ae2.container.me.patternencode.ContainerPatternEncodingTerm(
                    player.inventory, (TilePatternTerminal) tile);
        }
        if (tile instanceof TilePatternAccessTerminal) {
            return new ae2.container.me.patternaccess.ContainerPatternAccessTerm(
                    player.inventory, (TilePatternAccessTerminal) tile);
        }
        return new ContainerMEStorage(GuiIds.GuiKey.ME_STORAGE_TERMINAL, player.inventory, tile);
    }
}