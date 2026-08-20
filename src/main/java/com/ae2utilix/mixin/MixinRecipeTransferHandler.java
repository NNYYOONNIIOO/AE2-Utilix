package com.ae2utilix.mixin;

import com.ae2utilix.AE2Utilix;
import com.ae2utilix.gui.ContainerFullPattern;
import com.ae2utilix.integration.AE2FCRUCompat;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;

@Mixin(targets = "appeng.integration.modules.jei.RecipeTransferHandler", remap = false)
public abstract class MixinRecipeTransferHandler<T extends Container> {

    @Inject(method = "transferRecipe", at = @At(value = "INVOKE", target = "Ljava/util/Map;entrySet()Ljava/util/Set;"), cancellable = true, remap = false)
    private void ae2utilix$handleFullPatternMode(@Nonnull T container, IRecipeLayout recipeLayout,
                                                   @Nonnull EntityPlayer player, boolean maxTransfer,
                                                   boolean doTransfer, CallbackInfoReturnable<IRecipeTransferError> cir) {
        if (AE2FCRUCompat.isUtilixFluidPatternContainer(container)) {
            if (doTransfer) {
                AE2FCRUCompat.transferNativeFluidPatternRecipe(container, recipeLayout);
            }
            // Prevent AE2's item-only handler from running after the AE2FC
            // transfer. Its fallback is exactly what loses FluidStack inputs.
            cir.setReturnValue(null);
            return;
        }
        if (container instanceof ContainerFullPattern) {
            ContainerFullPattern cpt = (ContainerFullPattern) container;
            String recipeType = recipeLayout.getRecipeCategory().getUid();
            try {
                if (cpt.isCraftingMode()) {
                    // Currently in crafting mode, switch to processing if not a crafting recipe
                    if (!recipeType.equals(VanillaRecipeCategoryUid.CRAFTING)) {
                        appeng.core.sync.network.NetworkHandler.instance().sendToServer(
                                new appeng.core.sync.packets.PacketValueConfig("PatternTerminal.CraftMode", "0"));
                    }
                } else {
                    // Currently in processing mode, switch to crafting if it's a crafting recipe
                    if (recipeType.equals(VanillaRecipeCategoryUid.CRAFTING)) {
                        appeng.core.sync.network.NetworkHandler.instance().sendToServer(
                                new appeng.core.sync.packets.PacketValueConfig("PatternTerminal.CraftMode", "1"));
                    }
                }
            } catch (IOException e) {
                AE2Utilix.LOGGER.error("Failed to send pattern mode switch packet", e);
            }
        }
    }
}
