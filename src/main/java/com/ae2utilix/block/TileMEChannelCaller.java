package com.ae2utilix.block;

import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.tile.grid.AENetworkTile;
import com.ae2utilix.AE2Utilix;
import com.ae2utilix.integration.MEChannelCallerNetwork;
import com.ae2utilix.item.ItemMEChannelCaller;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * The caller is an actual AE2 network host.  The wireless bridge is managed
 * separately, but the block itself must still expose a node so an ME cable
 * can discover it and provide network power.
 */
public class TileMEChannelCaller extends AENetworkTile {
    public static final int STATUS_OFF = 0;
    public static final int STATUS_CONNECTED = 1;
    public static final int STATUS_ERROR = 2;
    public static final int REASON_NONE = 0;
    public static final int REASON_MISSING = 1;
    public static final int REASON_OVERLOADED = 2;
    public static final int REASON_AMBIGUOUS = 3;

    private static final String NBT_CHANNEL_ID = "ChannelId";
    private static final String NBT_STATUS = "Status";
    private static final String NBT_STATUS_REASON = "StatusReason";
    private static final String NBT_REMOTE_CHANNELS = "RemoteChannels";
    private static final String MEMORY_SETTINGS = "ae2_utilix.me_channel_caller";

    @Nullable
    private UUID channelId;
    private int status = STATUS_OFF;
    private int statusReason = REASON_NONE;
    private int remoteChannels;

    @Nullable
    public UUID getChannelId() {
        return channelId;
    }

    public void setChannelId(@Nullable UUID id) {
        if (channelId == null ? id == null : channelId.equals(id)) {
            return;
        }
        channelId = id;
        saveChanges();
        MEChannelCallerNetwork.markDirty(this);
        markForUpdate();
    }

    public int getStatus() {
        return status;
    }

    public int getStatusReason() {
        return statusReason;
    }

    public int getRemoteChannels() {
        return remoteChannels;
    }

    public void setStatus(int nextStatus) {
        setStatus(nextStatus, REASON_NONE, 0);
    }

    public void setStatus(int nextStatus, int reason, int channels) {
        nextStatus = Math.max(STATUS_OFF, Math.min(STATUS_ERROR, nextStatus));
        reason = Math.max(REASON_NONE, Math.min(REASON_AMBIGUOUS, reason));
        channels = Math.max(0, channels);
        if (status == nextStatus && statusReason == reason && remoteChannels == channels) {
            return;
        }
        status = nextStatus;
        statusReason = reason;
        remoteChannels = channels;
        if (world != null && !world.isRemote) {
            IBlockStateUpdater.update(this, status);
            markForUpdate();
        }
    }

    public void bindHeldItem(EntityPlayer player, ItemStack held) {
        UUID blockId = channelId;
        UUID itemId = ItemMEChannelCaller.getChannelId(held);
        if (blockId != null) {
            if (itemId == null) {
                bindOneHeldItem(player, held, blockId);
                sendMessage(player, "ae2_utilix.me_channel_caller.bind.copied");
                return;
            }
            sendMessage(player, "ae2_utilix.me_channel_caller.bind.occupied");
            return;
        }

        UUID id = itemId == null ? UUID.randomUUID() : itemId;
        setChannelId(id);
        if (itemId == null) {
            bindOneHeldItem(player, held, id);
            sendMessage(player, "ae2_utilix.me_channel_caller.bind.created");
        } else {
            sendMessage(player, "ae2_utilix.me_channel_caller.bind.pasted");
        }
    }

    private static void bindOneHeldItem(EntityPlayer player, ItemStack held, UUID id) {
        if (held.getCount() == 1) {
            ItemMEChannelCaller.setChannelId(held, id);
            return;
        }
        ItemStack bound = held.splitStack(1);
        ItemMEChannelCaller.setChannelId(bound, id);
        if (!player.inventory.addItemStackToInventory(bound)) {
            player.dropItem(bound, false);
        }
    }

