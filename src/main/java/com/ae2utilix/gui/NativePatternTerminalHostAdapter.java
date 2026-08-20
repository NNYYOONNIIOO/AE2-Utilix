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
    private static final ThreadLocal<TilePatternTerminal> CONSTRUCTING_TERMINAL = new ThreadLocal<>();
    private final TilePatternTerminal terminal;

    public NativePatternTerminalHostAdapter(TilePatternTerminal terminal) {
        super(ItemStack.EMPTY);
        this.terminal = terminal;
    }

    public static void beginConstruction(TilePatternTerminal terminal) {
        CONSTRUCTING_TERMINAL.set(terminal);
    }

    public static void endConstruction() {
        CONSTRUCTING_TERMINAL.remove();
    }

    private TilePatternTerminal delegate() {
        TilePatternTerminal result = this.terminal;
        if (result == null) {
            result = CONSTRUCTING_TERMINAL.get();
        }
        if (result == null) {
            throw new IllegalStateException("Pattern terminal adapter has no block host");
        }
        return result;
    }

    public TilePatternTerminal getTerminal() {
        return delegate();
    }

    @Override
    public ItemStack getItemStack() {
        TilePatternTerminal terminal = this.terminal;
        return terminal != null ? ItemStack.EMPTY : ItemStack.EMPTY;
    }

    @Override
    public AENetworkProxy getProxy() {
        return delegate().getProxy();
    }

    @Override
    public IGridNode getGridNode() {
        return delegate().getProxy().getNode();
    }

    @Override
    public IGridNode getGridNode(AEPartLocation direction) {
        return delegate().getProxy().getNode();
    }

    @Override
    public IGridNode getActionableNode() {
        return delegate().getProxy().getNode();
    }

    @Override
    public TileEntity getTile() {
        return delegate();
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(delegate());
    }

    @Override
    public <T extends IAEStack<T>> IMEMonitor<T> getInventory(IStorageChannel<T> channel) {
        return delegate().getInventory(channel);
    }

    @Override
    public IItemHandler getInventoryByName(String name) {
        return delegate().getInventoryByName(name);
    }

    @Override
    public IConfigManager getConfigManager() {
        return delegate().getConfigManager();
    }

    @Override
    public IItemHandler getViewCellStorage() {
        return delegate().getViewCellStorage();
    }

    @Override
    public void saveChanges() {
        delegate().saveChanges();
    }

    @Override
    public boolean isCraftingRecipe() {
        return delegate().isCraftingRecipe();
    }

    @Override
    public void setCraftingRecipe(boolean crafting) {
        delegate().setCraftingRecipe(crafting);
    }

    @Override
    public boolean isSubstitution() {
        return delegate().isSubstitute();
    }

    @Override
    public void setSubstitution(boolean substitute) {
        delegate().setSubstitute(substitute);
    }

    @Override
    public void onChangeInventory(IItemHandler inventory, int slot, InvOperation operation,
                                  ItemStack removedStack, ItemStack addedStack) {
        delegate().onChangeInventory(inventory, slot, operation, removedStack, addedStack);
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
