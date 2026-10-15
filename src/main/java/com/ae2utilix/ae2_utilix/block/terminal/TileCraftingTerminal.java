package com.ae2utilix.ae2_utilix.block.terminal;

import ae2.api.inventories.InternalInventory;
import ae2.api.inventories.ISegmentedInventory;
import ae2.container.GuiIds;
import ae2.parts.reporting.CraftingTerminalPart;
import ae2.util.inv.AppEngInternalInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Block form of ME's crafting terminal, including its 3x3 crafting grid. */
public class TileCraftingTerminal extends TileFullTerminal implements ISegmentedInventory {

    private final AppEngInternalInventory craftingGrid = new AppEngInternalInventory(this, 9);

    @Override
    public GuiIds.GuiKey getGuiKey() {
        return GuiIds.GuiKey.CRAFTING_TERMINAL;
    }

    @Nullable
    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (id.equals(CraftingTerminalPart.INV_CRAFTING)) {
            return this.craftingGrid;
        }
        return null;
    }

    @Override
    protected void loadTerminalTag(NBTTagCompound data) {
        this.craftingGrid.readFromNBT(data, "craftingGrid");
    }

    @Override
    protected void saveTerminalTag(NBTTagCompound data) {
        this.craftingGrid.writeToNBT(data, "craftingGrid");
    }
}