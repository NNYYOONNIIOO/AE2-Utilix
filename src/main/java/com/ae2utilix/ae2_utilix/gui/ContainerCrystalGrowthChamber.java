package com.ae2utilix.ae2_utilix.gui;

import ae2.core.definitions.AEItems;
import com.ae2utilix.ae2_utilix.block.TileCrystalGrowthChamber;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

public class ContainerCrystalGrowthChamber extends Container {

    private final TileCrystalGrowthChamber cgc;

    public ContainerCrystalGrowthChamber(InventoryPlayer ip, TileCrystalGrowthChamber cgc) {
        this.cgc = cgc;

        IItemHandler inputInv = cgc.getInputInv();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlotToContainer(new SlotItemHandler(inputInv, row * 3 + col,
                        GuiCrystalGrowthChamber.item_3_3_9_X + 1 + col * 18,
                        GuiCrystalGrowthChamber.item_3_3_9_Y + 1 + row * 18));
            }
        }

        IItemHandler outputInv = cgc.getOutputInv();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 2; col++) {
                addSlotToContainer(new OutputSlot(outputInv, row * 2 + col,
                        GuiCrystalGrowthChamber.item_3_2_6_X + 1 + col * 18,
                        GuiCrystalGrowthChamber.item_3_2_6_Y + 1 + row * 18));
            }
        }

        IItemHandler upgradeInv = cgc.getUpgradeInv();
        for (int i = 0; i < TileCrystalGrowthChamber.UPGRADE_SLOTS; i++) {
            addSlotToContainer(new SpeedCardSlot(upgradeInv, i,
                    GuiCrystalGrowthChamber.upgrade_slot_X + 8,
                    GuiCrystalGrowthChamber.upgrade_slot_Y + 8 + i * 18));
        }

        bindPlayerInventory(ip);
    }

    private void bindPlayerInventory(InventoryPlayer ip) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(ip, col + row * 9 + 9,
                        GuiCrystalGrowthChamber.PLAYER_INV_X + col * 18,
                        GuiCrystalGrowthChamber.PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(ip, col,
                    GuiCrystalGrowthChamber.PLAYER_INV_X + col * 18,
                    GuiCrystalGrowthChamber.PLAYER_HOTBAR_Y));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        BlockPos pos = cgc.getPos();
        if (player.world.getTileEntity(pos) != cgc) return false;
        return player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = inventorySlots.get(index);

        if (slot != null && slot.getHasStack()) {
            ItemStack itemstack1 = slot.getStack();
            itemstack = itemstack1.copy();

            int inputEnd = TileCrystalGrowthChamber.INPUT_SLOTS;
            int outputEnd = inputEnd + TileCrystalGrowthChamber.OUTPUT_SLOTS;
            int upgradeEnd = outputEnd + TileCrystalGrowthChamber.UPGRADE_SLOTS;

            if (index < inputEnd) {
                if (!mergeItemStack(itemstack1, upgradeEnd, inventorySlots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (index < outputEnd) {
                if (!mergeItemStack(itemstack1, upgradeEnd, inventorySlots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (index < upgradeEnd) {
                if (!mergeItemStack(itemstack1, upgradeEnd, inventorySlots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (isUpgradeCard(itemstack1)) {
                    for (int i = outputEnd; i < upgradeEnd; i++) {
                        Slot upgradeSlot = inventorySlots.get(i);
                        if (!upgradeSlot.getHasStack()) {
                            ItemStack oneCard = itemstack1.copy();
                            oneCard.setCount(1);
                            upgradeSlot.putStack(oneCard);
                            itemstack1.shrink(1);
                            if (itemstack1.getCount() <= 0) {
                                slot.putStack(ItemStack.EMPTY);
                            } else {
                                slot.onSlotChanged();
                            }
                            return itemstack;
                        }
                    }
                    return ItemStack.EMPTY;
                }
                if (!mergeItemStack(itemstack1, 0, inputEnd, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (itemstack1.getCount() == 0) {
                slot.putStack(ItemStack.EMPTY);
            } else {
                slot.onSlotChanged();
            }
        }

        return itemstack;
    }

    private boolean isSpeedCard(ItemStack stack) {
        return AEItems.SPEED_CARD.isSameAs(stack);
    }

    private boolean isUpgradeCard(ItemStack stack) {
        return isSpeedCard(stack);
    }

    public TileCrystalGrowthChamber getCGC() {
        return cgc;
    }

    private static class OutputSlot extends SlotItemHandler {
        OutputSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return false;
        }
    }

    private static class SpeedCardSlot extends SlotItemHandler {
        SpeedCardSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return AEItems.SPEED_CARD.isSameAs(stack);
        }

        @Override
        public int getItemStackLimit(ItemStack stack) {
            return 1;
        }
    }
}
