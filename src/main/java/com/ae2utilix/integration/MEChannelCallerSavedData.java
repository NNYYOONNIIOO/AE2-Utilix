package com.ae2utilix.integration;

import com.ae2utilix.block.TileMEChannelCaller;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Persistent UUID endpoint index for ME channel callers.
 *
 * <p>The live AE bridge only uses loaded tile entities, but the endpoint
 * identity must not disappear when a chunk unloads.  Keeping the UUID and
 * position in world data also makes a restart deterministic: loading a tile
 * re-registers the same endpoint instead of creating a new in-memory group.</p>
 */
public final class MEChannelCallerSavedData extends WorldSavedData {
    private static final String DATA_ID = "ae2_utilix_me_channel_callers";
    private static final String CHANNELS = "Channels";
    private static final String CHANNEL_ID = "Id";
    private static final String ENDPOINTS = "Endpoints";
    private static final String DIMENSION = "Dimension";
    private static final String POSITION = "Position";
    private static final String LISTENER = "Listener";

    private final List<Endpoint> endpoints = new ArrayList<>();

    public MEChannelCallerSavedData(String name) {
        super(name);
    }

    @Nullable
    public static MEChannelCallerSavedData get(World world) {
        if (world == null || world.isRemote || world.getMapStorage() == null) {
            return null;
        }
        WorldSavedData loaded = world.getMapStorage().getOrLoadData(
                MEChannelCallerSavedData.class, DATA_ID);
        if (loaded instanceof MEChannelCallerSavedData) {
            return (MEChannelCallerSavedData) loaded;
        }
        MEChannelCallerSavedData created = new MEChannelCallerSavedData(DATA_ID);
        world.getMapStorage().setData(DATA_ID, created);
        return created;
    }

    /** Updates one loaded endpoint, removing its old UUID entry first. */
    public static void update(TileMEChannelCaller tile) {
        if (tile == null || tile.getWorld() == null || tile.getWorld().isRemote) {
            return;
        }
        MEChannelCallerSavedData data = get(tile.getWorld());
        if (data == null) {
            return;
        }
        data.removePosition(tile.getWorld(), tile.getPos());
        UUID id = tile.getChannelId();
        if (id != null && tile.getPos() != null) {
            data.endpoints.add(new Endpoint(id, tile.getWorld().provider.getDimension(),
                    tile.getPos(), tile.isListener()));
        }
        data.markDirty();
    }

    /** Removes a block from the persistent UUID index when it is broken. */
    public static void remove(TileMEChannelCaller tile) {
        if (tile == null || tile.getWorld() == null || tile.getWorld().isRemote) {
            return;
        }
        MEChannelCallerSavedData data = get(tile.getWorld());
        if (data != null) {
            data.removePosition(tile.getWorld(), tile.getPos());
            data.markDirty();
        }
    }

    /** Removes records whose loaded chunk no longer contains the endpoint. */
    public static void prune(World world) {
        if (world == null || world.isRemote) {
            return;
        }
        MEChannelCallerSavedData data = get(world);
        if (data == null) {
            return;
        }
        boolean changed = false;
        int dimension = world.provider.getDimension();
        for (Iterator<Endpoint> iterator = data.endpoints.iterator(); iterator.hasNext();) {
            Endpoint endpoint = iterator.next();
            if (endpoint.dimension != dimension || !world.isBlockLoaded(endpoint.position)) {
                continue;
            }
            if (!(world.getTileEntity(endpoint.position) instanceof TileMEChannelCaller)) {
                iterator.remove();
                changed = true;
                continue;
            }
            TileMEChannelCaller tile = (TileMEChannelCaller) world.getTileEntity(endpoint.position);
            if (!endpoint.id.equals(tile.getChannelId()) || endpoint.listener != tile.isListener()) {
                iterator.remove();
                changed = true;
            }
        }
        if (changed) {
            data.markDirty();
        }
    }

    private void removePosition(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return;
        }
        int dimension = world.provider.getDimension();
        for (Iterator<Endpoint> iterator = endpoints.iterator(); iterator.hasNext();) {
            Endpoint endpoint = iterator.next();
            if (endpoint.dimension == dimension && endpoint.position.equals(pos)) {
                iterator.remove();
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        endpoints.clear();
        NBTTagList channels = tag.getTagList(CHANNELS, 10);
        for (int i = 0; i < channels.tagCount(); i++) {
            NBTTagCompound channel = channels.getCompoundTagAt(i);
            if (!channel.hasUniqueId(CHANNEL_ID)) {
                continue;
            }
            UUID id = channel.getUniqueId(CHANNEL_ID);
            NBTTagList channelEndpoints = channel.getTagList(ENDPOINTS, 10);
            for (int j = 0; j < channelEndpoints.tagCount(); j++) {
                NBTTagCompound endpoint = channelEndpoints.getCompoundTagAt(j);
                endpoints.add(new Endpoint(id, endpoint.getInteger(DIMENSION),
                        BlockPos.fromLong(endpoint.getLong(POSITION)),
                        endpoint.getBoolean(LISTENER)));
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        NBTTagList channels = new NBTTagList();
        List<UUID> ids = new ArrayList<>();
        for (Endpoint endpoint : endpoints) {
            if (!ids.contains(endpoint.id)) {
                ids.add(endpoint.id);
            }
        }
        for (UUID id : ids) {
            NBTTagCompound channel = new NBTTagCompound();
            channel.setUniqueId(CHANNEL_ID, id);
            NBTTagList channelEndpoints = new NBTTagList();
            for (Endpoint endpoint : endpoints) {
                if (id.equals(endpoint.id)) {
                    NBTTagCompound saved = new NBTTagCompound();
                    saved.setInteger(DIMENSION, endpoint.dimension);
                    saved.setLong(POSITION, endpoint.position.toLong());
                    saved.setBoolean(LISTENER, endpoint.listener);
                    channelEndpoints.appendTag(saved);
                }
            }
            channel.setTag(ENDPOINTS, channelEndpoints);
            channels.appendTag(channel);
        }
        tag.setTag(CHANNELS, channels);
        return tag;
    }

    private static final class Endpoint {
        private final UUID id;
        private final int dimension;
        private final BlockPos position;
        private final boolean listener;

        private Endpoint(UUID id, int dimension, BlockPos position, boolean listener) {
            this.id = id;
            this.dimension = dimension;
            this.position = position;
            this.listener = listener;
        }
    }
}
