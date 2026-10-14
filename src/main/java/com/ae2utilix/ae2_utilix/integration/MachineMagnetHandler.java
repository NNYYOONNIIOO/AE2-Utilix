package com.ae2utilix.ae2_utilix.integration;

import ae2.api.config.Actionable;
import ae2.api.config.FuzzyMode;
import ae2.api.config.IncludeExclude;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEItemKey;
import ae2.api.storage.MEStorage;
import ae2.api.storage.StorageHelper;
import ae2.core.definitions.AEItems;
import ae2.util.ConfigInventory;
import ae2.util.prioritylist.IPartitionList;
import com.ae2utilix.ae2_utilix.AE2Utilix;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Shared magnet behaviour for the ME interface and the pattern provider.
 *
 * <p>While a magnet card sits in the machine's upgrade slots, dropped items within
 * {@link #RANGE} blocks are pulled into the machine's ME network. Which drops are picked up
 * is decided by the card's own pickup filter, honouring the whitelist/blacklist mode and the
 * fuzzy setting stored on the card. The upgrade slot layout is shared with AE2's wireless
 * terminal magnet, which is why the same NBT keys are reused here.</p>
 */
public final class MachineMagnetHandler {

    /** Radius searched around the machine, matching the original mod's 5 block reach. */
    public static final double RANGE = 5.0;

    /** One scan every five ticks, matching the original mod. */
    public static final int SCAN_INTERVAL = 5;

    private static final String TAG_PICKUP_CONFIG = "magnet_pickup_config";
    private static final String TAG_PICKUP_MODE = "magnet_pickup_mode";
    private static final String TAG_FUZZY = "FuzzyMode";

    private MachineMagnetHandler() {
    }

    /** True when the given upgrade inventory holds a magnet card. */
    public static boolean hasMagnetCard(ae2.api.upgrades.IUpgradeInventory upgrades) {
        return !findMagnetCard(upgrades).isEmpty();
    }

    /** The installed magnet card, or an empty stack. */
    public static ItemStack findMagnetCard(ae2.api.upgrades.IUpgradeInventory upgrades) {
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
     * Runs one scan, pulling matching drops into the machine's network.
     *
     * @return true when at least one drop was absorbed.
     */
    public static boolean doMagnetWork(IGridNode gridNode, TileEntity host, ae2.api.upgrades.IUpgradeInventory upgrades) {
        ItemStack card = findMagnetCard(upgrades);
        if (card.isEmpty() || gridNode == null || host == null) {
            return false;
        }
        World world = host.getWorld();
        if (world == null || world.isRemote) {
            return false;
        }

        IGrid grid = gridNode.grid();
        if (grid == null) {
            return false;
        }
        MEStorage storage = grid.getStorageService().getInventory();
        IActionSource source = gridNode.getOwner() instanceof ae2.api.networking.security.IActionHost actionHost
                ? new ae2.me.helpers.MachineSource(actionHost)
                : ae2.api.networking.security.IActionSource.empty();

        MagnetFilter filter = MagnetFilter.of(card);
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
            if (key == null || !filter.matches(key)) {
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

    /** Pickup filter decoded from a magnet card's NBT. */
    private static final class MagnetFilter {
        private final IPartitionList filter;
        private final IncludeExclude mode;

        private MagnetFilter(IPartitionList filter, IncludeExclude mode) {
            this.filter = filter;
            this.mode = mode;
        }

        private static MagnetFilter of(ItemStack card) {
            ConfigInventory config = ConfigInventory.configTypes(27)
                    .supportedType(ae2.api.stacks.AEKeyType.items())
                    .build();
            NBTTagCompound tag = card.getTagCompound();
            if (tag != null) {
                config.readFromChildTag(tag, TAG_PICKUP_CONFIG);
            }

            IPartitionList.Builder builder = IPartitionList.builder();
            builder.fuzzyMode(readFuzzy(tag));
            for (int i = 0; i < config.size(); i++) {
                builder.add(config.getKey(i));
            }

            IncludeExclude mode = tag != null && tag.getBoolean(TAG_PICKUP_MODE)
                    ? IncludeExclude.WHITELIST
                    : IncludeExclude.BLACKLIST;
            return new MagnetFilter(builder.build(), mode);
        }

        private static FuzzyMode readFuzzy(@Nullable NBTTagCompound tag) {
            if (tag != null && tag.hasKey(TAG_FUZZY)) {
                try {
                    return FuzzyMode.valueOf(tag.getString(TAG_FUZZY));
                } catch (IllegalArgumentException ignored) {
                    // Fall through to the default.
                }
            }
            return FuzzyMode.IGNORE_ALL;
        }

        private boolean matches(AEItemKey key) {
            if (filter.isEmpty() && mode == IncludeExclude.WHITELIST) {
                return false;
            }
            return filter.matchesFilter(key, mode);
        }
    }
}