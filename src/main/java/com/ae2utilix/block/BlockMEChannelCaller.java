package com.ae2utilix.block;

import appeng.api.implementations.items.IMemoryCard;
import com.ae2utilix.AE2Utilix;
import com.ae2utilix.item.ItemMEChannelCaller;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public class BlockMEChannelCaller extends Block implements ITileEntityProvider {
    public static final PropertyInteger STATUS = PropertyInteger.create("status", 0, 2);
    public static final PropertyBool LISTENER_MODE = PropertyBool.create("listener_mode");

    public BlockMEChannelCaller() {
        super(Material.IRON);
        setCreativeTab(AE2Utilix.AE2_UTILIX_TAB);
        setHardness(3.0F);
        setResistance(8.0F);
        setSoundType(net.minecraft.block.SoundType.METAL);
        setDefaultState(blockState.getBaseState()
                .withProperty(LISTENER_MODE, false)
                .withProperty(STATUS, 0));
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

        TileMEChannelCaller channelCaller = (TileMEChannelCaller) tile;
        if (!held.isEmpty() && held.getItem() instanceof ItemMEChannelCaller) {
            if (!world.isRemote) {
                channelCaller.bindHeldItem(player, held);
            }
            return true;
        }

        if (!held.isEmpty() && held.getItem() instanceof IMemoryCard) {
            if (!world.isRemote) {
                channelCaller.handleMemoryCard(player, held);
            }
            return true;
        }

        if (held.isEmpty()) {
            if (!world.isRemote) {
                boolean listenerMode = !channelCaller.isListener();
                channelCaller.setListenerMode(listenerMode);
                player.sendMessage(new net.minecraft.util.text.TextComponentTranslation(
                        channelCaller.getTranslationPrefix() + ".mode"));
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

    /** Always drop the unified caller item, regardless of the current mode/status. */
    @Override
    public int damageDropped(IBlockState state) {
        return 0;
    }

    @Override
    public ItemStack getItem(World world, BlockPos pos, IBlockState state) {
        Item item = net.minecraft.item.Item.getItemFromBlock(this);
        return createModeStack(item, world, pos, false);
    }

    /**
     * TOP uses Block#getPickBlock for the title shown above its information.
     * The dropped item must remain the ordinary caller item, but the pick
     * stack may carry the listener metadata so TOP can use the listener name
     * as its title while the block is in listener mode.
     */
    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world,
                                  BlockPos pos, EntityPlayer player) {
        Item item = net.minecraft.item.Item.getItemFromBlock(this);
        return createModeStack(item, world, pos, true);
    }

    private ItemStack createModeStack(@Nullable Item item, @Nullable World world,
                                      @Nullable BlockPos pos, boolean includeMode) {
        if (item == null || world == null || pos == null) {
            return ItemStack.EMPTY;
        }
        int metadata = 0;
        if (includeMode) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileMEChannelCaller
                    && ((TileMEChannelCaller) tile).isListener()) {
                metadata = 3;
            }
        }
        return new ItemStack(item, 1, metadata);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        int normalized = Math.max(0, Math.min(5, meta));
        return getDefaultState()
                .withProperty(LISTENER_MODE, normalized >= 3)
                .withProperty(STATUS, normalized % 3);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(STATUS) + (state.getValue(LISTENER_MODE) ? 3 : 0);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, LISTENER_MODE, STATUS);
    }

    @Override
    @Nullable
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileMEChannelCaller) {
            TileMEChannelCaller channelCaller = (TileMEChannelCaller) tile;
            return state.withProperty(LISTENER_MODE, channelCaller.isListener())
                    .withProperty(STATUS, channelCaller.getStatus());
        }
        return state;
    }
}
