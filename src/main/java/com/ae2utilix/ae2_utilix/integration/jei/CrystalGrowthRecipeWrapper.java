package com.ae2utilix.ae2_utilix.integration.jei;

import com.ae2utilix.ae2_utilix.recipe.CrystalGrowthRecipe;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** JEI view of one crystal growth chamber recipe. */
public class CrystalGrowthRecipeWrapper implements IRecipeWrapper {

    private static final int INPUT_SLOTS = 9;
    private static final int OUTPUT_SLOTS = 6;

    private final List<List<ItemStack>> inputs;
    private final List<List<ItemStack>> outputs;
    private final FluidStack inputFluid;
    private final FluidStack outputFluid;
    private final int processingTime;
    private final double energyCost;

    public CrystalGrowthRecipeWrapper(CrystalGrowthRecipe recipe) {
        List<List<ItemStack>> inputLists = new ArrayList<>();
        for (int i = 0; i < INPUT_SLOTS; i++) {
            if (i < recipe.getInputs().size()) {
                inputLists.add(Collections.singletonList(recipe.getInputs().get(i).copy()));
            } else {
                inputLists.add(Collections.emptyList());
            }
        }
        this.inputs = inputLists;

        List<List<ItemStack>> outputLists = new ArrayList<>();
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            if (i < recipe.getOutputs().size()) {
                outputLists.add(Collections.singletonList(recipe.getOutputs().get(i).copy()));
            } else {
                outputLists.add(Collections.emptyList());
            }
        }
        this.outputs = outputLists;

        this.inputFluid = recipe.getInputFluid() != null ? recipe.getInputFluid().copy() : null;
        this.outputFluid = recipe.getOutputFluid() != null ? recipe.getOutputFluid().copy() : null;
        this.processingTime = recipe.getProcessingTime();
        this.energyCost = recipe.getEnergyCost();
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        ingredients.setInputLists(ItemStack.class, this.inputs);
        ingredients.setOutputLists(ItemStack.class, this.outputs);
        if (this.inputFluid != null) {
            ingredients.setInputLists(FluidStack.class,
                    Collections.singletonList(Collections.singletonList(this.inputFluid)));
        }
        if (this.outputFluid != null) {
            ingredients.setOutputLists(FluidStack.class,
                    Collections.singletonList(Collections.singletonList(this.outputFluid)));
        }
    }

    public int getProcessingTime() {
        return this.processingTime;
    }

    public double getEnergyCost() {
        return this.energyCost;
    }
}