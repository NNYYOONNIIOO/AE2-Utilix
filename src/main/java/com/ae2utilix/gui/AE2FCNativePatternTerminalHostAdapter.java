package com.ae2utilix.gui;

import com.ae2utilix.block.terminal.TilePatternTerminal;
import com.glodblock.github.interfaces.FCFluidPatternPart;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.item.ItemStack;

import java.util.List;

/** AE2FC-specific view of the native block-terminal adapter. */
public final class AE2FCNativePatternTerminalHostAdapter
        extends NativePatternTerminalHostAdapter implements FCFluidPatternPart {

    public AE2FCNativePatternTerminalHostAdapter(TilePatternTerminal terminal) {
        super(terminal);
    }

    @Override
    public boolean getCombineMode() {
        return getTerminal().getCombineMode();
    }

    @Override
    public void setCombineMode(boolean mode) {
        getTerminal().setCombineMode(mode);
    }

    @Override
    public boolean getFluidPlaceMode() {
        return getTerminal().getFluidPlaceMode();
    }

    @Override
    public void setFluidPlaceMode(boolean mode) {
        getTerminal().setFluidPlaceMode(mode);
    }

    @Override
    public void onChangeCrafting(Int2ObjectMap<ItemStack[]> inputs,
                                 List<ItemStack> outputs, boolean combine) {
        getTerminal().onChangeCrafting(inputs, outputs, combine);
    }
}
