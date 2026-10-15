package com.ae2utilix.ae2_utilix.block.terminal;

/** Block form of this terminal. */
public class BlockStorageTerminal extends BlockFullTerminal<TileStorageTerminal> {

    public BlockStorageTerminal() {
        this.setTileEntity(TileStorageTerminal.class);
    }
}