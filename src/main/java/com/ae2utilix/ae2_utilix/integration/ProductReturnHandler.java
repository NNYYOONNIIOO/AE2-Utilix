package com.ae2utilix.ae2_utilix.integration;

import ae2.api.config.Actionable;
import ae2.api.networking.IGridNode;
import ae2.api.networking.security.IActionHost;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEItemKey;
import ae2.api.storage.MEStorage;
import ae2.api.storage.StorageHelper;
import ae2.me.helpers.MachineSource;
import com.ae2utilix.ae2_utilix.item.ItemProductReturnCard;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Pulls finished products back into the network for the product return card.
 *
 * <p>Once a pattern has been pushed, the owner periodically looks at the block its provider
 * points at and moves stacks matching the expected pattern outputs into the ME network.</p>
 *
 * <p>Every side of the target is probed. The product return card is deliberately independent
 * of the phase card: the phase card only chooses which side receives the crafted inputs,
 * while locating the machine's output slot is the return card's own job. A machine may only
 * expose that slot on one side (a vanilla furnace only offers products through its bottom
 * face), so all six sides are tried.</p>
 */
public final class ProductReturnHandler {

    private ProductReturnHandler() {
    }

    /**
     * Moves one matching stack from the target block into the network.
     *
     * @param gridNode  the provider's grid node, used for network storage and power
     * @param card      the installed product return card
     * @param expected  an item the pushed pattern is expected to produce
     * @param level     the world the provider lives in
     * @param targetPos the block to pull products from
     * @param faces     capability faces to probe, in the order they should be tried
     * @return the number of items moved into the network, 0 when nothing was taken
     */
    public static long returnProduct(IGridNode gridNode, ItemStack card, ItemStack expected,
                                     World level, BlockPos targetPos, List<EnumFacing> faces) {
        if (gridNode == null || expected.isEmpty() || level == null || targetPos == null) {
            return 0;
        }
        if (card.isEmpty() || !(card.getItem() instanceof ItemProductReturnCard)) {
            return 0;
        }
        var grid = gridNode.grid();
        if (grid == null) {
            return 0;
        }
        if (level.isRemote || !level.isBlockLoaded(targetPos)) {
            return 0;
        }

        TileEntity target = level.getTileEntity(targetPos);
        if (target == null) {
            return 0;
        }

        MEStorage storage = grid.getStorageService().getInventory();
        IActionSource source = gridNode.getOwner() instanceof IActionHost actionHost
                ? new MachineSource(actionHost)
                : IActionSource.empty();

        for (EnumFacing face : faces) {
            IItemHandler handler = getItemHandler(target, face);
            if (handler == null) {
                continue;
            }
            long moved = extractOne(handler, expected, storage, grid, source);
            if (moved > 0) {
                return moved;
            }
        }
        return 0;
    }

    /**
     * Extracts a matching stack through one handler.
     *
     * <p>Two independent guards keep unrelated contents safe. The capability already
     * restricts which slots this face exposes and whether the machine allows extraction at
     * all (a vanilla furnace reports its fuel and input slots as non-extractable), and the
     * item comparison below only ever accepts stacks that match the expected pattern
     * output.</p>
     */
    private static long extractOne(IItemHandler handler, ItemStack expected, MEStorage storage,
                                   ae2.api.networking.IGrid grid, IActionSource source) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || !matches(stack, expected)) {
                continue;
            }
            ItemStack simulated = handler.extractItem(slot, stack.getCount(), true);
            if (simulated.isEmpty()) {
                continue;
            }
            AEItemKey key = AEItemKey.of(simulated);
            if (key == null) {
                continue;
            }
            long inserted = StorageHelper.poweredInsert(grid.getEnergyService(), storage, key,
                    simulated.getCount(), source, Actionable.MODULATE);
            if (inserted <= 0) {
                continue;
            }
            ItemStack extracted = handler.extractItem(slot, (int) inserted, false);
            if (!extracted.isEmpty()) {
                return extracted.getCount();
            }
        }
        return 0;
    }

    private static boolean matches(ItemStack candidate, ItemStack expected) {
        if (candidate.getItem() != expected.getItem()) {
            return false;
        }
        if (candidate.getItemDamage() != expected.getItemDamage()) {
            return false;
        }
        return ItemStack.areItemStackTagsEqual(candidate, expected);
    }

    @Nullable
    private static IItemHandler getItemHandler(TileEntity target, @Nullable EnumFacing face) {
        Capability<IItemHandler> capability = CapabilityItemHandler.ITEM_HANDLER_CAPABILITY;
        if (target.hasCapability(capability, face)) {
            IItemHandler handler = target.getCapability(capability, face);
            if (handler != null) {
                return handler;
            }
        }
        return target.getCapability(capability, face);
    }
}