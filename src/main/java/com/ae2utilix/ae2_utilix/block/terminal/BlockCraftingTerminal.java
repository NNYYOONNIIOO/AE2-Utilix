package com.ae2utilix.ae2_utilix.block.terminal;

/** Block form of this terminal. */
public class BlockCraftingTerminal extends BlockFullTerminal<TileCraftingTerminal> {

    public BlockCraftingTerminal() {
        this.setTileEntity(TileCraftingTerminal.class);
    }
}