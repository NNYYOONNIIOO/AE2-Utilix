package com.ae2utilix.ae2_utilix.block;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public interface IPhaseLinkHost {
    void setLinkData(int dimension, BlockPos position, EnumFacing face);

    void clearLinkData();

    boolean hasLinkData();

    @Nullable
    Integer getLinkDimension();

    @Nullable
    BlockPos getLinkPos();

    @Nullable
    EnumFacing getLinkFace();

    boolean isLinkValid();

    @Nullable
    String ae2utilix$getTermNameKey();

    World getWorld();
}
