package com.ae2utilix.ae2_utilix.block;

import ae2.api.implementations.items.IMemoryCard;
import ae2.api.implementations.items.MemoryCardMessages;
import ae2.api.networking.IGridNode;
import ae2.api.util.AEColor;
import ae2.api.util.AEPartLocation;
import ae2.api.networking.IGridNodeListener;
import ae2.items.tools.MemoryCardItem;
import ae2.tile.grid.AENetworkedTile;
import com.ae2utilix.ae2_utilix.AE2Utilix;
import com.ae2utilix.ae2_utilix.integration.MEChannelCallerNetwork;
import com.ae2utilix.ae2_utilix.integration.MEChannelCallerSavedData;
import com.ae2utilix.ae2_utilix.item.ItemMEChannelCaller;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.UUID;

/**
 * The caller is an actual AE2 network host.  The wireless bridge is managed
 * separately, but the block itself must still expose a node so an ME cable
 * can discover it and provide network power.
 */
public class TileMEChannelCaller extends AENetworkedTile {
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
    private static final String NBT_MAX_CHANNELS = "MaxChannels";
    private static final String NBT_LISTENER_MODE = "ListenerMode";
    private static final String NBT_PLACEMENT_ORDER = "PlacementOrder";
    /** Memory card settings name; AE2 looks up "<name>.name" for the card tooltip. */
    private static final String MEMORY_SETTINGS = "tile.ae2_utilix.me_channel_caller";
    private static final String MEMORY_SETTINGS_LISTENER = "tile.ae2_utilix.me_channel_listener";

    @Nullable
    private UUID channelId;
    private int status = STATUS_OFF;
    private int statusReason = REASON_NONE;
    /** Channels currently carried by this block's UUID link. */
    private int remoteChannels;
    /** Channels that link can carry, as reported by AE2's own configuration. */
    private int maxChannels;
    private boolean listenerMode;
    /** Monotonic stamp of when this block was placed, so ties can prefer the oldest. */
    private long placementOrder;

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
        MEChannelCallerSavedData.update(this);
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

    public long getPlacementOrder() {
        return placementOrder;
    }

    /**
     * Stamps the placement time once, so two UUIDs with the same spare capacity can still
     * be told apart by which caller was placed first.
     */
    public void ensurePlacementOrder() {
        if (placementOrder <= 0) {
            // World time survives a restart, unlike JVM clock readings, so "placed first"
            // still means the same thing after the server is reloaded.
            long stamp = world == null ? 1L : world.getTotalWorldTime();
            placementOrder = Math.max(1L, stamp);
            saveChanges();
        }
    }

    public int getMaxChannels() {
        return maxChannels;
    }

    public boolean isListener() {
        return listenerMode;
    }

    @Override
    public IGridNode getGridNode(EnumFacing direction) {
        if (world != null && pos != null && direction != null) {
            TileEntity adjacent = world.getTileEntity(pos.offset(direction));
            if (adjacent instanceof TileMEChannelCaller) {
                return null;
            }
        }
        return super.getGridNode(direction);
    }

    public String getTranslationPrefix() {
        return listenerMode ? "ae2_utilix.me_channel_listener" : "ae2_utilix.me_channel_caller";
    }

    public void setListenerMode(boolean listenerMode) {
        if (this.listenerMode == listenerMode) {
            return;
        }
        this.listenerMode = listenerMode;
        saveChanges();
        MEChannelCallerSavedData.update(this);
        MEChannelCallerNetwork.markDirty(this);
        if (world != null && !world.isRemote) {
            IBlockStateUpdater.update(this, status, listenerMode);
            markForUpdate();
        }
    }

    public void setStatus(int nextStatus) {
        setStatus(nextStatus, REASON_NONE, 0, 0);
    }

    public void setStatus(int nextStatus, int reason, int channels) {
        setStatus(nextStatus, reason, channels, maxChannels);
    }

    public void setStatus(int nextStatus, int reason, int channels, int channelLimit) {
        nextStatus = Math.max(STATUS_OFF, Math.min(STATUS_ERROR, nextStatus));
        reason = Math.max(REASON_NONE, Math.min(REASON_AMBIGUOUS, reason));
        channels = Math.max(0, channels);
        channelLimit = Math.max(0, channelLimit);
        if (status == nextStatus && statusReason == reason && remoteChannels == channels
                && maxChannels == channelLimit) {
            return;
        }
        status = nextStatus;
        statusReason = reason;
        remoteChannels = channels;
        maxChannels = channelLimit;
        if (world != null && !world.isRemote) {
            IBlockStateUpdater.update(this, status, listenerMode);
            markForUpdate();
        }
    }

