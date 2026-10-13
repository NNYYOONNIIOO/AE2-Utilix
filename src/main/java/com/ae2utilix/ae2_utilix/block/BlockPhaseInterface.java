package com.ae2utilix.ae2_utilix.block;

import ae2.block.misc.InterfaceBlock;
import ae2.tile.misc.TileInterface;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Interface block whose tile entity carries an extra link to another block.
 *
 * <p>Supergiant opens interface GUIs through the tile entity itself, so this block only has
 * to swap in the phase aware tile entity and let the base class handle activation.</p>
 */
public class BlockPhaseInterface extends ae2.block.AEBaseTileBlock<TilePhaseInterface> {

    public BlockPhaseInterface() {
        super(net.minecraft.block.material.Material.IRON);
        this.setTileEntity(TilePhaseInterface.class);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (super.onBlockActivated(world, pos, state, player, hand, facing, hitX, hitY, hitZ)) {
            return true;
        }
        // Sneaking is reserved for the coupling staff / memory card interactions.
        if (player.isSneaking()) {
            return false;
        }
        TilePhaseInterface tile = this.getTileEntity(world, pos);
        if (tile != null) {
            if (!world.isRemote) {
                tile.openGui(player, ae2.core.gui.locator.GuiHostLocators.forTile(tile));
            }
            return true;
        }
        return false;
    }

    /** Convenience accessor used by the link and staff code. */
    public TileInterface getInterfaceTile(World world, BlockPos pos) {
        return this.getTileEntity(world, pos);
    }
}