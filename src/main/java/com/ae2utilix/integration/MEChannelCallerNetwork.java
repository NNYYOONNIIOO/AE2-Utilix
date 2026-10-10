package com.ae2utilix.integration;

import appeng.api.AEApi;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.me.GridConnection;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import com.ae2utilix.AE2Utilix;
import com.ae2utilix.block.TileMEChannelCaller;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Maintains the AE2 bridge behind paired ME Channel Caller blocks.
 *
 * <p>The bridge follows the same shape as an ME P2P tunnel: a channel-bearing
 * proxy is attached to each local network, while the dense outer proxies are
 * joined into one shared network. A UUID therefore represents a channel group,
 * not just a pair of blocks, and a powered endpoint can power every other
 * endpoint in that group.</p>
 */
public final class MEChannelCallerNetwork {
    private static final long REFRESH_INTERVAL = 10L;
    private static final EnumFacing[] CHANNEL_DIRECTION_ORDER = {
            EnumFacing.EAST, EnumFacing.UP, EnumFacing.SOUTH,
            EnumFacing.WEST, EnumFacing.DOWN, EnumFacing.NORTH
    };
    private static final Map<World, Set<TileMEChannelCaller>> CALLERS =
            new WeakHashMap<>();
    private static final Map<World, Long> NEXT_REFRESH = new WeakHashMap<>();
    private static final Set<World> DIRTY_WORLDS =
            Collections.newSetFromMap(new WeakHashMap<World, Boolean>());
    private static final Map<String, CallerBridge> BRIDGES = new HashMap<>();

    private MEChannelCallerNetwork() {
    }

    public static void register(TileMEChannelCaller caller) {
        if (caller == null || caller.getWorld() == null || caller.getWorld().isRemote) {
            return;
        }
        Set<TileMEChannelCaller> callers = CALLERS.get(caller.getWorld());
        if (callers == null) {
            callers = Collections.newSetFromMap(
                    new IdentityHashMap<TileMEChannelCaller, Boolean>());
            CALLERS.put(caller.getWorld(), callers);
        }
        callers.add(caller);
        DIRTY_WORLDS.add(caller.getWorld());
    }

    public static void unregister(TileMEChannelCaller caller) {
        if (caller == null) {
            return;
        }
        World world = caller.getWorld();
        if (world != null) {
            Set<TileMEChannelCaller> callers = CALLERS.get(world);
            if (callers != null) {
                callers.remove(caller);
            }
            DIRTY_WORLDS.add(world);
        }
        removeBridgesFor(caller);
    }

