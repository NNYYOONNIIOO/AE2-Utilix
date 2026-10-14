package com.ae2utilix.ae2_utilix.block;

import com.ae2utilix.ae2_utilix.AE2Utilix;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Crystal growth chamber block. Uses Supergiant's orientable tile block base so the
 * facing property, block state sync and drop handling stay consistent with AE2.
 */
public class BlockCrystalGrowthChamber extends ae2.block.AEBaseTileBlock<TileCrystalGrowthChamber> {

    private static final AxisAlignedBB AABB_NORTH = new AxisAlignedBB(0.125, 0.0, 0.0, 0.875, 0.875, 1.0);
    private static final AxisAlignedBB AABB_EAST = new AxisAlignedBB(0.0, 0.0, 0.125, 1.0, 0.875, 0.875);

    public BlockCrystalGrowthChamber() {
        super(Material.IRON);
        this.setTileEntity(TileCrystalGrowthChamber.class);
        this.setHardness(3.0f);
        this.setResistance(8.0f);
        this.setCreativeTab(AE2Utilix.AE2_UTILIX_TAB);
    }

    @Override
    public ae2.api.orientation.IOrientationStrategy getOrientationStrategy() {
        return ae2.api.orientation.OrientationStrategies.horizontalFacing();
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullSize() {
        return false;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        TileCrystalGrowthChamber tile = this.getTileEntity(source, pos);
        EnumFacing forward = tile != null ? tile.getForward() : EnumFacing.NORTH;
        return forward.getAxis() == EnumFacing.Axis.X ? AABB_EAST : AABB_NORTH;
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB entityBox,
                                      List<AxisAlignedBB> collidingBoxes, @Nullable Entity entity,
                                      boolean isActualState) {
        addCollisionBoxToList(pos, entityBox, collidingBoxes, this.getBoundingBox(state, world, pos));
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (super.onBlockActivated(world, pos, state, player, hand, facing, hitX, hitY, hitZ)) {
            return true;
        }
        if (world.isRemote) {
            return true;
        }
        TileCrystalGrowthChamber tile = this.getTileEntity(world, pos);
        if (tile != null) {
            tile.activate(player);
        }
        return true;
    }

}