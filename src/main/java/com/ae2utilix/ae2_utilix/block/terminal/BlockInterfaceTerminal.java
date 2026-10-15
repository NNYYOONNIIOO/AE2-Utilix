package com.ae2utilix.ae2_utilix.block.terminal;

/** Block form of this terminal. */
public class BlockInterfaceTerminal extends BlockFullTerminal<TilePatternAccessTerminal> {

    public BlockInterfaceTerminal() {
        this.setTileEntity(TilePatternAccessTerminal.class);
    }
}