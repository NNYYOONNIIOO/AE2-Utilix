package com.ae2utilix.ae2_utilix.recipe;

import com.ae2utilix.ae2_utilix.AE2Utilix;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class CrystalGrowthRecipes {

    private static final List<CrystalGrowthRecipe> recipes = new ArrayList<>();
    private static boolean initialized;

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        List<String> recipeFiles = new ArrayList<>();
        // Only resources shipped by this version are loaded. Missing optional files
        // must not make the built-in recipe initialization look broken.
        recipeFiles.add("assets/ae2_utilix/crystal_growth/fluix_crystal.json");

        for (String path : recipeFiles) {
            java.io.InputStream stream = CrystalGrowthRecipes.class.getClassLoader().getResourceAsStream(path);
            if (stream == null) {
                AE2Utilix.LOGGER.warn("Crystal growth recipe resource not found: {}", path);
                continue;
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject json = new Gson().fromJson(reader, JsonObject.class);
                CrystalGrowthRecipe recipe = CrystalGrowthRecipe.fromJson(json);
                recipes.add(recipe);
                AE2Utilix.LOGGER.info("Crystal growth recipe loaded: {} inputs={} fluid={} outputs={} time={} energy={}",
                        path, describe(recipe.getInputs()), recipe.getInputFluid(),
                        describe(recipe.getOutputs()), recipe.getProcessingTime(), recipe.getEnergyCost());
            } catch (Exception e) {
                AE2Utilix.LOGGER.error("Failed to load recipe: " + path, e);
            }
        }
    }

    /** Renders a stack list for the load log, so a half-parsed recipe is visible. */
    private static String describe(List<?> stacks) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < stacks.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(stacks.get(i));
        }
        return out.append(']').toString();
    }

    public static List<CrystalGrowthRecipe> getRecipes() {
        init();
        return recipes;
    }

    public static void addRecipe(CrystalGrowthRecipe recipe) {
        init();
        recipes.add(recipe);
    }

    public static boolean removeRecipeByOutput(ItemStack output) {
        init();
        return recipes.removeIf(recipe -> {
            for (ItemStack out : recipe.getOutputs()) {
                if (ItemStack.areItemsEqual(out, output) && ItemStack.areItemStackTagsEqual(out, output)) {
                    return true;
                }
            }
            return false;
        });
    }

    public static void removeAllRecipes() {
        init();
        recipes.clear();
    }

    public static CrystalGrowthRecipe findMatchingRecipe(IItemHandler inputInv, FluidStack availableFluid, int parallelMultiplier) {
        init();
        parallelMultiplier = Math.max(1, parallelMultiplier);
        CrystalGrowthRecipe bestMatch = null;
        int bestInputCount = -1;
        for (CrystalGrowthRecipe recipe : recipes) {
            if (recipe.matches(inputInv, availableFluid, parallelMultiplier)) {
                int inputCount = recipe.getInputs().size() + (recipe.getInputFluid() != null ? 1 : 0);
                if (inputCount > bestInputCount) {
                    bestInputCount = inputCount;
                    bestMatch = recipe;
                }
            }
        }
        return bestMatch;
    }
}
