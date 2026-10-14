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
public class TilePhasedPatternProvider extends TilePatternProvider implements IPhaseLinkHost {

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