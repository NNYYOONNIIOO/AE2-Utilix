package com.ae2utilix.gui;

import appeng.api.networking.IGridNode;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IConfigManager;
import appeng.core.sync.GuiBridge;
import appeng.me.helpers.AENetworkProxy;
import appeng.parts.reporting.AbstractPartEncoder;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.util.inv.InvOperation;
import com.ae2utilix.block.terminal.TilePatternTerminal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.items.IItemHandler;

/**
 * Adapter for the native AE2 pattern container. AE2 1.12's
 * ContainerPatternEncoder requires an AbstractPartEncoder, while this mod's
 * pattern terminal is intentionally a TileEntity.
 */
public class NativePatternTerminalHostAdapter extends AbstractPartEncoder {
    private final TilePatternTerminal terminal;

    public NativePatternTerminalHostAdapter(TilePatternTerminal terminal) {
        super(ItemStack.EMPTY);
        this.terminal = terminal;
    }

    public TilePatternTerminal getTerminal() {
        return this.terminal;
    }

    @Override
    public AENetworkProxy getProxy() {
        return this.terminal.getProxy();
    }

    @Override
    public IGridNode getGridNode() {
        return this.terminal.getProxy().getNode();
    }

    @Override
    public IGridNode getGridNode(AEPartLocation direction) {
        return this.terminal.getProxy().getNode();
    }

    @Override
    public IGridNode getActionableNode() {
        return this.terminal.getProxy().getNode();
    }

    @Override
    public TileEntity getTile() {
        return this.terminal;
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this.terminal);
    }

    @Override
    public <T extends IAEStack<T>> IMEMonitor<T> getInventory(IStorageChannel<T> channel) {
        return this.terminal.getInventory(channel);
    }

    @Override
    public IItemHandler getInventoryByName(String name) {
        return this.terminal.getInventoryByName(name);
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.terminal.getConfigManager();
    }

    @Override
    public IItemHandler getViewCellStorage() {
        return this.terminal.getViewCellStorage();
    }

    @Override
    public void saveChanges() {
        this.terminal.saveChanges();
    }

    @Override
    public boolean isCraftingRecipe() {
        return this.terminal.isCraftingRecipe();
    }

    @Override
    public void setCraftingRecipe(boolean crafting) {
        this.terminal.setCraftingRecipe(crafting);
    }

    @Override
    public boolean isSubstitution() {
        return this.terminal.isSubstitute();
    }

    @Override
    public void setSubstitution(boolean substitute) {
        this.terminal.setSubstitute(substitute);
    }

    @Override
    public void onChangeInventory(IItemHandler inventory, int slot, InvOperation operation,
                                  ItemStack removedStack, ItemStack addedStack) {
        this.terminal.onChangeInventory(inventory, slot, operation, removedStack, addedStack);
    }

    @Override
    public GuiBridge getGuiBridge() {
        return GuiBridge.GUI_PATTERN_TERMINAL;
    }

    @Override
    public appeng.api.parts.IPartModel getStaticModels() {
        return PartPatternTerminal.MODELS_OFF;
    }

    @Override
    public GuiBridge getGui(EntityPlayer player) {
        return GuiBridge.GUI_PATTERN_TERMINAL;
    }
}
