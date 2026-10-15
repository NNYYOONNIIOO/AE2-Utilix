package com.ae2utilix.ae2_utilix.part;

import ae2.api.AECapabilities;
import ae2.api.config.Actionable;
import ae2.api.config.IncludeExclude;
import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.networking.security.IActionSource;
import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.IStorageMounts;
import ae2.api.storage.MEStorage;
import ae2.core.definitions.AEItems;
import ae2.core.settings.TickRates;
import ae2.items.parts.PartModels;
import ae2.api.networking.IGridNode;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickingRequest;
import ae2.me.storage.NullInventory;
import ae2.parts.PartModel;
import ae2.parts.storagebus.StorageBusPart;
import ae2.util.prioritylist.IPartitionList;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * ME block storage bus.
 *
 * <p>Behaves like AE2's own storage bus when the faced block offers ME storage, and
 * otherwise exposes the faced block itself plus any items lying in front of it, matching
 * the Forge version of this mod:</p>
 * <ul>
 *   <li>the block itself is reported as a single read-only item,</li>
 *   <li>dropped items in front of the bus can be read and extracted,</li>
 *   <li>inserting an item drops it in front of the bus when that position is replaceable,
 *       and is refused when a full block occupies it.</li>
 * </ul>
 */
public class PartBlockStorageBus extends StorageBusPart {

    /**
     * Models of this mod's own storage bus. The base model is shared with the status
     * variants, exactly like AE2's own storage bus models are structured.
     */
    private static final ResourceLocation MODEL_BASE =
            new ResourceLocation("ae2_utilix", "part/block_storage_bus_base");

    @PartModels
    public static final IPartModel MODELS_OFF =
            new PartModel(MODEL_BASE, new ResourceLocation("ae2_utilix", "part/block_storage_bus_off"));

    @PartModels
    public static final IPartModel MODELS_ON =
            new PartModel(MODEL_BASE, new ResourceLocation("ae2_utilix", "part/block_storage_bus_on"));

    @PartModels
    public static final IPartModel MODELS_HAS_CHANNEL =
            new PartModel(MODEL_BASE, new ResourceLocation("ae2_utilix", "part/block_storage_bus_has_channel"));

    /** Storage backed by the faced block plus drops lying on it. */
    @Nullable
    private BlockAndEntityStorage blockStorage;

    /** Whether the bus delegate currently is {@link #blockStorage} rather than a container. */
    private boolean customDelegate;

    public PartBlockStorageBus(IPartItem<?> partItem) {
        super(partItem);
    }

