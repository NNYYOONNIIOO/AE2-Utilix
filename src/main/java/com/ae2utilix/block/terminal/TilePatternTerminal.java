package com.ae2utilix.block.terminal;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IItemList;
import appeng.api.storage.data.IAEItemStack;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.inv.InvOperation;
import com.ae2utilix.integration.AE2FCRUCompat;
import com.ae2utilix.integration.RandomComplementCompat;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

public class TilePatternTerminal extends TileStorageTerminal {

    public static final int PAGE_COUNT = 9;
    public static final int SLOTS_PER_PAGE_INPUT = 9;
    public static final int SLOTS_PER_PAGE_OUTPUT = 3;
    public static final int TOTAL_INPUT_SLOTS = PAGE_COUNT * SLOTS_PER_PAGE_INPUT;  // 81
    public static final int TOTAL_OUTPUT_SLOTS = PAGE_COUNT * SLOTS_PER_PAGE_OUTPUT; // 27

    private static final String NBT_PATTERN = "patternIn";
    private static final String NBT_OUTPUT = "patternOut";
    private static final String NBT_CRAFTING = "craftingGrid";
    private static final String NBT_CRAFTING_RECIPE = "isCraftingRecipe";
    private static final String NBT_SUBSTITUTE = "isSubstitute";
    private static final String NBT_COMBINE = "fc$combine";
    private static final String NBT_FLUID_FIRST = "fc$fluidFirst";

    private final AppEngInternalInventory patternIn = new AppEngInternalInventory(this, 9);
    private final AppEngInternalInventory patternOut = new AppEngInternalInventory(this, TOTAL_OUTPUT_SLOTS);
    private final AppEngInternalInventory craftingGrid = new AppEngInternalInventory(this, TOTAL_INPUT_SLOTS);

    private boolean isCraftingRecipe = true;
    private boolean isSubstitute = false;

    // AE2FCRU fields
    private boolean fc$combine = false;
    private boolean fc$fluidFirst = false;
    private Object rc$configManager;

    public TilePatternTerminal() {
        this.rc$configManager = RandomComplementCompat.createManager((Runnable) this::saveChanges);
        RandomComplementCompat.registerAutoFill(this.rc$configManager);
    }

    public Object getRandomComplementConfigManager() {
        return this.rc$configManager;
    }

    public boolean isCraftingRecipe() {
        return this.isCraftingRecipe;
    }

    public void setCraftingRecipe(boolean craftingRecipe) {
        this.isCraftingRecipe = craftingRecipe;
    }

    public boolean isSubstitute() {
        return this.isSubstitute;
    }

    public void setSubstitute(boolean substitute) {
        this.isSubstitute = substitute;
    }

    // AE2FCRU-compatible methods (was FCFluidPatternPart implementation)
    public boolean getCombineMode() {
        return this.fc$combine;
    }

    public void setCombineMode(boolean mode) {
        this.fc$combine = mode;
        this.saveChanges();
    }

    public boolean getFluidPlaceMode() {
        return this.fc$fluidFirst;
    }

    public void setFluidPlaceMode(boolean mode) {
        this.fc$fluidFirst = mode;
        this.saveChanges();
    }

