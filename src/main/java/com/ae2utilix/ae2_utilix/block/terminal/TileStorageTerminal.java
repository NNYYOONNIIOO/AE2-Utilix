package com.ae2utilix.ae2_utilix.block.terminal;

import ae2.container.GuiIds;

/** Block form of ME's storage terminal. */
public class TileStorageTerminal extends TileFullTerminal {

    @Override
    public GuiIds.GuiKey getGuiKey() {
        return GuiIds.GuiKey.ME_STORAGE_TERMINAL;
    }
}