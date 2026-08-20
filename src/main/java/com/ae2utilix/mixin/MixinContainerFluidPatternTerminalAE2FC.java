package com.ae2utilix.mixin;

import com.ae2utilix.block.terminal.TilePatternTerminal;
import com.ae2utilix.integration.AE2FCRUCompat;
import com.glodblock.github.client.container.ContainerFluidPatternTerminal;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * AE2FC's acceptPattern implementation only forwards JEI data to an AE2FC
 * panel part.  The native container also supports a tile host, so forward
 * the same packet to the block terminal when it is our host.
 */
@Mixin(value = ContainerFluidPatternTerminal.class, remap = false)
public abstract class MixinContainerFluidPatternTerminalAE2FC {

    @Inject(method = "checkHasFluidPattern", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2utilix$checkHasFluidPattern(CallbackInfoReturnable<Boolean> cir) {
        TilePatternTerminal terminal = AE2FCRUCompat.getUtilixPatternTerminal(this);
        if (terminal != null) {
            cir.setReturnValue(!terminal.isCraftingRecipe()
                    && AE2FCRUCompat.hasFluidOrGasPatternContent(terminal));
        }
    }

    @Inject(method = "acceptPattern", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2utilix$acceptPattern(Int2ObjectMap<ItemStack[]> inputs,
                                          List<ItemStack> outputs,
                                          boolean combine,
                                          CallbackInfo ci) {
        TilePatternTerminal terminal = AE2FCRUCompat.getUtilixPatternTerminal(this);
        if (terminal != null) {
            terminal.onChangeCrafting(inputs, outputs, combine);
            ci.cancel();
        }
    }
}