    public void onChangeCrafting(final Int2ObjectMap<ItemStack[]> inputs, final List<ItemStack> outputs, final boolean combine) {
        if (!AE2FCRUCompat.isLoaded()) return;

        // Clear crafting and output inventories
        AE2FCRUCompat.clearItemInventory(this.craftingGrid);
        AE2FCRUCompat.clearItemInventory(this.patternOut);

        // Get ME network storage list for fuzzy matching
        IItemList<IAEItemStack> storageList = null;
        try {
            appeng.api.storage.IMEMonitor<IAEItemStack> monitor =
                    this.getInventory(appeng.api.AEApi.instance().storage().getStorageChannel(appeng.api.storage.channels.IItemStorageChannel.class));
            if (monitor != null) {
                storageList = monitor.getStorageList();
            }
        } catch (Exception ignored) {
        }

        // Fuzzy-match input candidates against ME network storage
        ItemStack[] fuzzyFind = new ItemStack[AE2FCRUCompat.findMax(inputs.keySet()) + 1];
        for (final int index : inputs.keySet()) {
            final ItemStack[] candidates = inputs.get(index);
            AE2FCRUCompat.fuzzyTransferItems(index, candidates, fuzzyFind, storageList);

            // AE2FC deliberately sends fluids and gases as fake item stacks.
            // They do not occur in the ordinary AE item storage list, so a
            // storage-based fuzzy transfer can leave this slot empty even
            // though JEI supplied a valid resource. Keep the first candidate
            // as a fallback; this also makes item recipes import correctly
            // when their ingredient is not currently stored in the network.
            if (index >= 0 && index < fuzzyFind.length
                    && (fuzzyFind[index] == null || fuzzyFind[index].isEmpty())
                    && candidates != null) {
                for (final ItemStack candidate : candidates) {
                    if (candidate != null && !candidate.isEmpty()) {
                        fuzzyFind[index] = candidate.copy();
                        break;
                    }
                }
            }
        }

        // Compress (merge identical items) if combine is true and in processing mode
        if (combine && !this.isCraftingRecipe) {
            fuzzyFind = AE2FCRUCompat.compress(fuzzyFind);
        }

        // Write matched items into crafting grid
        int bound = Math.min(this.craftingGrid.getSlots(), fuzzyFind.length);
        for (int x = 0; x < bound; x++) {
            final ItemStack item = fuzzyFind[x];
            this.craftingGrid.setStackInSlot(x, item == null ? ItemStack.EMPTY : item);
        }

        // Write outputs
        bound = Math.min(this.patternOut.getSlots(), outputs.size());
        for (int x = 0; x < bound; x++) {
            final ItemStack item = outputs.get(x);
            this.patternOut.setStackInSlot(x, item == null ? ItemStack.EMPTY : item);
        }
    }

