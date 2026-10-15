package com.ae2utilix.ae2_utilix.block.terminal;

import ae2.api.config.Settings;
import ae2.api.config.ShowPatternProviders;
import ae2.api.storage.ILinkStatus;
import ae2.api.storage.IPatternAccessTermContainerHost;
import ae2.api.util.IConfigManager;
import ae2.container.GuiIds;

/**
 * Block form of ME's pattern access terminal.
 *
 * <p>Only the container host interface is needed: AE2's own container performs the network
 * lookup, so the block just exposes its node, link status and settings.</p>
 */
public class TilePatternAccessTerminal extends TileFullTerminal implements IPatternAccessTermContainerHost {

    private final IConfigManager patternConfig = IConfigManager.builder(this::saveChanges)
            .registerSetting(Settings.TERMINAL_SHOW_PATTERN_PROVIDERS, ShowPatternProviders.VISIBLE)
            .build();

    @Override
    public GuiIds.GuiKey getGuiKey() {
        return GuiIds.GuiKey.PATTERN_ACCESS_TERMINAL;
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.patternConfig;
    }

    @Override
    public ae2.api.networking.IGridNode getGridNode() {
        return getMainNode().getNode();
    }

    @Override
    public ILinkStatus getLinkStatus() {
        return ILinkStatus.ofManagedNode(getMainNode());
    }
}