    public void handleMemoryCard(EntityPlayer player, ItemStack held) {
        if (!(held.getItem() instanceof IMemoryCard)) {
            return;
        }
        IMemoryCard card = (IMemoryCard) held.getItem();
        if (player.isSneaking()) {
            NBTTagCompound data = new NBTTagCompound();
            if (channelId != null) {
                data.setUniqueId(NBT_CHANNEL_ID, channelId);
            }
            card.setMemoryCardContents(held, MEMORY_SETTINGS, data);
            card.notifyUser(player, MemoryCardMessages.SETTINGS_SAVED);
            sendMessage(player, "ae2_utilix.me_channel_caller.memory.saved");
            return;
        }

        NBTTagCompound data = card.getData(held);
        if (data == null || !data.hasUniqueId(NBT_CHANNEL_ID)) {
            card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            sendMessage(player, "ae2_utilix.me_channel_caller.memory.invalid");
            return;
        }
        setChannelId(data.getUniqueId(NBT_CHANNEL_ID));
        card.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
        sendMessage(player, "ae2_utilix.me_channel_caller.memory.loaded");
    }

    private static void sendMessage(EntityPlayer player, String key) {
        player.sendMessage(new TextComponentTranslation(key));
    }

    public void invalidateCaller() {
        MEChannelCallerNetwork.unregister(this);
    }

    @Override
    public void onReady() {
        super.onReady();
        MEChannelCallerNetwork.register(this);
    }

    @Override
    public void onChunkUnload() {
        MEChannelCallerNetwork.unregister(this);
        super.onChunkUnload();
    }

    @Override
    public void invalidate() {
        MEChannelCallerNetwork.unregister(this);
        super.invalidate();
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        channelId = data.hasUniqueId(NBT_CHANNEL_ID) ? data.getUniqueId(NBT_CHANNEL_ID) : null;
        status = data.hasKey(NBT_STATUS) ? data.getInteger(NBT_STATUS) : STATUS_OFF;
        statusReason = data.hasKey(NBT_STATUS_REASON) ? data.getInteger(NBT_STATUS_REASON) : REASON_NONE;
        remoteChannels = data.hasKey(NBT_REMOTE_CHANNELS) ? data.getInteger(NBT_REMOTE_CHANNELS) : 0;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        if (channelId != null) {
            data.setUniqueId(NBT_CHANNEL_ID, channelId);
        }
        data.setInteger(NBT_STATUS, status);
        data.setInteger(NBT_STATUS_REASON, statusReason);
        data.setInteger(NBT_REMOTE_CHANNELS, remoteChannels);
        return data;
    }

    @Override
    protected void writeToStream(ByteBuf data) throws IOException {
        super.writeToStream(data);
        data.writeByte(status);
        data.writeByte(statusReason);
        data.writeInt(remoteChannels);
    }

    @Override
    protected boolean readFromStream(ByteBuf data) throws IOException {
        boolean changed = super.readFromStream(data);
        int oldStatus = status;
        int oldReason = statusReason;
        int oldRemoteChannels = remoteChannels;
        status = data.readUnsignedByte();
        statusReason = data.readUnsignedByte();
        remoteChannels = data.readInt();
        return changed || oldStatus != status || oldReason != statusReason
                || oldRemoteChannels != remoteChannels;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        tag.setInteger(NBT_STATUS, status);
        tag.setInteger(NBT_STATUS_REASON, statusReason);
        tag.setInteger(NBT_REMOTE_CHANNELS, remoteChannels);
        return tag;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        if (tag.hasKey(NBT_STATUS)) {
            status = Math.max(STATUS_OFF, Math.min(STATUS_ERROR, tag.getInteger(NBT_STATUS)));
        }
        if (tag.hasKey(NBT_STATUS_REASON)) {
            statusReason = Math.max(REASON_NONE, Math.min(REASON_AMBIGUOUS,
                    tag.getInteger(NBT_STATUS_REASON)));
        }
        if (tag.hasKey(NBT_REMOTE_CHANNELS)) {
            remoteChannels = Math.max(0, tag.getInteger(NBT_REMOTE_CHANNELS));
        }
    }

    /** Keeps the visual state in sync without putting the status in the item NBT. */
    private static final class IBlockStateUpdater {
        private static void update(TileMEChannelCaller tile, int status) {
            World world = tile.getWorld();
            BlockPos pos = tile.getPos();
            if (world == null || pos == null) return;
            net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
            if (state.getBlock() == AE2Utilix.BLOCK_ME_CHANNEL_CALLER
                    && state.getPropertyKeys().contains(BlockMEChannelCaller.STATUS)) {
                world.setBlockState(pos, state.withProperty(BlockMEChannelCaller.STATUS, status), 3);
            }
        }
    }
}
