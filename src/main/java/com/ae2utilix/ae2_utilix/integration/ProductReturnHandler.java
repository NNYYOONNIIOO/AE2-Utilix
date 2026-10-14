package com.ae2utilix.ae2_utilix.integration;

import ae2.api.config.Actionable;
import ae2.api.networking.IGridNode;
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

/**
 * Pulls finished products back into the network for the product return card.
 *
 * <p>The card is installed in the pattern provider that pushed the crafting inputs. Once a
 * pattern has been pushed, this class periodically looks at the block the provider points at
 * (the face selected with a phase card, or the block linked with the coupling staff) and
 * moves matching stacks into the ME network.</p>
 */
public final class ProductReturnHandler {

    private ProductReturnHandler() {
    }

    /**
     * Extracts one matching stack from the target block into the network.
     *
     * @param gridNode the provider's grid node, used for network storage and power
     * @param host     the provider's tile entity
     * @param card     the installed product return card, used for nothing but presence
     * @param expected the item that the pushed pattern is expected to produce
     * @return true when something was moved into the network
     */
    public static boolean returnProduct(IGridNode gridNode, TileEntity host, ItemStack card,
                                        ItemStack expected, @Nullable BlockPos overrideTarget,
                                        @Nullable EnumFacing overrideFace) {
        if (gridNode == null || host == null || expected.isEmpty()) {
            return false;
        }
        if (card.isEmpty() || !(card.getItem() instanceof ItemProductReturnCard)) {
            return false;
        }

        World level = host.getWorld();
        if (level == null || level.isRemote) {
            return false;
        }
        var grid = gridNode.grid();
        if (grid == null) {
            return false;
        }

        BlockPos targetPos = overrideTarget;
        EnumFacing extractFace = overrideFace;
        if (targetPos == null) {
            return false;
        }

        TileEntity target = level.getTileEntity(targetPos);
        if (target == null) {
            return false;
        }

        IItemHandler handler = getItemHandler(target, extractFace);
        if (handler == null) {
            return false;
        }

        MEStorage storage = grid.getStorageService().getInventory();
        IActionSource source = gridNode.getOwner() instanceof ae2.api.networking.security.IActionHost actionHost
                ? new MachineSource(actionHost)
                : ae2.api.networking.security.IActionSource.empty();

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