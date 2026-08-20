package com.ae2utilix.mixin;

import com.ae2utilix.integration.AE2FCRUCompat;
import com.glodblock.github.client.container.ContainerFluidPatternTerminal;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * AE2FC's handler deliberately ignores containers whose host is not an
 * FCFluidPatternPart.  The block terminal is adapted to that interface, but
 * ContainerPatternTerm.getPart() still returns null for a tile, so handle the
 * block case before AE2FC's original guard runs.
 */
@Mixin(value = com.glodblock.github.integration.jei.FluidPatternTerminalRecipeTransferHandler.class, remap = false)
public abstract class MixinFluidPatternTerminalRecipeTransferHandlerAE2FC {

    @Inject(method = "transferRecipe", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2utilix$transferRecipe(
            @Nonnull ContainerFluidPatternTerminal container,
            @Nonnull IRecipeLayout recipeLayout,
            @Nonnull EntityPlayer player,
            boolean maxTransfer,
            boolean doTransfer,
            @Nullable CallbackInfoReturnable<IRecipeTransferError> cir) {
        if (AE2FCRUCompat.getUtilixPatternTerminal(container) == null) {
            return;
        }

        if (doTransfer) {
            AE2FCRUCompat.transferNativeFluidPatternRecipe(container, recipeLayout);
        }

        cir.setReturnValue(null);
    }
}
