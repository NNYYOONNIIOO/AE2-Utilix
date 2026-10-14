package com.ae2utilix.ae2_utilix.integration;

import com.ae2utilix.ae2_utilix.AE2Utilix;
import com.ae2utilix.ae2_utilix.block.BlockMEChannelCaller;
import com.ae2utilix.ae2_utilix.block.TileMEChannelCaller;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ITheOneProbe;
import mcjty.theoneprobe.api.ProbeMode;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Function;

/** Optional TOP provider registered through the standard IMC callback. */
public class MEChannelCallerTOPIntegration implements Function<ITheOneProbe, Void>, IProbeInfoProvider {
    @Override
    public Void apply(@Nullable ITheOneProbe probe) {
        if (probe != null) probe.registerProvider(this);
        return null;
    }

    @Override
    public String getID() {
        return AE2Utilix.MODID + ":me_channel_caller";
    }

    @Override
    public void addProbeInfo(ProbeMode mode, IProbeInfo probeInfo, EntityPlayer player, World world,
                             IBlockState blockState, IProbeHitData data) {
        if (!(blockState.getBlock() instanceof BlockMEChannelCaller)) return;
        BlockPos pos = data.getPos();
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileMEChannelCaller)) return;
        TileMEChannelCaller caller = (TileMEChannelCaller) tile;
        UUID id = caller.getChannelId();
        // Keep the UUID as an explicit TOP line.  The mode-specific item
        // name is supplied by Block#getPickBlock, so it belongs in TOP's
        // title rather than being duplicated as a status/tooltip line.
        if (id == null) {
            probeInfo.text(new TextComponentTranslation(
                    "item.ae2_utilix.me_channel_caller.tooltip.unbound"));
        } else {
            probeInfo.text(new TextComponentTranslation(
                    "item.ae2_utilix.me_channel_caller.tooltip.id", id));
        }
        if (caller.getStatus() == TileMEChannelCaller.STATUS_CONNECTED) {
            probeInfo.text(new TextComponentTranslation(
                    caller.getTranslationPrefix() + ".status.connected",
                    caller.getRemoteChannels(), caller.getMaxChannels()));
        } else if (caller.getStatus() == TileMEChannelCaller.STATUS_OFF) {
            probeInfo.text(new TextComponentTranslation(
                    caller.getTranslationPrefix() + ".status.off"));
        } else if (caller.getStatusReason() == TileMEChannelCaller.REASON_OVERLOADED) {
            probeInfo.text(new TextComponentTranslation(
                    caller.getTranslationPrefix() + ".status.overloaded",
                    caller.getRemoteChannels(), caller.getMaxChannels()));
        } else if (caller.getStatusReason() == TileMEChannelCaller.REASON_AMBIGUOUS) {
            probeInfo.text(new TextComponentTranslation(
                    caller.getTranslationPrefix() + ".status.ambiguous"));
        } else {
            probeInfo.text(new TextComponentTranslation(
                    caller.getTranslationPrefix() + ".status.missing"));
        }
    }
}
