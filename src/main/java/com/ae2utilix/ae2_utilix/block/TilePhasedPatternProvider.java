package com.ae2utilix.ae2_utilix.block;

import ae2.tile.crafting.TilePatternProvider;
import ae2.util.SettingsFrom;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Pattern provider that can be coupled to a remote block with the coupling staff.
 *
 * <p>Everything about patterns, upgrades and pushing is inherited from the stock AE2
 * provider. The link data added here is consumed by {@code MixinPatternProviderLogic},
 * which redirects the push target to the linked block and face.</p>
 */
public class TilePhasedPatternProvider extends TilePatternProvider implements IPhaseLinkHost,
        ae2.tile.ServerTickingTile {

    private static final double MAGNET_IDLE_POWER_MULTIPLIER = 10.0;

    /** Outputs the pushed patterns are expected to leave in the target block. */
    private final java.util.List<net.minecraft.item.ItemStack> ae2utilix$expectedResults =
            new java.util.ArrayList<>();
    /** Increasing retry delay so an empty machine is not polled every tick. */
    private int ae2utilix$returnTicker;
    private int ae2utilix$returnDelay = 1;

    private int ae2utilix$scanTicker;
    private boolean ae2utilix$lastMagnetState;
    private double ae2utilix$baseIdlePower = -1.0;

    /**
     * The stock provider sleeps when it has nothing to push, so upgrades that need
     * periodic work run their own five tick scan from the tile entity itself.
     */
    /**
     * Records the outputs a just-pushed pattern should leave behind, so the product
     * return card knows what to look for in the target block.
     */
    public void rememberExpectedOutputs(ae2.api.crafting.IPatternDetails pattern) {
        if (pattern == null) {
            return;
        }
        for (ae2.api.stacks.GenericStack output : pattern.getOutputs()) {
            if (output == null || !(output.what() instanceof ae2.api.stacks.AEItemKey itemKey)) {
                continue;
            }
            net.minecraft.item.ItemStack stack = itemKey.toStack();
            boolean merged = false;
            for (net.minecraft.item.ItemStack existing : this.ae2utilix$expectedResults) {
                if (net.minecraft.item.ItemStack.areItemsEqual(existing, stack)
                        && net.minecraft.item.ItemStack.areItemStackTagsEqual(existing, stack)) {
                    existing.grow((int) Math.min(Integer.MAX_VALUE - existing.getCount(), output.amount()));
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                stack.setCount((int) Math.min(Integer.MAX_VALUE, Math.max(1, output.amount())));
                this.ae2utilix$expectedResults.add(stack);
            }
        }
        this.ae2utilix$returnTicker = 0;
        this.ae2utilix$returnDelay = 1;
        this.saveChanges();
    }

    /** @return true when the installed upgrades include an active product return card. */
    private boolean ae2utilix$hasReturnCard() {
        for (int i = 0; i < this.getUpgrades().size(); i++) {
            net.minecraft.item.ItemStack stack = this.getUpgrades().getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem()
                    instanceof com.ae2utilix.ae2_utilix.item.ItemProductReturnCard) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void serverTick() {
        if (++this.ae2utilix$scanTicker < com.ae2utilix.ae2_utilix.integration.MachineMagnetHandler.SCAN_INTERVAL) {
            return;
        }
        this.ae2utilix$scanTicker = 0;

        ae2.api.networking.IManagedGridNode mainNode = this.getMainNode();
        ae2.api.networking.IGridNode node = mainNode.getNode();
        boolean hasMagnet = com.ae2utilix.ae2_utilix.integration.MachineMagnetHandler
                .hasMagnetCard(this.getUpgrades());
        // A magnet card multiplies this machine idle draw, matching the original mod.
        if (hasMagnet != this.ae2utilix$lastMagnetState) {
            this.ae2utilix$lastMagnetState = hasMagnet;
            if (node instanceof ae2.me.GridNode gridNode) {
                if (this.ae2utilix$baseIdlePower < 0.0) {
                    this.ae2utilix$baseIdlePower = gridNode.getIdlePowerUsage();
                }
                gridNode.setIdlePowerUsage(hasMagnet
                        ? this.ae2utilix$baseIdlePower * MAGNET_IDLE_POWER_MULTIPLIER
                        : this.ae2utilix$baseIdlePower);
            }
        }
        if (hasMagnet) {
            com.ae2utilix.ae2_utilix.integration.MachineMagnetHandler.doMagnetWork(
                    node, this, this.getUpgrades());
        }

        ae2utilix$tickProductReturn(node);
    }

    /**
     * Polls the push target for products the product return card expects, backing off
     * gradually while the machine has not finished crafting.
     */
    private void ae2utilix$tickProductReturn(ae2.api.networking.IGridNode node) {
        if (!this.ae2utilix$hasReturnCard()) {
            // Without the card the tracked outputs are meaningless, so drop them.
            this.ae2utilix$expectedResults.clear();
            return;
        }
        if (this.ae2utilix$expectedResults.isEmpty()) {
            return;
        }
        if (++this.ae2utilix$returnTicker < this.ae2utilix$returnDelay) {
            return;
        }
        this.ae2utilix$returnTicker = 0;

        net.minecraft.util.math.BlockPos targetPos = this.hasLinkData() && this.isLinkValid()
                ? this.getLinkPos() : null;
        if (targetPos == null) {
            return;
        }

        boolean didWork = false;
        for (net.minecraft.item.ItemStack expected : this.ae2utilix$expectedResults) {
            if (com.ae2utilix.ae2_utilix.integration.ProductReturnHandler.returnProduct(
                    node, this, new net.minecraft.item.ItemStack(
                            com.ae2utilix.ae2_utilix.AE2Utilix.PRODUCT_RETURN_CARD),
                    expected, targetPos, this.getLinkFace())) {
                didWork = true;
                break;
            }
        }

        if (didWork) {
            this.ae2utilix$returnDelay = 1;
        } else {
            this.ae2utilix$returnDelay = Math.min(20,
                    Math.max(1, (int) (this.ae2utilix$returnDelay * 1.15f)));
        }
    }


    private static final String NBT_LINK_DIM = "ae2utilix_link_dim";
    private static final String NBT_LINK_X = "ae2utilix_link_x";
    private static final String NBT_LINK_Y = "ae2utilix_link_y";
    private static final String NBT_LINK_Z = "ae2utilix_link_z";
    private static final String NBT_LINK_FACE = "ae2utilix_link_face";

    private int linkDim = Integer.MIN_VALUE;
    @Nullable
    private BlockPos linkPos;
    @Nullable
    private EnumFacing linkFace;

    @Override
    public void setLinkData(int dim, BlockPos pos, EnumFacing face) {
        this.linkDim = dim;
        this.linkPos = pos;
        this.linkFace = face;
        this.saveChanges();
        this.markForUpdate();
    }

    @Override
    public void clearLinkData() {
        this.linkDim = Integer.MIN_VALUE;
        this.linkPos = null;
        this.linkFace = null;
        this.saveChanges();
        this.markForUpdate();
    }

    @Override
    public boolean hasLinkData() {
        return this.linkPos != null && this.linkFace != null;
    }

    @Nullable
    @Override
    public Integer getLinkDimension() {
        return this.hasLinkData() ? this.linkDim : null;
    }

    @Nullable
    @Override
    public BlockPos getLinkPos() {
        return this.linkPos;
    }

    @Nullable
    @Override
    public EnumFacing getLinkFace() {
        return this.linkFace;
    }

    @Override
    public boolean isLinkValid() {
        if (!this.hasLinkData() || this.getWorld() == null || this.linkPos == null) {
            return false;
        }
        if (this.linkDim != this.getWorld().provider.getDimension()) {
            return false;
        }
        BlockPos self = this.getPos();
        int dx = Math.abs(this.linkPos.getX() - self.getX());
        int dy = Math.abs(this.linkPos.getY() - self.getY());
        int dz = Math.abs(this.linkPos.getZ() - self.getZ());
        return dx <= 16 && dy <= 16 && dz <= 16 && this.getWorld().isBlockLoaded(this.linkPos);
    }

    @Nullable
    @Override
    public String ae2utilix$getTermNameKey() {
        World world = this.getWorld();
        if (world == null || !this.hasLinkData() || !this.isLinkValid()) {
            return null;
        }
        net.minecraft.block.state.IBlockState state = world.getBlockState(this.linkPos);
        net.minecraft.block.Block block = state.getBlock();
        if (block == net.minecraft.init.Blocks.AIR) {
            return null;
        }
        net.minecraft.item.Item item = net.minecraft.item.Item.getItemFromBlock(block);
        if (item != net.minecraft.init.Items.AIR) {
            return new net.minecraft.item.ItemStack(item, 1, block.damageDropped(state)).getTranslationKey()
                    + ".name";
        }
        return block.getTranslationKey() + ".name";
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        if (data.hasKey(NBT_LINK_DIM)) {
            this.linkDim = data.getInteger(NBT_LINK_DIM);
            this.linkPos = new BlockPos(data.getInteger(NBT_LINK_X), data.getInteger(NBT_LINK_Y),
                    data.getInteger(NBT_LINK_Z));
            int ordinal = data.getInteger(NBT_LINK_FACE);
            if (ordinal >= 0 && ordinal < EnumFacing.values().length) {
                this.linkFace = EnumFacing.values()[ordinal];
            }
        }
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        if (this.hasLinkData()) {
            data.setInteger(NBT_LINK_DIM, this.linkDim);
            data.setInteger(NBT_LINK_X, this.linkPos.getX());
            data.setInteger(NBT_LINK_Y, this.linkPos.getY());
            data.setInteger(NBT_LINK_Z, this.linkPos.getZ());
            data.setInteger(NBT_LINK_FACE, this.linkFace.ordinal());
        }
    }

    @Override
    public void exportSettings(SettingsFrom mode, NBTTagCompound output) {
        super.exportSettings(mode, output);
        if (mode == SettingsFrom.MEMORY_CARD && this.hasLinkData()) {
            output.setInteger(NBT_LINK_DIM, this.linkDim);
            output.setInteger(NBT_LINK_X, this.linkPos.getX());
            output.setInteger(NBT_LINK_Y, this.linkPos.getY());
            output.setInteger(NBT_LINK_Z, this.linkPos.getZ());
            output.setInteger(NBT_LINK_FACE, this.linkFace.ordinal());
        }
    }

    @Override
    public void importSettings(SettingsFrom mode, NBTTagCompound input, @Nullable EntityPlayer player) {
        super.importSettings(mode, input, player);
        if (mode == SettingsFrom.MEMORY_CARD && input.hasKey(NBT_LINK_DIM)) {
            this.linkDim = input.getInteger(NBT_LINK_DIM);
            this.linkPos = new BlockPos(input.getInteger(NBT_LINK_X), input.getInteger(NBT_LINK_Y),
                    input.getInteger(NBT_LINK_Z));
            int ordinal = input.getInteger(NBT_LINK_FACE);
            if (ordinal >= 0 && ordinal < EnumFacing.values().length) {
                this.linkFace = EnumFacing.values()[ordinal];
            }
            this.saveChanges();
            this.markForUpdate();
        }
    }
}
