package com.ae2utilix.integration;

import appeng.api.config.Actionable;
import appeng.items.tools.powered.powersink.AEBasePoweredItem;
import baubles.api.BaubleType;
import baubles.api.BaublesApi;
import baubles.api.IBauble;
import baubles.api.cap.BaublesCapabilities;
import baubles.api.cap.IBaublesItemHandler;
import com.ae2utilix.item.ItemFluixResonancePivotCore;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nullable;

public class BaublesIntegration {

    public static void init() {
        MinecraftForge.EVENT_BUS.register(BaublesIntegration.class);
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<ItemStack> event) {
        ItemStack stack = event.getObject();
        if (stack.getItem() instanceof ItemFluixResonancePivotCore) {
            if (!stack.hasCapability(BaublesCapabilities.CAPABILITY_ITEM_BAUBLE, null)) {
                event.addCapability(
                        new ResourceLocation("ae2_utilix", "bauble"),
                        new BaubleCapabilityProvider()
                );
            }
        }
    }

    /**
     * Network of a wireless terminal sitting in the player's bauble slots, or null when
     * none of them is a linked terminal. Baubles keeps its own inventory, so the normal
     * player inventory scan cannot see these.
     */
    @Nullable
    public static appeng.api.networking.IGrid findTerminalNetwork(EntityPlayer player) {
        if (player == null) {
            return null;
        }
        try {
            IBaublesItemHandler handler = BaublesApi.getBaublesHandler(player);
            if (handler == null) {
                return null;
            }
            for (int i = 0; i < handler.getSlots(); i++) {
                appeng.api.networking.IGrid grid =
                        WirelessTerminalNetworkLocator.networkOf(handler.getStackInSlot(i));
                if (grid != null) {
                    return grid;
                }
            }
        } catch (Throwable ignored) {
            // A bauble slot that misbehaves simply does not take part in the search.
        }
        return null;
    }

    public static void chargeBaublesInventory(ItemStack coreStack, EntityPlayer player) {
        double available = ((ItemFluixResonancePivotCore) coreStack.getItem()).getAECurrentPower(coreStack);
        if (available <= 0) return;

        IBaublesItemHandler handler = BaublesApi.getBaublesHandler(player);
        for (int i = 0; i < handler.getSlots(); i++) {
            if (available <= 0) break;

            ItemStack target = handler.getStackInSlot(i);
            if (target.isEmpty() || target == coreStack) continue;
            if (!(target.getItem() instanceof AEBasePoweredItem)) continue;

            AEBasePoweredItem poweredItem = (AEBasePoweredItem) target.getItem();
            double targetCurrent = poweredItem.getAECurrentPower(target);
            double targetMax = poweredItem.getAEMaxPower(target);
            double targetNeeded = targetMax - targetCurrent;
            if (targetNeeded <= 0) continue;

            double toInject = Math.min(available, targetNeeded);
            double overflow = poweredItem.injectAEPower(target, toInject, Actionable.MODULATE);
            double actuallyUsed = toInject - overflow;
            if (actuallyUsed > 0) {
                ((ItemFluixResonancePivotCore) coreStack.getItem()).extractAEPower(coreStack, actuallyUsed, Actionable.MODULATE);
                available -= actuallyUsed;
            }
        }
    }

    public static boolean tryEquip(EntityPlayer player, ItemStack heldItem) {
        if (player.world.isRemote) return false;
        IBaublesItemHandler baubles = BaublesApi.getBaublesHandler(player);
        for (int i = 0; i < baubles.getSlots(); i++) {
            if (baubles.getStackInSlot(i).isEmpty() && baubles.isItemValidForSlot(i, heldItem, player)) {
                baubles.setStackInSlot(i, heldItem.copy());
                if (!player.capabilities.isCreativeMode) {
                    player.inventory.setInventorySlotContents(player.inventory.currentItem, ItemStack.EMPTY);
                }
                return true;
            }
        }
        return false;
    }

    private static class BaubleCapabilityProvider implements IBauble, ICapabilityProvider {

        @Override
        public BaubleType getBaubleType(ItemStack itemstack) {
            return BaubleType.TRINKET;
        }

        @Override
        public void onWornTick(ItemStack itemstack, EntityLivingBase player) {
            if (player instanceof EntityPlayer && !player.world.isRemote) {
                if (player.ticksExisted % 10 == 0) {
                    ItemFluixResonancePivotCore.tickCore(itemstack, (EntityPlayer) player);
                }
            }
        }

        @Override
        public boolean canEquip(ItemStack itemstack, EntityLivingBase player) {
            return true;
        }

        @Override
        public boolean canUnequip(ItemStack itemstack, EntityLivingBase player) {
            return true;
        }

        @Override
        public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
            return capability == BaublesCapabilities.CAPABILITY_ITEM_BAUBLE;
        }

        @Override
        public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
            if (capability == BaublesCapabilities.CAPABILITY_ITEM_BAUBLE) {
                return BaublesCapabilities.CAPABILITY_ITEM_BAUBLE.cast(this);
            }
            return null;
        }
    }
}
