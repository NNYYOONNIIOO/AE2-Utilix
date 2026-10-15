package com.ae2utilix.ae2_utilix.block.terminal;

import ae2.block.AEBaseTileBlock;
import com.ae2utilix.ae2_utilix.AE2Utilix;
import com.ae2utilix.ae2_utilix.proxy.CommonProxy;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Shared block class of the block form terminals.
 *
 * <p>The block only carries its facing and opens the terminal GUI; all terminal behaviour
 * lives in the tile entity subclass. Deriving from {@link AEBaseTileBlock} keeps the
 * wrench and memory card handling identical to AE2's own machines.</p>
 */
@SuppressWarnings("deprecation")
public abstract class BlockFullTerminal<T extends TileFullTerminal> extends AEBaseTileBlock<T> {

    public static final PropertyDirection FACING = PropertyDirection.create("facing");

    protected BlockFullTerminal() {
        super(Material.IRON);
        this.setHardness(2.2F);
        this.setResistance(11.0F);
        this.setCreativeTab(AE2Utilix.AE2_UTILIX_TAB);
        this.setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).ordinal();
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        EnumFacing[] values = EnumFacing.values();
        int index = meta >= 0 && meta < values.length ? meta : EnumFacing.NORTH.ordinal();
        return this.getDefaultState().withProperty(FACING, values[index]);
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer,
                                ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        EnumFacing facing = placer.getHorizontalFacing().getOpposite();
        world.setBlockState(pos, state.withProperty(FACING, facing), 2);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (super.onBlockActivated(world, pos, state, player, hand, facing, hitX, hitY, hitZ)) {
            return true;
        }
        if (!world.isRemote) {
            TileFullTerminal tile = this.getTileEntity(world, pos);
            if (tile != null) {
                player.openGui(AE2Utilix.instance(), CommonProxy.GUI_FULL_TERMINAL, world,
                        pos.getX(), pos.getY(), pos.getZ());
            }
        }
        return true;
    }
}