    public void bindHeldItem(EntityPlayer player, ItemStack held) {
        UUID blockId = channelId;
        UUID itemId = ItemMEChannelCaller.getChannelId(held);
        if (blockId != null) {
            if (itemId == null) {
                bindOneHeldItem(player, held, blockId);
                sendRoleMessage(player, "bind.copied");
                return;
            }
            sendRoleMessage(player, "bind.occupied");
            return;
        }

        UUID id = itemId == null ? UUID.randomUUID() : itemId;
        setChannelId(id);
        if (itemId == null) {
            bindOneHeldItem(player, held, id);
            sendRoleMessage(player, "bind.created");
        } else {
            sendRoleMessage(player, "bind.pasted");
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
            // Only the UUID travels on the card: the mode of this block stays behind, so
            // pasting it elsewhere never turns a listener into a caller or the reverse.
            // The card itself gets a matching colour pair so it is visibly bound.
            MemoryCardItem.clearCard(held);
            NBTTagCompound tag = held.getTagCompound();
            if (tag == null) {
                tag = new NBTTagCompound();
                held.setTagCompound(tag);
            }
            if (channelId != null) {
                tag.setUniqueId(NBT_CHANNEL_ID, channelId);
            }
            MemoryCardItem.setMemoryCardColors(tag, memoryCardColors(channelId));
            // AE2 already reports "saved to the memory card", so no second chat line here.
            card.notifyUser(player, MemoryCardMessages.SETTINGS_SAVED);
            return;
        }

        NBTTagCompound tag = held.getTagCompound();
        if (tag == null || !tag.hasUniqueId(NBT_CHANNEL_ID)) {
            card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            sendRoleMessage(player, "memory.invalid");
            return;
        }
        setChannelId(tag.getUniqueId(NBT_CHANNEL_ID));
        card.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
    }

    /**
     * Colour bands derived from the channel UUID, so a bound card is visually distinct.
     */
    private static ae2.api.implementations.items.MemoryCardColors memoryCardColors(@Nullable UUID id) {
        AEColor[] palette = AEColor.values();
        // Ordinal 0 is transparent, which draws nothing, so start past it.
        int usable = Math.max(1, palette.length - 1);
        int seed = id == null ? 0 : id.hashCode();
        AEColor[] picked = new AEColor[4];
        for (int i = 0; i < 4; i++) {
            picked[i] = palette[1 + Math.floorMod((seed >>> (i * 5)) & 0x1F, usable)];
        }
        return ae2.api.implementations.items.MemoryCardColors.repeatedPairs(picked[0], picked[1], picked[2],
                picked[3]);
    }
    /**
     * Four colours derived from the channel UUID, mirrored into the eight entries AE2's
     * memory card model expects.
     */
    private static int[] colorCodeFor(@Nullable UUID id) {
        int seed = id == null ? 0 : id.hashCode();
        AEColor[] palette = AEColor.values();
        // Ordinal 0 is transparent, which draws nothing, so start past it.
        int usable = Math.max(1, palette.length - 1);
        int[] code = new int[8];
        for (int i = 0; i < 4; i++) {
            int slot = 1 + Math.floorMod((seed >>> (i * 5)) & 0x1F, usable);
            code[i * 2] = palette[slot].ordinal();
            code[i * 2 + 1] = palette[slot].ordinal();
        }
        return code;
    }

    private void sendRoleMessage(EntityPlayer player, String suffix) {
        sendMessage(player, getTranslationPrefix() + "." + suffix);
    }

    private static void sendMessage(EntityPlayer player, String key) {
        player.sendMessage(new TextComponentTranslation(key));
    }

    public void invalidateCaller() {
        MEChannelCallerSavedData.remove(this);
        MEChannelCallerNetwork.unregister(this);
    }

    @Override
    public void onReady() {
        super.onReady();
        restoreListenerModeFromBlockState();
        MEChannelCallerSavedData.update(this);
        MEChannelCallerNetwork.register(this);
    }

    /**
     * Older unified blocks may already have listener-mode block metadata while
     * their TileEntity was saved before the mode field existed.  Restore that
     * role before registering the endpoint, otherwise getActualState() would
     * turn the block back into a caller and create sender-to-sender links.
     */
    private void restoreListenerModeFromBlockState() {
        if (world == null || world.isRemote || listenerMode
                || !(world.getBlockState(pos).getBlock() instanceof BlockMEChannelCaller)) {
            return;
        }
        net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
        if (state.getPropertyKeys().contains(BlockMEChannelCaller.LISTENER_MODE)
                && state.getValue(BlockMEChannelCaller.LISTENER_MODE)) {
            setListenerMode(true);
        }
    }

