package com.ae2utilix.ae2_utilix.integration;

import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.IGridNode;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.core.definitions.AEItems;
import com.ae2utilix.ae2_utilix.item.ItemProductReturnCard;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks the outputs a just-pushed pattern is expected to leave behind, and polls the
 * target block for them.
 *
 * <p>Shared by the phase pattern provider and AE2's own pattern providers, so the product
 * return card behaves identically everywhere it is accepted.</p>
 */
public final class ProductReturnTracker {

    /** Increasing retry delay so an idle machine is not polled every tick. */
    private static final int MAX_RETRY_DELAY = 20;

    private final List<ItemStack> expected = new ArrayList<>();
    private int retryTicker;
    private int retryDelay = 1;

    /** Remembers the outputs of a successfully pushed pattern and restarts the polling. */
    public void remember(IPatternDetails pattern) {
        if (pattern == null) {
            return;
        }
        for (GenericStack output : pattern.getOutputs()) {
            if (output == null || !(output.what() instanceof AEItemKey itemKey)) {
                continue;
            }
            ItemStack stack = itemKey.toStack();
            boolean merged = false;
            for (ItemStack existing : this.expected) {
                if (ItemStack.areItemsEqual(existing, stack)
                        && ItemStack.areItemStackTagsEqual(existing, stack)) {
                    existing.grow((int) Math.min(Integer.MAX_VALUE - existing.getCount(), output.amount()));
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                stack.setCount((int) Math.min(Integer.MAX_VALUE, Math.max(1, output.amount())));
                this.expected.add(stack);
            }
        }
        this.retryTicker = 0;
        this.retryDelay = 1;
    }

    /** True when the given upgrade inventory holds a product return card. */
    public static boolean hasReturnCard(IUpgradeInventory upgrades) {
        if (upgrades == null) {
            return false;
        }
        for (int i = 0; i < upgrades.size(); i++) {
            ItemStack stack = upgrades.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof ItemProductReturnCard) {
                return true;
            }
        }
        return false;
    }

    /**
     * Advances the polling by one tick.
     *
     * @param card     a stack of the return card, used to confirm the feature is active
     * @param targetPos where the products should appear
     * @return true when something was moved into the network
     */
    public boolean tick(IGridNode gridNode, ItemStack card, World level, @Nullable BlockPos targetPos) {
        if (!hasCard(card)) {
            // Without the card the tracked outputs are meaningless, so drop them.
            this.expected.clear();
            return false;
        }
        if (this.expected.isEmpty() || gridNode == null || level == null || targetPos == null) {
            return false;
        }
        if (++this.retryTicker < this.retryDelay) {
            return false;
        }
        this.retryTicker = 0;

        boolean didWork = false;
        for (ItemStack expectedStack : this.expected) {
            long moved = ProductReturnHandler.returnProduct(gridNode, card, expectedStack, level,
                    targetPos, allFaces());
            if (moved > 0) {
                expectedStack.shrink((int) Math.min(Integer.MAX_VALUE, moved));
                didWork = true;
                if (expectedStack.getCount() <= 0) {
                    this.expected.remove(expectedStack);
                }
                break;
            }
        }

        if (didWork) {
            this.retryDelay = 1;
        } else {
            this.retryDelay = Math.min(MAX_RETRY_DELAY,
                    Math.max(1, (int) (this.retryDelay * 1.15f)));
        }
        return didWork;
    }

    public boolean isEmpty() {
        return this.expected.isEmpty();
    }

    /**
     * True while outputs are still outstanding.
     *
     * <p>The owner uses this to decide whether it has to stay awake: once every expected
     * output has been collected the tracker has nothing to poll, so the machine may sleep
     * and only the next pattern push needs to wake it again.</p>
     */
    public boolean isWaiting() {
        return !this.expected.isEmpty();
    }

    private static boolean hasCard(ItemStack card) {
        return !card.isEmpty() && card.getItem() instanceof ItemProductReturnCard;
    }

    /** Every side, because the output slot of a machine can be any face. */
    private static List<EnumFacing> allFaces() {
        List<EnumFacing> faces = new ArrayList<>(6);
        for (EnumFacing side : EnumFacing.values()) {
            faces.add(side);
        }
        return faces;
    }
}