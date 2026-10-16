package com.ae2utilix.ae2_utilix.integration.crt;

import com.ae2utilix.ae2_utilix.recipe.CrystalGrowthRecipe;
import com.ae2utilix.ae2_utilix.recipe.CrystalGrowthRecipes;
import crafttweaker.CraftTweakerAPI;
import crafttweaker.IAction;
import crafttweaker.annotations.ModOnly;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.item.IIngredient;
import crafttweaker.api.item.IItemStack;
import crafttweaker.api.liquid.ILiquidStack;
import crafttweaker.api.minecraft.CraftTweakerMC;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * CraftTweaker support for the crystal growth chamber, ported from the Forge build so the
 * scripts keep working unchanged:
 *
 * <pre>
 * mods.ae2_utilix.CrystalGrowthChamber.addRecipe([inputs], fluidInput, [outputs], fluidOutput, time, energy);
 * mods.ae2_utilix.CrystalGrowthChamber.remove(output);
 * mods.ae2_utilix.CrystalGrowthChamber.removeAll();
 * </pre>
 */
@ZenClass("mods.ae2_utilix.CrystalGrowthChamber")
@ZenRegister
@ModOnly("ae2_utilix")
public class CrystalGrowthChamberHandler {

    @ZenMethod
    public static void addRecipe(IIngredient[] inputs,
                                 ILiquidStack fluidInput,
                                 IIngredient[] outputs,
                                 ILiquidStack fluidOutput,
                                 int processingTime, double energyCost) {
        if (processingTime <= 0) {
            CraftTweakerAPI.logError("CrystalGrowthChamber: processingTime must be > 0!");
            return;
        }
        if (energyCost < 0) {
            CraftTweakerAPI.logError("CrystalGrowthChamber: energyCost must be >= 0!");
            return;
        }
        boolean hasItemInput = inputs != null && inputs.length > 0;
        boolean hasFluidInput = fluidInput != null;
        boolean hasItemOutput = outputs != null && outputs.length > 0;
        boolean hasFluidOutput = fluidOutput != null;

        if (!hasItemInput && !hasFluidInput) {
            CraftTweakerAPI.logError("CrystalGrowthChamber: must have at least one input (item or fluid)!");
            return;
        }
        if (!hasItemOutput && !hasFluidOutput) {
            CraftTweakerAPI.logError("CrystalGrowthChamber: must have at least one output (item or fluid)!");
            return;
        }
        CraftTweakerAPI.apply(new ActionAddRecipe(inputs, outputs, processingTime, energyCost, fluidInput, fluidOutput));
    }

    private static ILiquidStack firstLiquidStack(Object[] values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value instanceof ILiquidStack) {
                return (ILiquidStack) value;
            }
        }
        return null;
    }

    /** Compatibility overload for ZenScript array literals, including an empty any[] output. */
    @ZenMethod
    public static void addRecipe(IIngredient[] inputs,
                                 ILiquidStack[] fluidInput,
                                 IIngredient[] outputs,
                                 Object[] fluidOutput,
                                 int processingTime, int energyCost) {
        ILiquidStack input = fluidInput != null && fluidInput.length > 0 ? fluidInput[0] : null;
        addRecipe(inputs, input, outputs, firstLiquidStack(fluidOutput), processingTime, (double) energyCost);
    }

    @ZenMethod
    public static void remove(IItemStack output) {
        if (output == null) {
            CraftTweakerAPI.logError("CrystalGrowthChamber: output cannot be null!");
            return;
        }
        CraftTweakerAPI.apply(new ActionRemoveRecipe(output));
    }

    @ZenMethod
    public static void removeAll() {
        CraftTweakerAPI.apply(new ActionRemoveAll());
    }

    private static class ActionAddRecipe implements IAction {
        private final IIngredient[] inputs;
        private final IIngredient[] outputs;
        private final int processingTime;
        private final double energyCost;
        private final ILiquidStack fluidInput;
        private final ILiquidStack fluidOutput;

        ActionAddRecipe(IIngredient[] inputs, IIngredient[] outputs,
                        int processingTime, double energyCost,
                        ILiquidStack fluidInput, ILiquidStack fluidOutput) {
            this.inputs = inputs;
            this.outputs = outputs;
            this.processingTime = processingTime;
            this.energyCost = energyCost;
            this.fluidInput = fluidInput;
            this.fluidOutput = fluidOutput;
        }

        @Override
        public void apply() {
            List<ItemStack> inputStacks = new ArrayList<>();
            if (inputs != null) {
                for (IIngredient input : inputs) {
                    if (input != null) {
                        IItemStack[] items = input.getItemArray();
                        if (items.length > 0) {
                            inputStacks.add(CraftTweakerMC.getItemStack(items[0]));
                        }
                    }
                }
            }

            List<ItemStack> outputStacks = new ArrayList<>();
            if (outputs != null) {
                for (IIngredient output : outputs) {
                    if (output != null) {
                        IItemStack[] items = output.getItemArray();
                        if (items.length > 0) {
                            outputStacks.add(CraftTweakerMC.getItemStack(items[0]));
                        }
                    }
                }
            }

            FluidStack mcFluidInput = fluidInput != null ? (FluidStack) fluidInput.getInternal() : null;
            FluidStack mcFluidOutput = fluidOutput != null ? (FluidStack) fluidOutput.getInternal() : null;

            CrystalGrowthRecipe recipe = new CrystalGrowthRecipe(
                    inputStacks, mcFluidInput, outputStacks, mcFluidOutput, processingTime, energyCost);
            CrystalGrowthRecipes.addRecipe(recipe);
        }

        @Override
        public String describe() {
            StringBuilder sb = new StringBuilder("Adding CrystalGrowthChamber recipe: ");
            if (outputs != null) {
                for (IIngredient out : outputs) {
                    if (out != null) {
                        IItemStack[] items = out.getItemArray();
                        if (items.length > 0) {
                            sb.append(items[0].getDisplayName()).append(" ");
                        }
                    }
                }
            }
            if (fluidOutput != null) {
                sb.append(fluidOutput.getDisplayName()).append(" ").append(fluidOutput.getAmount()).append("mB ");
            }
            sb.append("[Time: ").append(processingTime).append(", Energy: ").append(energyCost).append("]");
            return sb.toString();
        }
    }

    private static class ActionRemoveRecipe implements IAction {
        private final IItemStack output;

        ActionRemoveRecipe(IItemStack output) {
            this.output = output;
        }

        @Override
        public void apply() {
            ItemStack mcOutput = CraftTweakerMC.getItemStack(output);
            CrystalGrowthRecipes.removeRecipeByOutput(mcOutput);
        }

        @Override
        public String describe() {
            return "Removing CrystalGrowthChamber recipes for " + output.getDisplayName();
        }
    }

    private static class ActionRemoveAll implements IAction {
        @Override
        public void apply() {
            CrystalGrowthRecipes.removeAllRecipes();
        }

        @Override
        public String describe() {
            return "Removing all CrystalGrowthChamber recipes";
        }
    }
}