    @Override
    public void onChunkUnloaded() {
        MEChannelCallerNetwork.unregister(this);
        super.onChunkUnload();
    }

    @Override
    public void setRemoved() {
        MEChannelCallerNetwork.unregister(this);
        super.setRemoved();
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        channelId = data.hasUniqueId(NBT_CHANNEL_ID) ? data.getUniqueId(NBT_CHANNEL_ID) : null;
        status = data.hasKey(NBT_STATUS) ? data.getInteger(NBT_STATUS) : STATUS_OFF;
        statusReason = data.hasKey(NBT_STATUS_REASON) ? data.getInteger(NBT_STATUS_REASON) : REASON_NONE;
        remoteChannels = data.hasKey(NBT_REMOTE_CHANNELS) ? data.getInteger(NBT_REMOTE_CHANNELS) : 0;
        maxChannels = data.hasKey(NBT_MAX_CHANNELS) ? data.getInteger(NBT_MAX_CHANNELS) : 0;
        listenerMode = data.getBoolean(NBT_LISTENER_MODE);
        placementOrder = data.getLong(NBT_PLACEMENT_ORDER);
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        if (channelId != null) {
            data.setUniqueId(NBT_CHANNEL_ID, channelId);
        }
        data.setInteger(NBT_STATUS, status);
        data.setInteger(NBT_STATUS_REASON, statusReason);
        data.setInteger(NBT_REMOTE_CHANNELS, remoteChannels);
        data.setInteger(NBT_MAX_CHANNELS, maxChannels);
        data.setBoolean(NBT_LISTENER_MODE, listenerMode);
        data.setLong(NBT_PLACEMENT_ORDER, placementOrder);
    }

    @Override
    protected void writeToStream(ByteBuf data) {
        super.writeToStream(data);
        data.writeByte(status);
        data.writeByte(statusReason);
        data.writeInt(remoteChannels);
        data.writeInt(maxChannels);
        data.writeBoolean(listenerMode);
    }

    @Override
    protected boolean readFromStream(ByteBuf data) {
        boolean changed = super.readFromStream(data);
        int oldStatus = status;
        int oldReason = statusReason;
        int oldRemoteChannels = remoteChannels;
        int oldMaxChannels = maxChannels;
        status = data.readUnsignedByte();
        statusReason = data.readUnsignedByte();
        remoteChannels = data.readInt();
        maxChannels = data.readInt();
        boolean oldListenerMode = listenerMode;
        listenerMode = data.readBoolean();
        return changed || oldStatus != status || oldReason != statusReason
                || oldRemoteChannels != remoteChannels || oldMaxChannels != maxChannels
                || oldListenerMode != listenerMode;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        tag.setInteger(NBT_STATUS, status);
        tag.setInteger(NBT_STATUS_REASON, statusReason);
        tag.setInteger(NBT_REMOTE_CHANNELS, remoteChannels);
        tag.setInteger(NBT_MAX_CHANNELS, maxChannels);
        tag.setBoolean(NBT_LISTENER_MODE, listenerMode);
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
        if (tag.hasKey(NBT_MAX_CHANNELS)) {
            maxChannels = Math.max(0, tag.getInteger(NBT_MAX_CHANNELS));
        }
        if (tag.hasKey(NBT_LISTENER_MODE)) {
            listenerMode = tag.getBoolean(NBT_LISTENER_MODE);
        }
    }

    /** Keeps the visual state in sync without putting the status in the item NBT. */
    private static final class IBlockStateUpdater {
        private static void update(TileMEChannelCaller tile, int status, boolean listenerMode) {
            World world = tile.getWorld();
            BlockPos pos = tile.getPos();
            if (world == null || pos == null) return;
            net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof BlockMEChannelCaller
                    && state.getPropertyKeys().contains(BlockMEChannelCaller.STATUS)
                    && state.getPropertyKeys().contains(BlockMEChannelCaller.LISTENER_MODE)) {
                world.setBlockState(pos, state
                        .withProperty(BlockMEChannelCaller.LISTENER_MODE, listenerMode)
                        .withProperty(BlockMEChannelCaller.STATUS, status), 3);
            }
        }
    }
}
