package com.ae2utilix.integration.jei;

import com.ae2utilix.AE2Utilix;
import com.ae2utilix.gui.GuiCommonBus;
import com.ae2utilix.gui.GuiCommonInterface;
import com.ae2utilix.recipe.CrystalGrowthRecipe;
import com.ae2utilix.recipe.CrystalGrowthRecipes;
import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.recipe.IRecipeCategoryRegistration;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

@JEIPlugin
public class JEIUtilixPlugin implements IModPlugin {

    private static IJeiRuntime runtime;

    @Override
    public void registerCategories(IRecipeCategoryRegistration registry) {
        if (com.ae2utilix.AE2UtilixConfig.registerCrystalGrowthChamber) {
            registry.addRecipeCategories(new CrystalGrowthRecipeCategory(registry.getJeiHelpers().getGuiHelper()));
        }
    }

    @Override
    public void register(IModRegistry registry) {
        // IJEIGhostIngredients is only a target-provider interface. JEI still
        // needs an explicit handler registration for each GUI class before it
        // will ask those GUIs for drag targets.
        if (com.ae2utilix.AE2UtilixConfig.registerCommonBuses) {
            UtilixGhostIngredientHandler<GuiCommonBus> busGhostHandler =
                    new UtilixGhostIngredientHandler<>(GuiCommonBus.class);
            registry.addAdvancedGuiHandlers(busGhostHandler);
            registry.addGhostIngredientHandler(GuiCommonBus.class, busGhostHandler);
        }
        if (com.ae2utilix.AE2UtilixConfig.registerCommonInterface) {
            UtilixGhostIngredientHandler<GuiCommonInterface> interfaceGhostHandler =
                    new UtilixGhostIngredientHandler<>(GuiCommonInterface.class);
            registry.addAdvancedGuiHandlers(interfaceGhostHandler);
            registry.addGhostIngredientHandler(GuiCommonInterface.class, interfaceGhostHandler);
        }

        if (com.ae2utilix.AE2UtilixConfig.registerCrystalGrowthChamber) {
            registry.addAdvancedGuiHandlers(new CrystalGrowthChamberGuiHandler());

            List<CrystalGrowthRecipeWrapper> wrappers = new ArrayList<>();
            for (CrystalGrowthRecipe recipe : CrystalGrowthRecipes.getRecipes()) {
                wrappers.add(new CrystalGrowthRecipeWrapper(recipe));
            }
            registry.addRecipes(wrappers, CrystalGrowthRecipeCategory.UID);

            registry.addRecipeCatalyst(new ItemStack(AE2Utilix.BLOCK_CRYSTAL_GROWTH_CHAMBER), CrystalGrowthRecipeCategory.UID);

            registry.addRecipeClickArea(com.ae2utilix.gui.GuiCrystalGrowthChamber.class,
                    86, 38, 17, 10,
                    CrystalGrowthRecipeCategory.UID);
        }

    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    public static IJeiRuntime getRuntime() {
        return runtime;
    }
}
