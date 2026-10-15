package com.ae2utilix.ae2_utilix.integration.jei;

import com.ae2utilix.ae2_utilix.AE2Utilix;
import com.ae2utilix.ae2_utilix.config.AE2UtilixConfig;
import com.ae2utilix.ae2_utilix.recipe.CrystalGrowthRecipe;
import com.ae2utilix.ae2_utilix.recipe.CrystalGrowthRecipes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.recipe.IRecipeCategoryRegistration;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI integration.
 *
 * <p>The crystal growth chamber uses its own recipe format rather than the crafting
 * registry, so it needs a category and a catalyst to be discoverable in JEI.</p>
 */
@JEIPlugin
public class JEIUtilixPlugin implements IModPlugin {

    @Override
    public void registerCategories(IRecipeCategoryRegistration registry) {
        if (AE2UtilixConfig.registerCrystalGrowthChamber) {
            registry.addRecipeCategories(
                    new CrystalGrowthRecipeCategory(registry.getJeiHelpers().getGuiHelper()));
        }
    }

    @Override
    public void register(IModRegistry registry) {
        if (!AE2UtilixConfig.registerCrystalGrowthChamber) {
            return;
        }

        List<CrystalGrowthRecipeWrapper> wrappers = new ArrayList<>();
        for (CrystalGrowthRecipe recipe : CrystalGrowthRecipes.getRecipes()) {
            wrappers.add(new CrystalGrowthRecipeWrapper(recipe));
        }
        registry.addRecipes(wrappers, CrystalGrowthRecipeCategory.UID);
        registry.addRecipeCatalyst(new ItemStack(AE2Utilix.BLOCK_CRYSTAL_GROWTH_CHAMBER),
                CrystalGrowthRecipeCategory.UID);
    }
}