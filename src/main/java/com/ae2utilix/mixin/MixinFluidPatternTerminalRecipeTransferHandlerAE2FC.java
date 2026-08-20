package com.ae2utilix.mixin;

import com.ae2utilix.integration.AE2FCRUCompat;
import com.glodblock.github.client.container.ContainerFluidPatternTerminal;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import net.minecraft.entity.player.EntityPlayer;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;

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
            boolean craftMode = container.craftingMode;
            try {
                if (container.isCraftingMode()
                        && !recipeLayout.getRecipeCategory().getUid().equals(VanillaRecipeCategoryUid.CRAFTING)) {
                    NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.CraftMode", "0"));
                    craftMode = false;
                } else if (!container.isCraftingMode()
                        && recipeLayout.getRecipeCategory().getUid().equals(VanillaRecipeCategoryUid.CRAFTING)) {
                    NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.CraftMode", "1"));
                    craftMode = true;
                }
            } catch (IOException ignored) {
            }

            AE2FCRUCompat.sendRecipeTransfer(
                    container,
                    container.fluidFirst,
                    container.combine,
                    recipeLayout,
                    craftMode);
        }

        cir.setReturnValue(null);
    }
}
