package com.ae2utilix.mixin;

import com.ae2utilix.block.terminal.TilePatternTerminal;
import com.glodblock.github.interfaces.FCFluidPatternPart;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

/**
 * Makes the block pattern terminal an AE2FC fluid-pattern host when AE2FC is
 * installed.  The concrete methods already live on TilePatternTerminal; the
 * shadows only expose them through AE2FC's optional interface so its native
 * container and JEI transfer handler can use the block exactly like a fluid
 * pattern terminal part.
 */
@Mixin(value = TilePatternTerminal.class, remap = false)
public abstract class MixinTilePatternTerminalAE2FC implements FCFluidPatternPart {

    @Shadow(remap = false)
    public abstract boolean getCombineMode();

    @Shadow(remap = false)
    public abstract void setCombineMode(boolean mode);

    @Shadow(remap = false)
    public abstract boolean getFluidPlaceMode();

    @Shadow(remap = false)
    public abstract void setFluidPlaceMode(boolean mode);

    @Shadow(remap = false)
    public abstract void onChangeCrafting(Int2ObjectMap<ItemStack[]> inputs,
                                          List<ItemStack> outputs,
                                          boolean combine);
}
