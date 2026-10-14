package com.ae2utilix.ae2_utilix.integration;

import ae2.api.config.Actionable;
import ae2.api.networking.IGridNode;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEItemKey;
import ae2.api.storage.MEStorage;
import ae2.api.storage.StorageHelper;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.core.definitions.AEItems;
import ae2.me.helpers.MachineSource;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * Shared magnet behaviour for the ME interface and the pattern provider.
 *
 * <p>Supergiant's magnet card carries no filter of its own: the pickup configuration lives
 * on the wireless terminal that happens to hold the card, so a card installed in a machine
 * has nothing to filter with. This handler therefore picks up everything within
 * {@link #RANGE} blocks, which is also what the original mod did for machines.</p>
 */
public final class MachineMagnetHandler {

    /** Radius searched around the machine. */
    public static final double RANGE = 5.0;

    /** One scan every five ticks. */
    public static final int SCAN_INTERVAL = 5;

    private MachineMagnetHandler() {
    }

    /** True when the given upgrade inventory holds a magnet card. */
    public static boolean hasMagnetCard(IUpgradeInventory upgrades) {
        return !findMagnetCard(upgrades).isEmpty();
    }

    /** The installed magnet card, or an empty stack. */
    public static ItemStack findMagnetCard(IUpgradeInventory upgrades) {
        if (upgrades == null) {
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < upgrades.size(); i++) {
            ItemStack stack = upgrades.getStackInSlot(i);
            if (!stack.isEmpty() && AEItems.MAGNET_CARD.is(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Runs one scan for the machine owning the given node.
     *
     * <p>The scan is driven from the node's owner, so the same call works for tile entity
     * machines and for the cable-mounted parts.</p>
     */
    public static boolean doMagnetWork(IGridNode gridNode) {
        if (gridNode == null) {
            return false;
        }
        Object owner = gridNode.getOwner();
        IUpgradeInventory upgrades = null;
        TileEntity host = null;
        if (owner instanceof ae2.api.upgrades.IUpgradeableObject upgradeable) {
            upgrades = upgradeable.getUpgrades();
        }
        if (owner instanceof TileEntity tile) {
            host = tile;
        } else if (owner instanceof ae2.parts.AEBasePart part) {
            ae2.api.parts.IPartHost partHost = part.getHost();
            host = partHost == null ? null : partHost.getTileEntity();
        }
        if (host == null) {
            return false;
        }
        return doMagnetWork(gridNode, host, upgrades);
    }

    /**
     * Runs one scan, pulling nearby drops into the machine's network.
     *
     * @return true when at least one drop was absorbed.
     */
    public static boolean doMagnetWork(IGridNode gridNode, TileEntity host, IUpgradeInventory upgrades) {
        if (findMagnetCard(upgrades).isEmpty() || gridNode == null || host == null) {
            return false;
        }
        World world = host.getWorld();
        if (world == null || world.isRemote) {
            return false;
        }

        var grid = gridNode.grid();
        if (grid == null) {
            return false;
        }
        MEStorage storage = grid.getStorageService().getInventory();
        IActionSource source = gridNode.getOwner() instanceof ae2.api.networking.security.IActionHost actionHost
                ? new MachineSource(actionHost)
                : ae2.api.networking.security.IActionSource.empty();

        BlockPos pos = host.getPos();
        List<EntityItem> drops = world.getEntitiesWithinAABB(EntityItem.class,
                new AxisAlignedBB(pos.getX() - RANGE, pos.getY() - RANGE, pos.getZ() - RANGE,
                        pos.getX() + RANGE + 1, pos.getY() + RANGE + 1, pos.getZ() + RANGE + 1));

        boolean didWork = false;
        for (EntityItem drop : drops) {
            if (drop == null || drop.isDead) {
                continue;
            }
            NBTTagCompound entityData = drop.getEntityData();
            if (entityData.hasKey("PreventRemoteMovement")) {
                continue;
            }

            ItemStack stack = drop.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            AEItemKey key = AEItemKey.of(stack);
            if (key == null) {
                continue;
            }

            long inserted = StorageHelper.poweredInsert(grid.getEnergyService(), storage, key,
                    stack.getCount(), source, Actionable.MODULATE);
            if (inserted <= 0) {
                continue;
            }
            didWork = true;
            stack.shrink((int) inserted);
            if (stack.isEmpty()) {
                drop.setDead();
            } else {
                drop.setItem(stack);
            }
        }
        return didWork;
    }
}