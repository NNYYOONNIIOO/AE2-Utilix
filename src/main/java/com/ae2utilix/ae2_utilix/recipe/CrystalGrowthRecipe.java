package com.ae2utilix.ae2_utilix.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.JsonUtils;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

public class CrystalGrowthRecipe {

    private final List<ItemStack> inputs;
    private final FluidStack inputFluid;
    private final List<ItemStack> outputs;
    private final FluidStack outputFluid;
    private final int processingTime;
    private final double energyCost;

    public CrystalGrowthRecipe(List<ItemStack> inputs, FluidStack inputFluid, List<ItemStack> outputs, FluidStack outputFluid, int processingTime, double energyCost) {
        this.inputs = inputs;
        this.inputFluid = inputFluid;
        this.outputs = outputs;
        this.outputFluid = outputFluid;
        this.processingTime = processingTime;
        this.energyCost = energyCost;
    }

    public List<ItemStack> getInputs() { return inputs; }
    public FluidStack getInputFluid() { return inputFluid; }
    public List<ItemStack> getOutputs() { return outputs; }
    public FluidStack getOutputFluid() { return outputFluid; }
    public int getProcessingTime() { return processingTime; }
    public double getEnergyCost() { return energyCost; }
    public double getEnergyPerTick() { return processingTime > 0 ? energyCost / processingTime : 0; }

    public boolean matches(IItemHandler inv, FluidStack availableFluid, int parallelMultiplier) {
        parallelMultiplier = Math.max(1, parallelMultiplier);
        if (inputFluid != null) {
            int requiredAmount = inputFluid.amount * parallelMultiplier;
            if (availableFluid == null || availableFluid.amount < requiredAmount) return false;
            if (!inputFluid.isFluidEqual(availableFluid)) return false;
        }

        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack input : inputs) {
            ItemStack required = input.copy();
            required.setCount(required.getCount() * parallelMultiplier);
            remaining.add(required);
        }

        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack slotStack = inv.getStackInSlot(i);
            if (slotStack.isEmpty()) continue;

