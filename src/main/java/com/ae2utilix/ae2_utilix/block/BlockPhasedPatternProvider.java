package com.ae2utilix.ae2_utilix.block;

import ae2.block.AEBaseTileBlock;
import ae2.block.crafting.PushDirection;
import ae2.api.networking.extensions.GridLogicExtensions;
import ae2.util.InteractionUtil;
import ae2.util.Platform;
import com.ae2utilix.ae2_utilix.AE2Utilix;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
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
 * Block for the phase pattern provider.
 *
 * <p>Mirrors AE2's own pattern provider block (push direction property, wrench rotation,
 * GUI opening) but binds our link-aware tile entity instead.</p>
 */
@SuppressWarnings("deprecation")
public class BlockPhasedPatternProvider extends AEBaseTileBlock<TilePhasedPatternProvider> {

    private static final PushDirection[] PUSH_DIRECTIONS = PushDirection.values();

    public static final PropertyEnum<PushDirection> PUSH_DIRECTION = PropertyEnum.create("push_direction",
            PushDirection.class);

    public BlockPhasedPatternProvider() {
        super(Material.IRON);
        this.setHardness(2.2F);
        this.setResistance(11.0F);
        this.setTileEntity(TilePhasedPatternProvider.class);
        this.setCreativeTab(AE2Utilix.AE2_UTILIX_TAB);
        this.setDefaultState(this.blockState.getBaseState().withProperty(PUSH_DIRECTION, PushDirection.ALL));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return createBlockState(PUSH_DIRECTION);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(PUSH_DIRECTION).ordinal();
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        int index = meta >= 0 && meta < PUSH_DIRECTIONS.length ? meta : PushDirection.ALL.ordinal();
        return this.getDefaultState().withProperty(PUSH_DIRECTION, PUSH_DIRECTIONS[index]);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
        super.neighborChanged(state, world, pos, blockIn, fromPos);
        TilePhasedPatternProvider tile = this.getTileEntity(world, pos);
        if (tile != null) {
            tile.getLogic().invalidateTargetCaches();
            tile.getLogic().updateRedstoneState();
            EnumFacing side = GridLogicExtensions.getNeighborSide(pos, fromPos);
            if (side != null && !world.isRemote) {
                tile.getLogic().onNeighborChanged(side);
            }
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (super.onBlockActivated(world, pos, state, player, hand, facing, hitX, hitY, hitZ)) {
            return true;
        }

        TilePhasedPatternProvider tile = this.getTileEntity(world, pos);
        if (tile == null) {
            return false;
        }

        ItemStack heldItem = player.getHeldItem(hand);
        if (!heldItem.isEmpty() && InteractionUtil.canWrenchRotate(player, heldItem, pos, state)) {
            if (!world.isRemote) {
                this.setSide(world, pos, facing);
            }
            return true;
        }

        if (!world.isRemote) {
            tile.openGui(player, ae2.core.gui.locator.GuiHostLocators.forTile(tile));
        }
        return true;
    }

    public void setSide(World world, BlockPos pos, EnumFacing facing) {
        IBlockState currentState = world.getBlockState(pos);
        EnumFacing pushSide = currentState.getValue(PUSH_DIRECTION).getDirection();

        PushDirection newPushDirection;
        if (pushSide == facing.getOpposite()) {
            newPushDirection = PushDirection.fromDirection(facing);
        } else if (pushSide == facing) {
            newPushDirection = PushDirection.ALL;
        } else if (pushSide == null) {
            newPushDirection = PushDirection.fromDirection(facing.getOpposite());
        } else {
            newPushDirection = PushDirection.fromDirection(Platform.rotateAround(pushSide, facing));
        }

        world.setBlockState(pos, currentState.withProperty(PUSH_DIRECTION, newPushDirection), 3);
        TilePhasedPatternProvider tile = this.getTileEntity(world, pos);
        if (tile != null) {
            tile.getLogic().invalidateTargetCaches();
            tile.onPushDirectionChanged();
        }
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer,
                                ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
    }
}