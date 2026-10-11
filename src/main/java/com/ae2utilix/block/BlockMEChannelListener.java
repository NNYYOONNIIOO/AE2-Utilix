package com.ae2utilix.block;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BlockMEChannelListener extends BlockMEChannelCaller {
    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileMEChannelListener();
    }
}
