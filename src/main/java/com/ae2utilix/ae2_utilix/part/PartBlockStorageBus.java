package com.ae2utilix.ae2_utilix.part;

import ae2.api.AECapabilities;
import ae2.api.config.Actionable;
import ae2.api.networking.security.IActionSource;
import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.MEStorage;
import ae2.core.AppEng;
import ae2.items.parts.PartModels;
import ae2.parts.PartModel;
import ae2.parts.storagebus.StorageBusPart;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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
 * <p>Unlike AE2's own storage bus, this one also exposes the targeted block itself: the
 * block in front of the bus is reported as a one-item storage, and items inserted while
 * facing a replaceable position are dropped in front of the bus instead of being pushed
 * into a container. Drop entities sitting on the target block are both readable and
 * extractable, which is what makes the bus usable on farms and on plain block faces.</p>
 */
public class PartBlockStorageBus extends StorageBusPart {

    private static final ResourceLocation MODEL_BASE = AppEng.makeId("part/storage_bus_base");

    @PartModels
    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, AppEng.makeId("part/storage_bus_off"));

    @PartModels
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, AppEng.makeId("part/storage_bus_on"));

    @PartModels
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE,
            AppEng.makeId("part/storage_bus_has_channel"));

    /** Storage backed by the faced block plus drops lying on it. */
    @Nullable
    private BlockAndEntityStorage blockStorage;

    public PartBlockStorageBus(IPartItem<?> partItem) {
        super(partItem);
    }

    /**
     * Replaces the target lookup so a block face with no ME storage still yields an
     * inventory: the targeted block and any drops lying on it.
     */
    @Override
    protected void updateTarget(boolean forceFullUpdate) {
        if (isClientSide()) {
            super.updateTarget(forceFullUpdate);
            return;
        }
        net.minecraft.util.EnumFacing side = this.getSide();
        if (side == null) {
            super.updateTarget(forceFullUpdate);
            return;
        }
        BlockPos targetPos = this.getTileEntity().getPos().offset(side);
        World world = getLevel();
        if (world == null || !world.isBlockLoaded(targetPos)) {
            super.updateTarget(forceFullUpdate);
            return;
        }

        // A block that actually offers ME storage keeps the stock behaviour.
        net.minecraft.tileentity.TileEntity adjacent = world.getTileEntity(targetPos);
        if (adjacent != null && adjacent.hasCapability(AECapabilities.ME_STORAGE, side.getOpposite())
                && adjacent.getCapability(AECapabilities.ME_STORAGE, side.getOpposite()) != null) {
            super.updateTarget(forceFullUpdate);
            return;
        }

        if (this.blockStorage == null || !this.blockStorage.matches(world, targetPos)) {
            this.blockStorage = new BlockAndEntityStorage(this, world, targetPos);
        }
        replaceDelegate(this.blockStorage);
        invokeConfigureHandler();
        this.remountStorage();
        useHandlerAsMonitor();
    }

    /** Re-applies the bus filter settings; the stock method is private. */
    private void invokeConfigureHandler() {
        try {
            java.lang.reflect.Method configure = findMethod(this.getClass(), "configureHandler");
            if (configure != null) {
                configure.setAccessible(true);
                configure.invoke(this);
            }
        } catch (ReflectiveOperationException ignored) {
            // Filtering falls back to the last applied configuration.
        }
    }

    @Nullable
    private static java.lang.reflect.Method findMethod(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredMethod(name);
            } catch (NoSuchMethodException ignored) {
                // Keep searching the superclass chain.
            }
        }
        return null;
    }

    /** Swaps the bus delegate. The stock setter is protected, so it is reached directly. */
    private void replaceDelegate(MEStorage storage) {
        try {
            Class<?> declaring = null;
            for (Class<?> type = this.handler.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    declaring = type;
                    type.getDeclaredMethod("setDelegate", MEStorage.class);
                    break;
                } catch (NoSuchMethodException ignored) {
                    // Keep walking up until the class that declares the setter is found.
                }
            }
            if (declaring == null) {
                throw new NoSuchMethodException("setDelegate");
            }
            java.lang.reflect.Method setter = declaring.getDeclaredMethod("setDelegate", MEStorage.class);
            setter.setAccessible(true);
            setter.invoke(this.handler, storage);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not install the block storage bus delegate", error);
        }
    }

    /**
     * The stock bus only polls its delegate while a monitor is installed. Our delegate
     * reads the world directly, so the handler itself becomes the monitor.
     */
    private void useHandlerAsMonitor() {
        try {
            java.lang.reflect.Field monitorField = findField(this.getClass(), "monitor");
            if (monitorField == null) {
                return;
            }
            monitorField.setAccessible(true);
            monitorField.set(this, this.handler);
        } catch (ReflectiveOperationException ignored) {
            // Without the monitor the bus still reports contents; it just will not push
            // incremental updates for drops that appear later.
        }
    }

    @Nullable
    private static java.lang.reflect.Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // Keep searching the superclass chain.
            }
        }
        return null;
    }

    private BlockPos getTargetPos() {
        return this.getTileEntity().getPos().offset(this.getSide());
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

        private KeyCounter collect() {
            KeyCounter out = KeyCounter.saturating();
            IBlockState state = this.world.getBlockState(this.pos);
            Block block = state.getBlock();
            if (!block.isAir(state, this.world, this.pos)) {
                Item item = Item.getItemFromBlock(block);
                if (item != null && item != Items.AIR) {
                    ItemStack stack = new ItemStack(item, 1, block.damageDropped(state));
                    if (!stack.isEmpty()) {
                        AEItemKey key = AEItemKey.of(stack);
                        if (key != null) {
                            out.add(key, 1);
                        }
                    }
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
            return out;
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (!(what instanceof AEItemKey itemKey) || amount <= 0) {
                return 0;
            }
            IBlockState state = this.world.getBlockState(this.pos);
            Block block = state.getBlock();
            boolean replaceable = block.isAir(state, this.world, this.pos)
                    || block.isReplaceable(this.world, this.pos);
            if (!replaceable && state.getMaterial() != Material.AIR) {
                return 0;
            }
            if (mode == Actionable.MODULATE) {
                ItemStack stack = itemKey.toStack((int) Math.min(Integer.MAX_VALUE, amount));
                this.bus.spawnEntityItem(this.world, this.pos, stack);
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
                if (stack.isEmpty() || !matches(what, stack)) {
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

        private static boolean matches(AEKey what, ItemStack stack) {
            AEItemKey key = AEItemKey.of(stack);
            return key != null && key.equals(what);
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            for (var entry : collect()) {
                out.add(entry.getKey(), entry.getLongValue());
            }
        }

        @Override
        public ITextComponent getDescription() {
            return new TextComponentTranslation("item.ae2_utilix.block_storage_bus");
        }
    }
}