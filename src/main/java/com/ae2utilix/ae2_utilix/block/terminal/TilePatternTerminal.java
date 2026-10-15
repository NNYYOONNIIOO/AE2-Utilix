package com.ae2utilix.ae2_utilix.block.terminal;

import ae2.container.GuiIds;
import ae2.helpers.IPatternTerminalGuiHost;
import ae2.helpers.IPatternTerminalLogicHost;
import ae2.parts.encoding.PatternEncodingLogic;
import ae2.util.inv.AppEngInternalInventory;
import ae2.util.inv.InternalInventoryHost;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import java.util.List;

/**
 * Block form of ME's pattern encoding terminal.
 *
 * <p>The block delegates to AE2's own {@link PatternEncodingLogic}, so encoding, the blank
 * pattern slot and the encoded pattern output all behave exactly like the panel form.</p>
 */
public class TilePatternTerminal extends TileFullTerminal
        implements IPatternTerminalLogicHost, IPatternTerminalGuiHost, InternalInventoryHost {

    private final PatternEncodingLogic logic = new PatternEncodingLogic(this);

    @Override
    public GuiIds.GuiKey getGuiKey() {
        return GuiIds.GuiKey.PATTERN_ENCODING_TERMINAL;
    }

    @Override
    public PatternEncodingLogic getLogic() {
        return this.logic;
    }

    @Override
    public World getLevel() {
        return this.world;
    }

    @Override
    public void markForSave() {
        saveChanges();
    }

    @Override
    protected void loadTerminalTag(NBTTagCompound data) {
        this.logic.readFromNBT(data);
    }

    @Override
    protected void saveTerminalTag(NBTTagCompound data) {
        this.logic.writeToNBT(data);
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        saveChanges();
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.logic.getBlankPatternInv().clear();
        this.logic.getEncodedPatternInv().clear();
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops) {
        super.addAdditionalDrops(drops);
        for (var stack : this.logic.getBlankPatternInv()) {
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
        for (var stack : this.logic.getEncodedPatternInv()) {
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
    }
}