            for (int j = 0; j < remaining.size(); j++) {
                ItemStack req = remaining.get(j);
                if (!req.isEmpty() && ItemStack.areItemsEqual(req, slotStack) && ItemStack.areItemStackTagsEqual(req, slotStack)) {
                    int toMatch = Math.min(req.getCount(), slotStack.getCount());
                    req.shrink(toMatch);
                    if (req.getCount() <= 0) {
                        remaining.set(j, ItemStack.EMPTY);
                    }
                    break;
                }
            }
        }

        for (ItemStack req : remaining) {
            if (!req.isEmpty()) return false;
        }

        return true;
    }

    public boolean canFitOutputs(IItemHandler outputInv, int multiplier) {
        multiplier = Math.max(1, multiplier);
        for (ItemStack output : outputs) {
            ItemStack toInsert = output.copy();
            toInsert.setCount(toInsert.getCount() * multiplier);
            for (int i = 0; i < outputInv.getSlots(); i++) {
                ItemStack existing = outputInv.getStackInSlot(i);
                if (existing.isEmpty()) {
                    int max = outputInv.getSlotLimit(i);
                    int canInsert = Math.min(toInsert.getCount(), max);
                    toInsert.shrink(canInsert);
                    if (toInsert.getCount() <= 0) break;
                } else if (ItemStack.areItemsEqual(existing, toInsert) && ItemStack.areItemStackTagsEqual(existing, toInsert)) {
                    int canFit = existing.getMaxStackSize() - existing.getCount();
                    int canInsert = Math.min(toInsert.getCount(), canFit);
                    toInsert.shrink(canInsert);
                    if (toInsert.getCount() <= 0) break;
                }
            }
            if (toInsert.getCount() > 0) return false;
        }
        return true;
    }

    public boolean canFitFluidOutput(FluidTank outputTank, int multiplier) {
        multiplier = Math.max(1, multiplier);
        if (outputFluid == null) return true;
        int required = outputFluid.amount * multiplier;
        FluidStack existing = outputTank.getFluid();
        if (existing == null) return outputTank.getCapacity() >= required;
        if (!existing.isFluidEqual(outputFluid)) return false;
        return outputTank.getCapacity() - existing.amount >= required;
    }

    public void consumeInputs(IItemHandler inv) {
        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack input : inputs) {
            remaining.add(input.copy());
        }

        for (int i = 0; i < inv.getSlots() && !remaining.isEmpty(); i++) {
            ItemStack slotStack = inv.getStackInSlot(i);
            if (slotStack.isEmpty()) continue;

            for (int j = 0; j < remaining.size(); j++) {
                ItemStack req = remaining.get(j);
                if (!req.isEmpty() && ItemStack.areItemsEqual(req, slotStack) && ItemStack.areItemStackTagsEqual(req, slotStack)) {
                    int toExtract = Math.min(req.getCount(), slotStack.getCount());
                    inv.extractItem(i, toExtract, false);
                    req.shrink(toExtract);
                    if (req.getCount() <= 0) {
                        remaining.set(j, ItemStack.EMPTY);
                    }
                    break;
                }
            }
        }
    }

    public void produceOutputs(IItemHandler outputInv) {
        for (ItemStack output : outputs) {
            ItemStack toInsert = output.copy();
            for (int i = 0; i < outputInv.getSlots() && !toInsert.isEmpty(); i++) {
                toInsert = outputInv.insertItem(i, toInsert, false);
            }
        }
    }

    public void produceFluidOutput(FluidTank outputTank) {
        if (outputFluid == null) return;
        outputTank.fill(outputFluid.copy(), true);
    }

    public static CrystalGrowthRecipe fromJson(JsonObject json) {
        List<ItemStack> inputs = new ArrayList<>();
        JsonArray inputsArr = JsonUtils.getJsonArray(json, "inputs");
        for (JsonElement elem : inputsArr) {
            inputs.add(parseItemStack(elem.getAsJsonObject()));
        }

        FluidStack inputFluid = null;
        if (json.has("input_fluid")) {
            inputFluid = parseFluidStack(JsonUtils.getJsonObject(json, "input_fluid"));
        }

        List<ItemStack> outputs = new ArrayList<>();
        JsonArray outputsArr = JsonUtils.getJsonArray(json, "outputs");
        for (JsonElement elem : outputsArr) {
            outputs.add(parseItemStack(elem.getAsJsonObject()));
        }

        FluidStack outputFluid = null;
        if (json.has("output_fluid")) {
            outputFluid = parseFluidStack(JsonUtils.getJsonObject(json, "output_fluid"));
        }

        int processingTime = JsonUtils.getInt(json, "processing_time");
        double energyCost = JsonUtils.getFloat(json, "energy_cost");

        return new CrystalGrowthRecipe(inputs, inputFluid, outputs, outputFluid, processingTime, energyCost);
    }

    private static ItemStack parseItemStack(JsonObject json) {
        String itemId = JsonUtils.getString(json, "item");
        Item item = Item.getByNameOrId(itemId);
        if (item == null) return ItemStack.EMPTY;

        int meta = JsonUtils.getInt(json, "meta", 0);
        int count = JsonUtils.getInt(json, "count", 1);

        ItemStack stack = new ItemStack(item, count, meta);

        if (json.has("nbt")) {
            try {
                NBTTagCompound nbt = JsonToNBT.getTagFromJson(JsonUtils.getString(json, "nbt"));
                stack.setTagCompound(nbt);
            } catch (Exception ignored) {
            }
        }

        return stack;
    }

    private static FluidStack parseFluidStack(JsonObject json) {
        String fluidName = JsonUtils.getString(json, "fluid");
        int amount = JsonUtils.getInt(json, "amount", 1000);

        net.minecraftforge.fluids.Fluid fluid = FluidRegistry.getFluid(fluidName);
        if (fluid == null) return null;

        return new FluidStack(fluid, amount);
    }
}