    /**
     * Uses this mod's own models. The inherited implementation resolves AE2's storage bus
     * models, which is why the block previously rendered as a plain ME storage bus.
     */
    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return MODELS_HAS_CHANNEL;
        } else if (this.isPowered()) {
            return MODELS_ON;
        } else {
            return MODELS_OFF;
        }
    }

    /**
     * Picks the delegate for the current target: a real container keeps AE2's own storage
     * bus behaviour, anything else gets the block plus drops wrapper.
     */
    @Override
    protected void updateTarget(boolean forceFullUpdate) {
        BlockAndEntityStorage custom = resolveCustomStorage();
        if (custom == null) {
            this.customDelegate = false;
            this.blockStorage = null;
            super.updateTarget(forceFullUpdate);
            return;
        }

        boolean changed = !this.customDelegate || this.blockStorage != custom;
        this.blockStorage = custom;
        this.customDelegate = true;
        if (changed) {
            installDelegate(custom);
            configureHandler();
            this.remountStorage();
        }
    }

    /**
     * The stock bus only mounts while its own target validation flag is set, and that flag
     * is only written by the stock target lookup. This override mounts whenever the bus is
     * online with a usable delegate.
     */
    @Override
    public void mountInventories(IStorageMounts mounts) {
        if (!getMainNode().isOnline()) {
            return;
        }
        if (getInternalHandler() instanceof NullInventory) {
            return;
        }
        mounts.mount(this.handler, getPriority());
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!node.isOnline()) {
            return TickRateModulation.SLEEP;
        }
        // Re-resolve every tick: the player may replace the faced block at any time.
        updateTarget(false);
        if (this.customDelegate) {
            return this.handler.onTick();
        }
        return super.tickingRequest(node, ticksSinceLastCall);
    }

    @Override
    public void onNeighborChanged(net.minecraft.world.IBlockAccess level, BlockPos pos, BlockPos neighbor) {
        super.onNeighborChanged(level, pos, neighbor);
        EnumFacing side = getSide();
        if (side != null && !isClientSide() && pos.offset(side).equals(neighbor)) {
            updateTarget(true);
        }
    }

    /**
     * Builds the wrapper for the faced position, or returns null when AE2's own storage
     * lookup should handle the target instead.
     */
    @Nullable
    private BlockAndEntityStorage resolveCustomStorage() {
        if (isClientSide()) {
            return null;
        }
        EnumFacing side = getSide();
        TileEntity self = getTileEntity();
        if (side == null || self == null) {
            return null;
        }
        World world = self.getWorld();
        if (world == null) {
            return null;
        }
        BlockPos targetPos = self.getPos().offset(side);
        if (!world.isBlockLoaded(targetPos)) {
            return null;
        }

        // A block that offers ME storage keeps the stock behaviour.
        TileEntity adjacent = world.getTileEntity(targetPos);
        if (adjacent != null && !adjacent.isInvalid()
                && adjacent.hasCapability(AECapabilities.ME_STORAGE, side.getOpposite())
                && adjacent.getCapability(AECapabilities.ME_STORAGE, side.getOpposite()) != null) {
            return null;
        }

        BlockAndEntityStorage cached = this.blockStorage;
        if (cached != null && cached.matches(world, targetPos)) {
            return cached;
        }
        return new BlockAndEntityStorage(this, world, targetPos);
    }

    /** Installs our wrapper as the bus delegate. The stock setter is protected. */
    private void installDelegate(MEStorage storage) {
        try {
            java.lang.reflect.Method setter = null;
            for (Class<?> type = this.handler.getClass(); type != null && setter == null; type = type.getSuperclass()) {
                try {
                    setter = type.getDeclaredMethod("setDelegate", MEStorage.class);
                } catch (NoSuchMethodException ignored) {
                    // Keep walking up the hierarchy.
                }
            }
            if (setter == null) {
                throw new NoSuchMethodException("setDelegate");
            }
            setter.setAccessible(true);
            setter.invoke(this.handler, storage);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not install the block storage bus delegate", error);
        }
    }

    /** Applies the same access and filter settings the stock bus would apply. */
    private void configureHandler() {
        this.handler.setAccessRestriction(this.getConfigManager().getSetting(Settings.ACCESS));
        this.handler.setWhitelist(isUpgradedWith(AEItems.INVERTER_CARD)
                ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST);
        IPartitionList partitionList = createFilter();
        this.handler.setPartitionList(partitionList);
        this.handler.setReadPartitionFiltering(!partitionList.isEmpty());
        this.handler.setVoidOverflow(isUpgradedWith(AEItems.VOID_CARD));
        this.handler.setSticky(isUpgradedWith(AEItems.STICKY_CARD));
        boolean filterOnExtract = this.getConfigManager().getSetting(Settings.FILTER_ON_EXTRACT) == YesNo.YES;
        this.handler.setExtractFiltering(filterOnExtract, isExtractableOnly() && filterOnExtract);
        this.handler.configurationChanged();
    }

    /** Drops an item stack in front of the bus, mirroring formation plane drop mode. */
    void spawnEntityItem(World world, BlockPos targetPos, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        EntityItem drop = new EntityItem(world,
                targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5, stack.copy());
        drop.setPickupDelay(10);
        world.spawnEntity(drop);
    }

    /** The block item plus any dropped items on the targeted position. */
    private static final class BlockAndEntityStorage implements MEStorage {
        private final PartBlockStorageBus bus;
        private final World world;
        private final BlockPos pos;

        private BlockAndEntityStorage(PartBlockStorageBus bus, World world, BlockPos pos) {
            this.bus = bus;
            this.world = world;
            this.pos = pos;
        }

        private boolean matches(World otherWorld, BlockPos otherPos) {
            return this.world == otherWorld && this.pos.equals(otherPos);
        }

        private ItemStack blockItem() {
            IBlockState state = this.world.getBlockState(this.pos);
            Block block = state.getBlock();
            if (block.isAir(state, this.world, this.pos)) {
                return ItemStack.EMPTY;
            }
            Item item = Item.getItemFromBlock(block);
            if (item == null || item == Items.AIR) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(item, 1, block.damageDropped(state));
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (!(what instanceof AEItemKey itemKey) || amount <= 0) {
                return 0;
            }
            IBlockState state = this.world.getBlockState(this.pos);
            Block block = state.getBlock();
            // A solid, non-replaceable block refuses insertions; anything else accepts them
            // and drops the item in front of the bus.
            boolean occupied = !block.isAir(state, this.world, this.pos)
                    && state.getMaterial() != Material.AIR
                    && state.isNormalCube()
                    && !block.isReplaceable(this.world, this.pos);
            if (occupied) {
                return 0;
            }
            if (mode == Actionable.MODULATE) {
                long remaining = amount;
                while (remaining > 0) {
                    int size = (int) Math.min(remaining, 64);
                    this.bus.spawnEntityItem(this.world, this.pos, itemKey.toStack(size));
                    remaining -= size;
                }
            }
            return amount;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (!(what instanceof AEItemKey) || amount <= 0) {
                return 0;
            }
            long remaining = amount;
            for (EntityItem drop : this.world.getEntitiesWithinAABB(EntityItem.class,
                    new AxisAlignedBB(this.pos))) {
                if (drop == null || drop.isDead || remaining <= 0) {
                    continue;
                }
                ItemStack stack = drop.getItem();
                if (stack.isEmpty() || !AEItemKey.matches(what, stack)) {
                    continue;
                }
                int taken = (int) Math.min(remaining, stack.getCount());
                if (mode == Actionable.MODULATE) {
                    stack.shrink(taken);
                    if (stack.isEmpty()) {
                        drop.setDead();
                    } else {
                        drop.setItem(stack);
                    }
                }
                remaining -= taken;
            }
            return amount - remaining;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            ItemStack blockStack = blockItem();
            if (!blockStack.isEmpty()) {
                AEItemKey key = AEItemKey.of(blockStack);
                if (key != null) {
                    out.add(key, 1);
                }
            }
            for (EntityItem drop : this.world.getEntitiesWithinAABB(EntityItem.class,
                    new AxisAlignedBB(this.pos))) {
                if (drop == null || drop.isDead) {
                    continue;
                }
                ItemStack stack = drop.getItem();
                if (stack.isEmpty()) {
                    continue;
                }
                AEItemKey key = AEItemKey.of(stack);
                if (key != null) {
                    out.add(key, stack.getCount());
                }
            }
        }

        @Override
        public ITextComponent getDescription() {
            return new TextComponentTranslation("item.ae2_utilix.block_storage_bus");
        }
    }
}