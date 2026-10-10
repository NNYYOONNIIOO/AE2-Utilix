package com.ae2utilix.integration;

import appeng.util.InventoryAdaptor;
import appeng.util.inv.AdaptorItemHandler;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;

import javax.annotation.Nullable;

public class ExtractFaceHelper {

    @Nullable
    public static IItemHandler getItemHandler(TileEntity te, @Nullable EnumFacing face) {
        if (te == null) return null;

        if (te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face)) {
            IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
            if (handler != null) return handler;
        }

        IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
        if (handler != null) return handler;

        if (face != null && te instanceof ISidedInventory) {
            return new SidedInvWrapper((ISidedInventory) te, face);
        }
        if (te instanceof IInventory) {
            return new InvWrapper((IInventory) te);
        }

        return null;
    }

    @Nullable
    public static InventoryAdaptor getInventoryAdaptor(TileEntity te, @Nullable EnumFacing face) {
        if (te == null) return null;

        InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(te, face);
        if (adaptor != null) return adaptor;

        IItemHandler handler = getItemHandler(te, face);
        return handler == null ? null : new AdaptorItemHandler(handler);
    }

    @Nullable
    public static InventoryAdaptor findOutputAdaptor(TileEntity te, EnumFacing primaryFace,
                                                     ItemStack expectedItem, int amount) {
        InventoryAdaptor primaryAdaptor = getInventoryAdaptor(te, primaryFace);
        if (primaryAdaptor != null && canSimulateExtract(primaryAdaptor, expectedItem, amount)) {
            return primaryAdaptor;
        }

        for (EnumFacing face : EnumFacing.values()) {
            if (face == primaryFace) continue;

            InventoryAdaptor adaptor = getInventoryAdaptor(te, face);
            if (adaptor != null && canSimulateExtract(adaptor, expectedItem, amount)) {
                return adaptor;
            }
        }

        InventoryAdaptor nullAdaptor = getInventoryAdaptor(te, null);
        if (nullAdaptor != null && canSimulateExtract(nullAdaptor, expectedItem, amount)) {
            return nullAdaptor;
        }

        return null;
    }

    public static boolean canSimulateExtract(InventoryAdaptor adaptor, ItemStack expectedItem, int amount) {
        if (adaptor == null || amount <= 0) return false;
        ItemStack simulated = adaptor.simulateRemove(amount, expectedItem, null);
        return simulated != null && !simulated.isEmpty();
    }
}
