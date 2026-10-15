package com.ae2utilix.ae2_utilix.block.terminal;

/** Block form of this terminal. */
public class BlockPatternTerminal extends BlockFullTerminal<TilePatternTerminal> {

    public BlockPatternTerminal() {
        this.setTileEntity(TilePatternTerminal.class);
    }
}