    @Override
    public IItemHandler getInventoryByName(String name) {
        if ("pattern".equals(name)) {
            return this.patternIn;
        }
        if ("output".equals(name)) {
            return this.patternOut;
        }
        if ("crafting".equals(name)) {
            return this.craftingGrid;
        }
        return super.getInventoryByName(name);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.patternIn.readFromNBT(data, NBT_PATTERN);
        this.patternOut.readFromNBT(data, NBT_OUTPUT);
        this.craftingGrid.readFromNBT(data, NBT_CRAFTING);
        // Ensure craftingGrid always has TOTAL_INPUT_SLOTS slots,
        // since deserializeNBT may resize it from old NBT data (e.g. Size=9)
        if (this.craftingGrid.getSlots() != TOTAL_INPUT_SLOTS) {
            // Save items from the (possibly smaller) NBT-loaded grid
            int oldSize = this.craftingGrid.getSlots();
            ItemStack[] savedCrafting = new ItemStack[oldSize];
            for (int i = 0; i < oldSize; i++) {
                savedCrafting[i] = this.craftingGrid.getStackInSlot(i);
            }
            this.craftingGrid.setSize(TOTAL_INPUT_SLOTS);
            // Restore items into the expanded grid
            for (int i = 0; i < oldSize; i++) {
                if (savedCrafting[i] != null && !savedCrafting[i].isEmpty()) {
                    this.craftingGrid.setStackInSlot(i, savedCrafting[i]);
                }
            }
        }
        if (this.patternOut.getSlots() != TOTAL_OUTPUT_SLOTS) {
            int oldSize = this.patternOut.getSlots();
            ItemStack[] savedOutput = new ItemStack[oldSize];
            for (int i = 0; i < oldSize; i++) {
                savedOutput[i] = this.patternOut.getStackInSlot(i);
            }
            this.patternOut.setSize(TOTAL_OUTPUT_SLOTS);
            for (int i = 0; i < oldSize; i++) {
                if (savedOutput[i] != null && !savedOutput[i].isEmpty()) {
                    this.patternOut.setStackInSlot(i, savedOutput[i]);
                }
            }
        }
        if (data.hasKey(NBT_CRAFTING_RECIPE)) {
            this.isCraftingRecipe = data.getBoolean(NBT_CRAFTING_RECIPE);
        }
        if (data.hasKey(NBT_SUBSTITUTE)) {
            this.isSubstitute = data.getBoolean(NBT_SUBSTITUTE);
        }
        if (data.hasKey(NBT_COMBINE)) {
            this.fc$combine = data.getBoolean(NBT_COMBINE);
        }
        if (data.hasKey(NBT_FLUID_FIRST)) {
            this.fc$fluidFirst = data.getBoolean(NBT_FLUID_FIRST);
        }
        if (data.hasKey("rcConfig")) {
            RandomComplementCompat.read(this.rc$configManager, data.getCompoundTag("rcConfig"));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        this.patternIn.writeToNBT(data, NBT_PATTERN);
        this.patternOut.writeToNBT(data, NBT_OUTPUT);
        this.craftingGrid.writeToNBT(data, NBT_CRAFTING);
        data.setBoolean(NBT_CRAFTING_RECIPE, this.isCraftingRecipe);
        data.setBoolean(NBT_SUBSTITUTE, this.isSubstitute);
        data.setBoolean(NBT_COMBINE, this.fc$combine);
        data.setBoolean(NBT_FLUID_FIRST, this.fc$fluidFirst);
        NBTTagCompound rcTag = new NBTTagCompound();
        RandomComplementCompat.write(this.rc$configManager, rcTag);
        if (!rcTag.hasNoTags()) {
            data.setTag("rcConfig", rcTag);
        }
        return data;
    }

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc,
                                  ItemStack removedStack, ItemStack addedStack) {
        if (inv == this.patternIn && slot == 1) {
            ItemStack is = this.patternIn.getStackInSlot(1);
            if (!is.isEmpty() && is.getItem() instanceof ICraftingPatternItem) {
                ICraftingPatternItem patternItem = (ICraftingPatternItem) is.getItem();
                try {
                    ICraftingPatternDetails details =
                            patternItem.getPatternForItem(is, this.getWorld());
                    if (details != null) {
                        // Determine the correct inputs to write back to the crafting grid.
                        IAEItemStack[] inputs = details.getInputs();
                        boolean crafting = details.isCraftable();

                        // Check for FluidCraftingPatternDetails via reflection
                        if (AE2FCRUCompat.isFluidCraftingPatternDetails(details)) {
                            IAEItemStack[] originInputs = AE2FCRUCompat.getOriginInputs(details);
                            if (originInputs != null) {
                                inputs = originInputs;
                            }
                            // FluidCraftingPatternDetails.isCraftable() always returns false,
                            // but DENSE_CRAFT_ENCODED_PATTERN should be treated as crafting mode.
                            // Read the 'crafting' flag from NBT directly.
                            NBTTagCompound tag = is.getTagCompound();
                            if (tag != null) {
                                crafting = tag.getBoolean("crafting");
                            }
                        }

                        this.setCraftingRecipe(crafting);
                        this.setSubstitute(details.canSubstitute());

                        for (int x = 0; x < TOTAL_INPUT_SLOTS; x++) {
                            if (x < inputs.length && inputs[x] != null) {
                                this.craftingGrid.setStackInSlot(x, inputs[x].createItemStack());
                            } else {
                                this.craftingGrid.setStackInSlot(x, ItemStack.EMPTY);
                            }
                        }

                        IAEItemStack[] outputs = details.getOutputs();
                        for (int x = 0; x < TOTAL_OUTPUT_SLOTS; x++) {
                            if (x < outputs.length && outputs[x] != null) {
                                this.patternOut.setStackInSlot(x, outputs[x].createItemStack());
                            } else {
                                this.patternOut.setStackInSlot(x, ItemStack.EMPTY);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }
}
