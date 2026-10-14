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
 * <p>The card is installed in the pattern provider that pushed the crafting inputs. Once a
 * pattern has been pushed, this class periodically looks at the block the provider points at
 * and moves stacks that match the expected outputs into the ME network.</p>
 *
 * <p>Several faces are tried in order. A single position is not always enough: a vanilla
 * furnace, for example, only exposes its input slot through the top face and its output slot
 * through the bottom face. Only stacks matching the expected pattern outputs are ever
 * extracted, so trying extra faces cannot pull unrelated contents such as furnace fuel.</p>
 */
public final class ProductReturnHandler {

    private ProductReturnHandler() {
    }

    /**
     * Extracts one matching stack from the target block into the network.
     *
     * @param gridNode   the provider's grid node, used for network storage and power
     * @param card       the installed product return card
     * @param expected   an item the pushed pattern is expected to produce
     * @param level      the world the provider lives in
     * @param targetPos  the block to pull products from
     * @param faces      candidate capability faces, in the order they should be tried
     * @return true when something was moved into the network
     */
    public static boolean returnProduct(IGridNode gridNode, ItemStack card, ItemStack expected,
                                        World level, BlockPos targetPos, List<EnumFacing> faces) {
        if (gridNode == null || expected.isEmpty() || level == null || targetPos == null) {
            return false;
        }
        if (card.isEmpty() || !(card.getItem() instanceof ItemProductReturnCard)) {
            return false;
        }
        var grid = gridNode.grid();
        if (grid == null) {
            return false;
        }
        if (level.isRemote) {
            return false;
        }
        if (!level.isBlockLoaded(targetPos)) {
            return false;
        }

        TileEntity target = level.getTileEntity(targetPos);
        if (target == null) {
            return false;
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
            if (extractOne(handler, expected, storage, grid, source)) {
                return true;
            }
        }
        return false;
    }

    private static boolean extractOne(IItemHandler handler, ItemStack expected, MEStorage storage,
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
                return true;
            }
        }
        return false;
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