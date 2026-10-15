package com.ae2utilix.ae2_utilix.block.terminal;

import ae2.api.config.Settings;
import ae2.api.config.SortDir;
import ae2.api.config.SortOrder;
import ae2.api.config.ViewItems;
import ae2.api.implementations.blockentities.IViewCellStorage;
import ae2.api.inventories.InternalInventory;
import ae2.api.inventories.ISegmentedInventory;
import ae2.api.networking.IGridNode;
import ae2.api.storage.ILinkStatus;
import ae2.api.storage.ISubGuiHost;
import ae2.api.storage.ITerminalHost;
import ae2.api.storage.MEStorage;
import ae2.api.storage.SupplierStorage;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.IUpgradeableObject;
import ae2.api.upgrades.UpgradeInventories;
import ae2.api.util.IConfigManager;
import ae2.api.util.KeyTypeSelection;
import ae2.api.util.KeyTypeSelectionHost;
import ae2.container.ISubGui;
import ae2.tile.grid.AENetworkedTile;
import ae2.util.inv.AppEngInternalInventory;
import ae2.util.inv.InternalInventoryHost;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Shared block form of ME's storage, crafting, pattern encoding and pattern access
 * terminals.
 *
 * <p>Supergiant only ships the panel form of these terminals. This block entity exposes
 * the exact host interfaces their native containers and screens expect, so the port
 * reuses AE2's terminal GUI and network logic instead of re-implementing it. Subclasses
 * only add the extra inventory a specific terminal needs.</p>
 */
public abstract class TileFullTerminal extends AENetworkedTile
        implements ITerminalHost, IViewCellStorage, InternalInventoryHost, KeyTypeSelectionHost {

    private static final String NBT_VIEW_CELL = "viewCell";
    private static final String NBT_CONFIG = "configManager";
    private static final String NBT_KEY_TYPES = "keyTypeSelection";

    private final AppEngInternalInventory viewCell = new AppEngInternalInventory(this, 5);
    private final IConfigManager configManager = IConfigManager.builder(this::saveChanges)
            .registerSetting(Settings.SORT_BY, SortOrder.NAME)
            .registerSetting(Settings.VIEW_MODE, ViewItems.ALL)
            .registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING)
            .build();
    private final KeyTypeSelection keyTypeSelection =
            new KeyTypeSelection(this::saveChanges, keyType -> true);

    @Override
    public MEStorage getInventory() {
        return new SupplierStorage(() -> {
            var grid = getMainNode().getGrid();
            if (grid != null) {
                return grid.getStorageService().getInventory();
            }
            return null;
        });
    }

    @Nullable
    @Override
    public ae2.api.networking.storage.IStorageService getGridStorageService() {
        var mainNode = getMainNode();
        var grid = mainNode.getGrid();
        return mainNode.isActive() && grid != null ? grid.getStorageService() : null;
    }

    @Override
    public ILinkStatus getLinkStatus() {
        return ILinkStatus.ofManagedNode(getMainNode());
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.configManager;
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return UpgradeInventories.empty();
    }

    @Override
    public InternalInventory getViewCellStorage() {
        return this.viewCell;
    }

    @Override
    public KeyTypeSelection getKeyTypeSelection() {
        return this.keyTypeSelection;
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        saveChanges();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        saveChanges();
    }

    @Override
    public boolean isClientSide() {
        return this.world == null || this.world.isRemote;
    }

    /** GUI this terminal opens. */
    public abstract ae2.container.GuiIds.GuiKey getGuiKey();

    @Override
    public void returnToMainContainer(EntityPlayer player, ISubGui subGui) {
    }

    @Override
    public ItemStack getMainContainerIcon() {
        return new ItemStack(this.getBlockType());
    }


    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        this.configManager.readFromNBT(data.getCompoundTag(NBT_CONFIG));
        this.viewCell.readFromNBT(data, NBT_VIEW_CELL);
        this.keyTypeSelection.readFromNBT(data.getCompoundTag(NBT_KEY_TYPES));
        loadTerminalTag(data);
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        NBTTagCompound config = new NBTTagCompound();
        this.configManager.writeToNBT(config);
        data.setTag(NBT_CONFIG, config);
        this.viewCell.writeToNBT(data, NBT_VIEW_CELL);
        NBTTagCompound keyTypes = new NBTTagCompound();
        this.keyTypeSelection.writeToNBT(keyTypes);
        data.setTag(NBT_KEY_TYPES, keyTypes);
        saveTerminalTag(data);
    }

    /** Hook for subclasses that own extra inventories. */
    protected void loadTerminalTag(NBTTagCompound data) {
    }

    /** Hook for subclasses that own extra inventories. */
    protected void saveTerminalTag(NBTTagCompound data) {
    }
}