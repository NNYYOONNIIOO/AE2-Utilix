package com.ae2utilix.mixin;

import com.ae2utilix.AE2Utilix;
import com.ae2utilix.gui.ContainerFullCrafting;
import com.ae2utilix.gui.ContainerFullPattern;
import com.ae2utilix.integration.AE2FCRUCompat;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import mezz.jei.config.Constants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;

@Mixin(value = appeng.integration.modules.jei.JEIPlugin.class, remap = false)
public class MixinJEIPlugin {

    @Inject(method = "register", at = @At("RETURN"), remap = false)
    private void ae2utilix$registerRecipeTransferHandlers(mezz.jei.api.IModRegistry registry, CallbackInfo ci) {
        try {
            Class<?> handlerClass = Class.forName("appeng.integration.modules.jei.RecipeTransferHandler");
            Constructor<?> ctor = handlerClass.getDeclaredConstructor(Class.class);
            ctor.setAccessible(true);

            // Crafting terminal - same as ContainerCraftingTerm
            Object craftingHandler = ctor.newInstance(ContainerFullCrafting.class);
            registry.getRecipeTransferRegistry().addRecipeTransferHandler(
                    (mezz.jei.api.recipe.transfer.IRecipeTransferHandler) craftingHandler,
                    VanillaRecipeCategoryUid.CRAFTING);

            // AE2's generic handler only serializes ItemStack ingredients. If
            // AE2FC is installed, it would win over FullPatternRecipeTransferHandler
            // and silently drop FluidStack inputs. The project handler uses
            // AE2FC's RecipeTransferBuilder/CPacketLoadPattern instead. Keep
            // the generic handler only when AE2FC is absent.
            if (!AE2FCRUCompat.isLoaded()) {
                Object patternHandler = ctor.newInstance(ContainerFullPattern.class);
                registry.getRecipeTransferRegistry().addRecipeTransferHandler(
                        (mezz.jei.api.recipe.transfer.IRecipeTransferHandler) patternHandler,
                        Constants.UNIVERSAL_RECIPE_TRANSFER_UID);
            }

            AE2Utilix.LOGGER.debug("Registered JEI recipe transfer handlers for block terminals");
        } catch (Exception e) {
            AE2Utilix.LOGGER.warn("Failed to register JEI recipe transfer handlers: " + e.getMessage());
        }
    }
}
