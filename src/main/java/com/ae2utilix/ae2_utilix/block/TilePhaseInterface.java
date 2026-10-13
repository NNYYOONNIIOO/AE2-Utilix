package com.ae2utilix.ae2_utilix.block;

import ae2.api.util.ICustomName;
import ae2.tile.misc.TileInterface;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Interface that can be linked to another block, so the terminal shows the linked block and
 * the coupling staff can re-point it.
 *
 * <p>The Forge build reused ae2uel's {@code TileInterface} plus {@code ICustomNameObject} and
 * {@code InventoryAdaptor}. Supergiant renamed the naming interface to {@link ICustomName} and
 * replaced the inventory adaptor with its own storage API, so the link inspection below uses
 * the target block's item form instead.</p>
 */
public class TilePhaseInterface extends TileInterface implements ICustomName, IPhaseLinkHost {

    private static final String NBT_CUSTOM_NAME = "CustomName";
    private static final String NBT_LINK_DIM = "ae2utilix_link_dim";
    private static final String NBT_LINK_X = "ae2utilix_link_x";
    private static final String NBT_LINK_Y = "ae2utilix_link_y";
    private static final String NBT_LINK_Z = "ae2utilix_link_z";
    private static final String NBT_LINK_FACE = "ae2utilix_link_face";

    @Nullable
    private String customName;
    private int linkDim = Integer.MIN_VALUE;
    @Nullable
    private BlockPos linkPos;
    @Nullable
    private EnumFacing linkFace;

    public TilePhaseInterface() {
        super();
    }

    @Override
    public boolean hasCustomName() {
        return this.customName != null && !this.customName.isEmpty();
    }

    @Nullable
    @Override
    public String getCustomName() {
        String linked = ae2utilix$getLinkedBlockName();
        if (linked != null) {
            return linked;
        }
        return this.customName;
    }

    @Override
    public void setCustomName(@Nullable String customName) {
        this.customName = customName;
        saveChanges();
    }

    /** Display name of the linked block, or null when nothing valid is linked. */
    @Nullable
    public String ae2utilix$getLinkedBlockName() {
        String key = ae2utilix$getTermNameKey();
        if (key == null) {
            return null;
        }
        net.minecraft.util.text.ITextComponent translated =
                new net.minecraft.util.text.TextComponentTranslation(key);
        return translated.getUnformattedText();
    }

    /** Translation key of the linked block, for the terminal listing. */
    @Nullable
    @Override
    public String ae2utilix$getTermNameKey() {
        World world = getWorld();
        if (!hasLinkData() || !isLinkValid() || world == null || this.linkPos == null) {
            return null;
        }
        IBlockState state = world.getBlockState(this.linkPos);
        Block block = state.getBlock();
        if (block == net.minecraft.init.Blocks.AIR) {
            return null;
        }
        Item item = Item.getItemFromBlock(block);
        if (item != net.minecraft.init.Items.AIR && item != null) {
            return new ItemStack(item, 1, block.damageDropped(state)).getTranslationKey() + ".name";
        }
        return block.getTranslationKey() + ".name";
    }

    private ItemStack ae2utilix$pickBlock(IBlockState state, Block block, World world) {
        ItemStack what = new ItemStack(block, 1, block.getMetaFromState(state));
        try {
            double baseX = this.linkPos.getX() + 0.5 + this.linkFace.getXOffset() * 0.501;
            double baseY = this.linkPos.getY() + 0.5 + this.linkFace.getYOffset() * 0.501;
            double baseZ = this.linkPos.getZ() + 0.5 + this.linkFace.getZOffset() * 0.501;
            Vec3d from = new Vec3d(baseX, baseY, baseZ);
            Vec3d to = new Vec3d(baseX + this.linkFace.getXOffset(),
                    baseY + this.linkFace.getYOffset(), baseZ + this.linkFace.getZOffset());
            RayTraceResult hit = world.rayTraceBlocks(from, to, true);
            if (hit != null && this.linkPos.equals(hit.getBlockPos())) {
                ItemStack picked = block.getPickBlock(state, hit, world, this.linkPos, null);
                if (!picked.isEmpty()) {
                    what = picked;
                }
            }
        } catch (Throwable ignored) {
        }
        return what;
    }

    @Override
    public void setLinkData(int dim, BlockPos pos, EnumFacing face) {
        this.linkDim = dim;
        this.linkPos = pos;
        this.linkFace = face;
        saveChanges();
    }

    @Override
    public void clearLinkData() {
        this.linkDim = Integer.MIN_VALUE;
        this.linkPos = null;
        this.linkFace = null;
        saveChanges();
    }

    @Override
    public boolean hasLinkData() {
        return this.linkPos != null && this.linkFace != null;
    }

    @Nullable
    @Override
    public Integer getLinkDimension() {
        return hasLinkData() ? this.linkDim : null;
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
        if (!hasLinkData() || getWorld() == null || this.linkPos == null) {
            return false;
        }
        if (this.linkDim != getWorld().provider.getDimension()) {
            return false;
        }
        BlockPos self = getPos();
        int dx = Math.abs(this.linkPos.getX() - self.getX());
        int dy = Math.abs(this.linkPos.getY() - self.getY());
        int dz = Math.abs(this.linkPos.getZ() - self.getZ());
        return dx <= 16 && dy <= 16 && dz <= 16 && getWorld().isBlockLoaded(this.linkPos);
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.readFromNBT(data);
        if (data.hasKey(NBT_CUSTOM_NAME)) {
            this.customName = data.getString(NBT_CUSTOM_NAME);
        }
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
        super.writeToNBT(data);
        if (this.customName != null) {
            data.setString(NBT_CUSTOM_NAME, this.customName);
        }
        if (hasLinkData()) {
            data.setInteger(NBT_LINK_DIM, this.linkDim);
            data.setInteger(NBT_LINK_X, this.linkPos.getX());
            data.setInteger(NBT_LINK_Y, this.linkPos.getY());
            data.setInteger(NBT_LINK_Z, this.linkPos.getZ());
            data.setInteger(NBT_LINK_FACE, this.linkFace.ordinal());
        }
    }

    /** Kept for signature compatibility with the Forge build's companion code. */
    public BlockPos getEffectiveTargetPos() {
        return hasLinkData() ? this.linkPos.offset(this.linkFace) : null;
    }
}