    public static void markDirty(TileMEChannelCaller caller) {
        if (caller != null && caller.getWorld() != null && !caller.getWorld().isRemote) {
            DIRTY_WORLDS.add(caller.getWorld());
        }
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event == null || event.phase != TickEvent.Phase.END
                || event.world == null || event.world.isRemote) {
            return;
        }
        World world = event.world;
        // A bridge can have one endpoint in another dimension.  Do not use
        // the current world's caller set as the deciding factor here: the
        // world that happens to tick first must be able to reconcile all
        // loaded dimensions.
        if (CALLERS.isEmpty()) {
            return;
        }
        long now = world.getTotalWorldTime();
        Long next = NEXT_REFRESH.get(world);
        if (!DIRTY_WORLDS.contains(world) && next != null && now < next) {
            return;
        }
        DIRTY_WORLDS.clear();
        scheduleNextRefresh();
        reconcile(snapshotAllCallers());
    }

    /**
     * Returns all valid server-side callers in every currently loaded world.
     * The registry is still grouped by world so unloading a dimension can let
     * its entries be garbage-collected, but UUID matching must be global.
     */
    private static List<TileMEChannelCaller> snapshotAllCallers() {
        List<TileMEChannelCaller> callers = new ArrayList<>();
        for (Map.Entry<World, Set<TileMEChannelCaller>> entry
                : new ArrayList<>(CALLERS.entrySet())) {
            World world = entry.getKey();
            Set<TileMEChannelCaller> registered = entry.getValue();
            if (world == null || registered == null) {
                continue;
            }
            for (TileMEChannelCaller caller : new ArrayList<>(registered)) {
                if (caller == null || caller.isInvalid() || caller.getWorld() != world
                        || world.isRemote) {
                    registered.remove(caller);
                } else {
                    callers.add(caller);
                }
            }
        }
        return callers;
    }

    private static void scheduleNextRefresh() {
        for (World world : new ArrayList<>(CALLERS.keySet())) {
            if (world != null) {
                NEXT_REFRESH.put(world, world.getTotalWorldTime() + REFRESH_INTERVAL);
            }
        }
    }

    private static void reconcile(List<TileMEChannelCaller> callers) {
        Map<UUID, List<TileMEChannelCaller>> byId = new HashMap<>();
        for (TileMEChannelCaller caller : callers) {
            UUID id = caller.getChannelId();
            if (id != null) {
                List<TileMEChannelCaller> pair = byId.get(id);
                if (pair == null) {
                    pair = new ArrayList<>();
                    byId.put(id, pair);
                }
                pair.add(caller);
            } else {
                boolean powered = hasPoweredEndpoint(caller);
                caller.setStatus(powered ? TileMEChannelCaller.STATUS_ERROR
                                : TileMEChannelCaller.STATUS_OFF,
                        powered ? TileMEChannelCaller.REASON_MISSING
                                : TileMEChannelCaller.REASON_NONE, 0);
            }
        }

        Set<String> liveBridgeKeys = new HashSet<>();
        for (Map.Entry<UUID, List<TileMEChannelCaller>> entry : byId.entrySet()) {
            String key = bridgeKey(entry.getKey());
            CallerBridge bridge = BRIDGES.get(key);

            List<TileMEChannelCaller> activeCallers = new ArrayList<>();
            List<Endpoint> endpoints = new ArrayList<>();
            for (TileMEChannelCaller caller : entry.getValue()) {
                Endpoint endpoint = findEndpoint(caller);
                if (endpoint == null || endpoint.node == null) {
                    // A caller without an AE node cannot participate, but an
                    // existing node is enough: GridConnection can attach an
                    // isolated node to the group's powered network.
                    caller.setStatus(TileMEChannelCaller.STATUS_OFF,
                            TileMEChannelCaller.REASON_NONE, 0);
                } else {
                    activeCallers.add(caller);
                    endpoints.add(endpoint);
                }
            }

            if (activeCallers.size() < 2) {
                destroyBridge(key);
                for (TileMEChannelCaller caller : entry.getValue()) {
                    Endpoint endpoint = findEndpoint(caller);
                    boolean powered = isPowered(endpoint);
                    caller.setStatus(powered ? TileMEChannelCaller.STATUS_ERROR
                                    : TileMEChannelCaller.STATUS_OFF,
                            powered ? TileMEChannelCaller.REASON_MISSING
                                    : TileMEChannelCaller.REASON_NONE, 0);
                }
                continue;
            }

            // Do not tear down a bridge merely because its own connections
            // have merged all endpoint nodes into one AE grid. That merge is
            // the successful result of powering the remote callers.
            if (bridge != null && !bridge.matches(activeCallers, endpoints)) {
                destroyBridge(key);
                bridge = null;
            }

            if (bridge == null && !allSameGrid(endpoints)) {
                bridge = CallerBridge.create(activeCallers, endpoints);
                if (bridge != null) {
                    BRIDGES.put(key, bridge);
                }
            }

            if (bridge != null) {
                liveBridgeKeys.add(key);
            }
            boolean connected = allSameGrid(endpoints)
                    || (bridge != null && bridge.isAlive());
            for (int i = 0; i < activeCallers.size(); i++) {
                TileMEChannelCaller caller = activeCallers.get(i);
                Endpoint endpoint = endpoints.get(i);
                boolean powered = isPowered(endpoint);
                int channelUsers = countChannelUsers(endpoint.node);
                boolean overloaded = connected && channelUsers > getTransferCapacity();
                caller.setStatus(overloaded ? TileMEChannelCaller.STATUS_ERROR
                                : connected && powered ? TileMEChannelCaller.STATUS_CONNECTED
                                : connected ? TileMEChannelCaller.STATUS_OFF
                                : powered ? TileMEChannelCaller.STATUS_ERROR
                                : TileMEChannelCaller.STATUS_OFF,
                        overloaded ? TileMEChannelCaller.REASON_OVERLOADED
                                : connected || !powered ? TileMEChannelCaller.REASON_NONE
                                : TileMEChannelCaller.REASON_MISSING,
                        Math.max(0, channelUsers - 1));
            }
        }

        for (String key : new ArrayList<>(BRIDGES.keySet())) {
            CallerBridge bridge = BRIDGES.get(key);
            if (bridge == null || !liveBridgeKeys.contains(key)) {
                destroyBridge(key);
            }
        }
    }

    private static boolean hasPoweredEndpoint(TileMEChannelCaller caller) {
        return isPowered(findEndpoint(caller));
    }

    private static int getTransferCapacity() {
        try {
            return appeng.core.AEConfig.instance().getDenseChannelCapacity();
        } catch (Throwable ignored) {
            return 32;
        }
    }

    private static int countChannelUsers(IGridNode node) {
        if (node == null || node.getGrid() == null) {
            return 0;
        }
        int count = 0;
        try {
            for (IGridNode candidate : node.getGrid().getNodes()) {
                if (candidate != null && candidate.hasFlag(GridFlags.REQUIRE_CHANNEL)
                        && candidate.meetsChannelRequirements()) {
                    count++;
                }
            }
        } catch (Throwable ignored) {
        }
        return count;
    }

    private static boolean isPowered(@Nullable Endpoint endpoint) {
        if (endpoint == null || endpoint.node == null || endpoint.node.getGrid() == null) {
            return false;
        }
        try {
            IEnergyGrid energy = endpoint.node.getGrid().getCache(IEnergyGrid.class);
            return energy != null && energy.isNetworkPowered();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Nullable
    private static Endpoint findEndpoint(TileMEChannelCaller caller) {
        if (caller == null || caller.getWorld() == null || caller.getPos() == null) {
            return null;
        }

        // ChannelSparkNetwork discovers endpoints from the target IGridHost
        // itself, in a stable face order. The caller is that host; do not
        // substitute a cable/controller found by walking its connections.
        IGridHost host = caller;
        for (EnumFacing facing : CHANNEL_DIRECTION_ORDER) {
            AEPartLocation direction = AEPartLocation.fromFacing(facing);
            IGridNode node;
            try {
                node = host.getGridNode(direction);
            } catch (Throwable ignored) {
                continue;
            }
            if (isUsableEndpoint(node)) {
                return new Endpoint(node);
            }
        }

        IGridNode internal;
        try {
            internal = host.getGridNode(AEPartLocation.INTERNAL);
        } catch (Throwable ignored) {
            return null;
        }
        return isUsableEndpoint(internal)
                ? new Endpoint(internal) : null;
    }

    private static boolean isUsableEndpoint(@Nullable IGridNode node) {
        if (node == null) {
            return false;
        }
        try {
            return node.getGrid() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean sameGrid(IGridNode first, IGridNode second) {
        return first != null && second != null && first.getGrid() != null
                && first.getGrid() == second.getGrid();
    }

    private static boolean allSameGrid(List<Endpoint> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            return false;
        }
        IGridNode first = endpoints.get(0).node;
        if (first == null || first.getGrid() == null) {
            return false;
        }
        for (int i = 1; i < endpoints.size(); i++) {
            if (!sameGrid(first, endpoints.get(i).node)) {
                return false;
            }
        }
        return true;
    }

    private static String bridgeKey(UUID id) {
        return id == null ? "" : id.toString();
    }

    private static void removeBridgesFor(TileMEChannelCaller caller) {
        for (String key : new ArrayList<>(BRIDGES.keySet())) {
            CallerBridge bridge = BRIDGES.get(key);
            if (bridge != null && bridge.contains(caller)) {
                destroyBridge(key);
            }
        }
    }

    private static void destroyBridge(String key) {
        CallerBridge bridge = BRIDGES.remove(key);
        if (bridge != null) {
            bridge.destroy();
        }
    }

    private static void repath(IGridNode first, IGridNode second) {
        try {
            if (first != null && first.getGrid() != null) {
                ((appeng.api.networking.pathing.IPathingGrid) first.getGrid()
                        .getCache(appeng.api.networking.pathing.IPathingGrid.class)).repath();
            }
        } catch (Throwable ignored) {
        }
        try {
            if (second != null && second.getGrid() != null && !sameGrid(first, second)) {
                ((appeng.api.networking.pathing.IPathingGrid) second.getGrid()
                        .getCache(appeng.api.networking.pathing.IPathingGrid.class)).repath();
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean hasDirectConnection(IGridNode first, IGridNode second) {
        if (first == null || second == null) return false;
        try {
            for (IGridConnection connection : first.getConnections()) {
                if (connection != null && connection.getOtherSide(first) == second) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    @Nullable
    private static IGridConnection connect(IGridNode first, IGridNode second,
                                           AEPartLocation direction) {
        if (first == null || second == null) return null;
        for (IGridConnection connection : first.getConnections()) {
            if (connection.getOtherSide(first) == second) return connection;
        }
        try {
            IGridConnection connection = GridConnection.create(first, second,
                    direction == null ? AEPartLocation.INTERNAL : direction);
            repath(first, second);
            return connection;
        } catch (Throwable error) {
            if (direction == AEPartLocation.INTERNAL) {
                try {
                    IGridConnection connection = AEApi.instance().grid()
                            .createGridConnection(first, second);
                    repath(first, second);
                    return connection;
                } catch (Throwable ignored) {
                }
            }
            AE2Utilix.LOGGER.debug("ME Channel Caller local AE connection failed", error);
            return null;
        }
    }

    private static final class Endpoint {
        private final IGridNode node;

        private Endpoint(IGridNode node) {
            this.node = node;
        }
    }

    private static final class ProxyHost implements IGridProxyable {
        private final DimensionalCoord location;
        private final EnumSet<EnumFacing> sides;
        private AENetworkProxy proxy;

        private ProxyHost(DimensionalCoord location, EnumSet<EnumFacing> sides) {
            this.location = location;
            this.sides = sides == null ? EnumSet.noneOf(EnumFacing.class)
                    : EnumSet.copyOf(sides);
        }

        private void setProxy(AENetworkProxy proxy) {
            this.proxy = proxy;
        }

        @Override
        public AENetworkProxy getProxy() {
            return proxy;
        }

        @Override
        public DimensionalCoord getLocation() {
            return location;
        }

        @Override
        public void gridChanged() {
        }

        @Override
        public IGridNode getGridNode(AEPartLocation direction) {
            return proxy == null ? null : proxy.getNode();
        }

        @Override
        public void securityBreak() {
        }

        @Override
        public AECableType getCableConnectionType(AEPartLocation direction) {
            if (direction == null || direction == AEPartLocation.INTERNAL
                    || direction.getFacing() == null
                    || !sides.contains(direction.getFacing())) {
                return AECableType.NONE;
            }
            return AECableType.DENSE_SMART;
        }
    }

    /**
     * One wireless bridge for one UUID.  A channel is a group, so this owns
     * one proxy pair per caller and joins all outer proxies in a star.  The
     * first powered local network can therefore bring every other caller in
     * the group online, including callers that were previously isolated.
     */
    private static final class CallerBridge {
        private final List<TileMEChannelCaller> callers;
        private final List<Endpoint> endpoints;
        private final List<BridgeProxy> proxies = new ArrayList<>();
        private final List<IGridConnection> outerConnections = new ArrayList<>();

        private CallerBridge(List<TileMEChannelCaller> callers,
                             List<Endpoint> endpoints) {
            this.callers = new ArrayList<>(callers);
            this.endpoints = new ArrayList<>(endpoints);
        }

        @Nullable
        private static CallerBridge create(List<TileMEChannelCaller> callers,
                                           List<Endpoint> endpoints) {
            if (callers == null || endpoints == null || callers.size() < 2
                    || callers.size() != endpoints.size()) {
                return null;
            }

            CallerBridge result = new CallerBridge(callers, endpoints);
            for (int i = 0; i < callers.size(); i++) {
                BridgeProxy proxy = new BridgeProxy(callers.get(i), endpoints.get(i));
                result.proxies.add(proxy);
                if (!proxy.attach()) {
                    result.destroy();
                    return null;
                }
            }

            // Connecting each outer node from a different AE grid to the
            // first one makes the group topology deterministic and supports
            // any number of ends. Nodes already on the anchor grid are
            // already connected transitively and must not receive a second
            // artificial edge.
            IGridNode anchor = result.proxies.get(0).outerNode;
            for (int i = 1; i < result.proxies.size(); i++) {
                IGridNode remote = result.proxies.get(i).outerNode;
                if (sameGrid(anchor, remote)) {
                    continue;
                }
                IGridConnection connection = connectOuter(anchor, remote);
                if (connection == null) {
                    result.destroy();
                    return null;
                }
                result.outerConnections.add(connection);
            }
            return result;
        }

        @Nullable
        private static IGridConnection connectOuter(IGridNode first, IGridNode second) {
            if (first == null || second == null) {
                return null;
            }
            for (IGridConnection connection : first.getConnections()) {
                if (connection != null && connection.getOtherSide(first) == second) {
                    return connection;
                }
            }
            try {
                IGridConnection connection = AEApi.instance().grid()
                        .createGridConnection(first, second);
                repath(first, second);
                return connection;
            } catch (Throwable error) {
                AE2Utilix.LOGGER.debug("ME Channel Caller outer AE connection failed", error);
                return null;
            }
        }

        private boolean matches(List<TileMEChannelCaller> currentCallers,
                                List<Endpoint> currentEndpoints) {
            if (currentCallers == null || currentEndpoints == null
                    || currentCallers.size() != callers.size()
                    || currentEndpoints.size() != endpoints.size()) {
                return false;
            }
            for (int i = 0; i < currentCallers.size(); i++) {
                int oldIndex = callers.indexOf(currentCallers.get(i));
                if (oldIndex < 0
                        || endpoints.get(oldIndex).node != currentEndpoints.get(i).node) {
                    return false;
                }
            }
            return isAlive();
        }

        private boolean contains(TileMEChannelCaller caller) {
            return callers.contains(caller);
        }

        private boolean isAlive() {
            if (proxies.size() != callers.size()) {
                return false;
            }
            for (BridgeProxy proxy : proxies) {
                if (!proxy.isAlive()) {
                    return false;
                }
            }
            IGridNode anchor = proxies.get(0).outerNode;
            for (int i = 1; i < proxies.size(); i++) {
                IGridNode remote = proxies.get(i).outerNode;
                if (!sameGrid(anchor, remote)
                        && !hasDirectConnection(anchor, remote)) {
                    return false;
                }
            }
            return true;
        }

        private void destroy() {
            for (IGridConnection connection : new ArrayList<>(outerConnections)) {
                try {
                    connection.destroy();
                } catch (Throwable ignored) {
                }
            }
            outerConnections.clear();
            for (BridgeProxy proxy : new ArrayList<>(proxies)) {
                proxy.destroy();
            }
            proxies.clear();
            for (Endpoint endpoint : endpoints) {
                if (endpoint != null) {
                    repath(endpoint.node, endpoint.node);
                }
            }
        }
    }

    private static final class BridgeProxy {
        private final TileMEChannelCaller caller;
        private final Endpoint endpoint;
        private final AENetworkProxy channelProxy;
        private final AENetworkProxy outerProxy;
        private final List<IGridConnection> connections = new ArrayList<>();

        private BridgeProxy(TileMEChannelCaller caller, Endpoint endpoint) {
            this.caller = caller;
            this.endpoint = endpoint;
            DimensionalCoord location = new DimensionalCoord(caller.getWorld(), caller.getPos());
            EnumSet<EnumFacing> validSides = EnumSet.noneOf(EnumFacing.class);
            ProxyHost channelHost = new ProxyHost(location, validSides);
            channelProxy = new AENetworkProxy(channelHost,
                    "me_channel_caller_channel_" + caller.getPos(), ItemStack.EMPTY, false);
            channelHost.setProxy(channelProxy);
            channelProxy.setFlags(GridFlags.REQUIRE_CHANNEL, GridFlags.COMPRESSED_CHANNEL);
            channelProxy.setValidSides(validSides);

            ProxyHost outerHost = new ProxyHost(location, validSides);
            outerProxy = new AENetworkProxy(outerHost,
                    "me_channel_caller_outer_" + caller.getPos(), ItemStack.EMPTY, true);
            outerHost.setProxy(outerProxy);
            outerProxy.setFlags(GridFlags.DENSE_CAPACITY, GridFlags.CANNOT_CARRY_COMPRESSED);
            outerProxy.setValidSides(validSides);
            channelProxy.onReady();
            outerProxy.onReady();
            channelNode = channelProxy.getNode();
            outerNode = outerProxy.getNode();
        }

        private final IGridNode channelNode;
        private final IGridNode outerNode;

        private boolean attach() {
            if (endpoint.node == null || channelNode == null || outerNode == null) {
                return false;
            }
            IGridConnection channelConnection = connect(endpoint.node, channelNode,
                    AEPartLocation.INTERNAL);
            IGridConnection outerConnection = connect(endpoint.node, outerNode,
                    AEPartLocation.INTERNAL);
            if (channelConnection == null || outerConnection == null) {
                return false;
            }
            connections.add(channelConnection);
            if (outerConnection != channelConnection) connections.add(outerConnection);
            return sameGrid(endpoint.node, channelNode) && sameGrid(endpoint.node, outerNode);
        }

        private boolean isAlive() {
            return channelProxy.isActive() && outerProxy.isActive()
                    && channelNode != null && outerNode != null
                    && endpoint.node.getGrid() != null
                    && sameGrid(endpoint.node, channelNode)
                    && sameGrid(endpoint.node, outerNode);
        }

        private void destroy() {
            for (IGridConnection connection : new ArrayList<>(connections)) {
                try {
                    connection.destroy();
                } catch (Throwable ignored) {
                }
            }
            connections.clear();
            channelProxy.invalidate();
            outerProxy.invalidate();
        }
    }
}
