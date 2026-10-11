package com.ae2utilix.block;

import appeng.api.implementations.items.IMemoryCard;
import com.ae2utilix.AE2Utilix;
import com.ae2utilix.item.ItemMEChannelListener;
import com.ae2utilix.item.ItemMEChannelCaller;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public class BlockMEChannelCaller extends Block implements ITileEntityProvider {
    public static final PropertyInteger STATUS = PropertyInteger.create("status", 0, 2);

    public BlockMEChannelCaller() {
        super(Material.IRON);
        setCreativeTab(AE2Utilix.AE2_UTILIX_TAB);
        setHardness(3.0F);
        setResistance(8.0F);
        setSoundType(net.minecraft.block.SoundType.METAL);
        setDefaultState(blockState.getBaseState().withProperty(STATUS, 0));
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return true;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileMEChannelCaller();
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing side,
                                    float hitX, float hitY, float hitZ) {
        ItemStack held = player.getHeldItem(hand);
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileMEChannelCaller)) {
            return false;
        }

        if (!held.isEmpty() && held.getItem() instanceof ItemMEChannelCaller) {
            boolean listenerItem = held.getItem() instanceof ItemMEChannelListener;
            if (((TileMEChannelCaller) tile).isListener() != listenerItem) {
                return true;
            }
            if (!world.isRemote) {
                ((TileMEChannelCaller) tile).bindHeldItem(player, held);
            }
            return true;
        }

        if (!held.isEmpty() && held.getItem() instanceof IMemoryCard) {
            if (!world.isRemote) {
                ((TileMEChannelCaller) tile).handleMemoryCard(player, held);
            }
            return true;
        }

        return false;
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                                EntityLivingBase placer, ItemStack stack) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileMEChannelCaller) {
            java.util.UUID id = ItemMEChannelCaller.getChannelId(stack);
            if (id != null) {
                ((TileMEChannelCaller) tile).setChannelId(id);
            }
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileMEChannelCaller) {
            ((TileMEChannelCaller) tile).invalidateCaller();
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(STATUS, Math.max(0, Math.min(2, meta)));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(STATUS);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, STATUS);
    }

    @Override
    @Nullable
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileMEChannelCaller) {
            return state.withProperty(STATUS, ((TileMEChannelCaller) tile).getStatus());
        }
        return state;
    }
}
