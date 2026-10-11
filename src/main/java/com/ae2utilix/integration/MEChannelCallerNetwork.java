package com.ae2utilix.integration;

import appeng.api.AEApi;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.me.GridConnection;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.tile.networking.TileCableBus;
import appeng.tile.networking.TileController;
import com.ae2utilix.AE2Utilix;
import com.ae2utilix.block.TileMEChannelCaller;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
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

            if (bridge != null && !bridge.matches(activeCallers, endpoints)) {
                destroyBridge(key);
                bridge = null;
            }

            boolean alreadyConnected = allSameGrid(endpoints);
            if (bridge == null && !alreadyConnected) {
                bridge = CallerBridge.create(activeCallers, endpoints);
                if (bridge != null) {
                    BRIDGES.put(key, bridge);
                }
            }

            if (bridge != null) {
                liveBridgeKeys.add(key);
            }
            boolean connected = alreadyConnected || (bridge != null && bridge.isAlive());
            for (int i = 0; i < activeCallers.size(); i++) {
                TileMEChannelCaller caller = activeCallers.get(i);
                Endpoint endpoint = endpoints.get(i);
                boolean powered = isPowered(endpoint);
                int transferredChannels = bridge == null ? 0 : bridge.getTransferCount();
                boolean overloaded = connected
                        && transferredChannels > getTransferCapacity();
                caller.setStatus(overloaded ? TileMEChannelCaller.STATUS_ERROR
                                : connected && powered ? TileMEChannelCaller.STATUS_CONNECTED
                                : connected ? TileMEChannelCaller.STATUS_OFF
                                : powered ? TileMEChannelCaller.STATUS_ERROR
                                : TileMEChannelCaller.STATUS_OFF,
                        overloaded ? TileMEChannelCaller.REASON_OVERLOADED
                                : connected || !powered ? TileMEChannelCaller.REASON_NONE
                                : TileMEChannelCaller.REASON_MISSING,
                        transferredChannels);
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

        Endpoint first = null;
        // ChannelSparkNetwork targets the block the spark is physically
        // attached to. Resolve only directly adjacent blocks, never arbitrary
        // nodes found elsewhere in the connected AE grid.
        for (EnumFacing callerSide : CHANNEL_DIRECTION_ORDER) {
            BlockPos targetPos = caller.getPos().offset(callerSide);
            TileEntity target = caller.getWorld().getTileEntity(targetPos);
            Endpoint candidate = findAdjacentEndpoint(target, callerSide.getOpposite());
            if (candidate == null) {
                continue;
            }
            if (candidate.controller) {
                return candidate;
            }
            if (first == null) {
                first = candidate;
            }
        }
        return first;
    }

    @Nullable
    private static Endpoint findAdjacentEndpoint(@Nullable TileEntity target,
                                                 EnumFacing targetSide) {
        if (target == null) {
            return null;
        }
        AEPartLocation side = AEPartLocation.fromFacing(targetSide);

        if (target instanceof TileCableBus) {
            TileCableBus cable = (TileCableBus) target;
            IGridNode node = cable.getGridNode(side);
            if (node == null) {
                node = cable.getGridNode(AEPartLocation.INTERNAL);
            }
            return isUsableEndpoint(node)
                    ? new Endpoint(node, false, true, side) : null;
        }

        if (target instanceof IPartHost) {
            IPart part = ((IPartHost) target).getPart(targetSide);
            if (part != null) {
                IGridNode node = part.getExternalFacingNode();
                if (node == null) {
                    node = part.getGridNode();
                }
                if (isUsableEndpoint(node)) {
                    return new Endpoint(node, false, false, side);
                }
            }
        }

        if (target instanceof IGridHost) {
            IGridHost host = (IGridHost) target;
            boolean controller = target instanceof TileController;
            IGridNode node;
            try {
                node = host.getGridNode(side);
                if (node == null && !controller) {
                    node = host.getGridNode(AEPartLocation.INTERNAL);
                }
            } catch (Throwable ignored) {
                return null;
            }
            if (controller && !isControllerNode(node)) {
                return null;
            }
            if (isUsableEndpoint(node)) {
                return new Endpoint(node, controller, false, side);
            }
        }
        return null;
    }

    private static boolean isControllerNode(@Nullable IGridNode node) {
        try {
            return node != null && node.hasFlag(GridFlags.DENSE_CAPACITY);
        } catch (Throwable ignored) {
            return false;
        }
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
        private final boolean controller;
        private final boolean cable;
        private final AEPartLocation direction;

        private Endpoint(IGridNode node, boolean controller, boolean cable,
                         AEPartLocation direction) {
            this.node = node;
            this.controller = controller;
            this.cable = cable;
            this.direction = direction;
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

    /** One AE2 P2P-style channel bridge for all callers sharing a UUID. */
    private static final class CallerBridge {
        private final List<TileMEChannelCaller> callers;
        private final List<Endpoint> endpoints;
        private final int sourceIndex;
        private final ControllerSource controllerSource;
        private final List<BridgeProxy> sourceProxies = new ArrayList<>();
        private final List<BridgeProxy> outputProxies = new ArrayList<>();
        private final List<IGridConnection> outerConnections = new ArrayList<>();

        private CallerBridge(List<TileMEChannelCaller> callers,
                             List<Endpoint> endpoints, int sourceIndex,
                             @Nullable ControllerSource controllerSource) {
            this.callers = new ArrayList<>(callers);
            this.endpoints = new ArrayList<>(endpoints);
            this.sourceIndex = sourceIndex;
            this.controllerSource = controllerSource;
        }

        @Nullable
        private static CallerBridge create(List<TileMEChannelCaller> callers,
                                           List<Endpoint> endpoints) {
            if (callers == null || endpoints == null || callers.size() < 2
                    || callers.size() != endpoints.size()) {
                return null;
            }

            int sourceIndex = selectSourceIndex(callers, endpoints);
            Endpoint sourceEndpoint = endpoints.get(sourceIndex);
            ControllerSource controllerSource = sourceEndpoint.controller
                    ? new ControllerSource(callers.get(sourceIndex), sourceEndpoint.node)
                    : null;
            CallerBridge result = new CallerBridge(callers, endpoints, sourceIndex,
                    controllerSource);
            if (controllerSource != null && !controllerSource.attach()) {
                result.destroy();
                return null;
            }

            int outputIndex = 0;
            for (int i = 0; i < callers.size(); i++) {
                if (i == sourceIndex) {
                    continue;
                }
                BridgeProxy output = new BridgeProxy(callers.get(i), endpoints.get(i),
                        "output_" + outputIndex);
                result.outputProxies.add(output);
                if (!output.attachLocal()) {
                    result.destroy();
                    return null;
                }

                IGridNode sourceOuter;
                if (controllerSource != null) {
                    sourceOuter = controllerSource.acquireOuterNode();
                    if (sourceOuter == null) {
                        result.destroy();
                        return null;
                    }
                } else {
                    BridgeProxy source = new BridgeProxy(callers.get(sourceIndex),
                            sourceEndpoint, "source_" + outputIndex);
                    result.sourceProxies.add(source);
                    if (!source.attachLocal()) {
                        result.destroy();
                        return null;
                    }
                    sourceOuter = source.outerNode;
                }

                IGridConnection connection = connectOuter(sourceOuter, output.outerNode);
                if (connection == null) {
                    result.destroy();
                    return null;
                }
                result.outerConnections.add(connection);
                outputIndex++;
            }
            return result;
        }

        private static int selectSourceIndex(List<TileMEChannelCaller> callers,
                                             List<Endpoint> endpoints) {
            int best = 0;
            int bestScore = Integer.MIN_VALUE;
            for (int i = 0; i < endpoints.size(); i++) {
                Endpoint endpoint = endpoints.get(i);
                int score = isPowered(endpoint) ? 100 : 0;
                if (endpoint.controller) {
                    score += 1000 * countUnusedControllerFaces(endpoint.node);
                }
                if (score > bestScore || (score == bestScore
                        && compareCallerPosition(callers.get(i), callers.get(best)) < 0)) {
                    best = i;
                    bestScore = score;
                }
            }
            return best;
        }

        private static int compareCallerPosition(TileMEChannelCaller first,
                                                 TileMEChannelCaller second) {
            int comparison = Integer.compare(first.getWorld().provider.getDimension(),
                    second.getWorld().provider.getDimension());
            if (comparison != 0) return comparison;
            comparison = Integer.compare(first.getPos().getX(), second.getPos().getX());
            if (comparison != 0) return comparison;
            comparison = Integer.compare(first.getPos().getY(), second.getPos().getY());
            return comparison != 0 ? comparison
                    : Integer.compare(first.getPos().getZ(), second.getPos().getZ());
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
                        || endpoints.get(oldIndex).node != currentEndpoints.get(i).node
                        || endpoints.get(oldIndex).controller != currentEndpoints.get(i).controller
                        || endpoints.get(oldIndex).direction != currentEndpoints.get(i).direction) {
                    return false;
                }
            }
            return isAlive();
        }

        private boolean contains(TileMEChannelCaller caller) {
            return callers.contains(caller);
        }

        private int getTransferCount() {
            return outerConnections.size();
        }

        private boolean isAlive() {
            if (outputProxies.size() != callers.size() - 1
                    || sourceProxies.size() + (controllerSource == null ? 0 : 1)
                    != callers.size() - 1 || outerConnections.size() != callers.size() - 1) {
                return false;
            }
            if (controllerSource != null && !controllerSource.isAlive()) {
                return false;
            }
            for (BridgeProxy proxy : sourceProxies) {
                if (!proxy.isAlive()) {
                    return false;
                }
            }
            for (int i = 0; i < outputProxies.size(); i++) {
                BridgeProxy output = outputProxies.get(i);
                if (!output.isAlive()
                        || !hasDirectConnection(getSourceOuter(i), output.outerNode)) {
                    return false;
                }
            }
            return true;
        }

        @Nullable
        private IGridNode getSourceOuter(int outputIndex) {
            if (controllerSource != null) {
                return controllerSource.getOuterNode(outputIndex);
            }
            return outputIndex >= 0 && outputIndex < sourceProxies.size()
                    ? sourceProxies.get(outputIndex).outerNode : null;
        }

        private void destroy() {
            for (IGridConnection connection : new ArrayList<>(outerConnections)) {
                try {
                    connection.destroy();
                } catch (Throwable ignored) {
                }
            }
            outerConnections.clear();
            for (BridgeProxy proxy : new ArrayList<>(sourceProxies)) {
                proxy.destroy();
            }
            sourceProxies.clear();
            for (BridgeProxy proxy : new ArrayList<>(outputProxies)) {
                proxy.destroy();
            }
            outputProxies.clear();
            if (controllerSource != null) {
                controllerSource.destroy();
            }
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

        private BridgeProxy(TileMEChannelCaller caller, Endpoint endpoint,
                            String suffix) {
            this.caller = caller;
            this.endpoint = endpoint;
            DimensionalCoord location = new DimensionalCoord(caller.getWorld(), caller.getPos());
            EnumSet<EnumFacing> noSides = EnumSet.noneOf(EnumFacing.class);
            ProxyHost channelHost = new ProxyHost(location, noSides);
            channelProxy = new AENetworkProxy(channelHost,
                    proxyName("me_channel_caller_channel", caller, suffix),
                    ItemStack.EMPTY, false);
            channelHost.setProxy(channelProxy);
            channelProxy.setFlags(GridFlags.REQUIRE_CHANNEL, GridFlags.COMPRESSED_CHANNEL);
            channelProxy.setValidSides(noSides);

            ProxyHost outerHost = new ProxyHost(location, noSides);
            outerProxy = new AENetworkProxy(outerHost,
                    proxyName("me_channel_caller_outer", caller, suffix),
                    ItemStack.EMPTY, true);
            outerHost.setProxy(outerProxy);
            outerProxy.setFlags(GridFlags.DENSE_CAPACITY,
                    GridFlags.CANNOT_CARRY_COMPRESSED);
            outerProxy.setValidSides(noSides);
            channelProxy.onReady();
            outerProxy.onReady();
            channelNode = channelProxy.getNode();
            outerNode = outerProxy.getNode();
        }

        private final IGridNode channelNode;
        private final IGridNode outerNode;

        private boolean attachLocal() {
            if (endpoint.node == null || channelNode == null || outerNode == null) {
                return false;
            }
            AEPartLocation channelDirection = endpoint.cable
                    ? endpoint.direction : AEPartLocation.INTERNAL;
            AEPartLocation outerDirection = endpoint.direction == null
                    ? AEPartLocation.INTERNAL : endpoint.direction;
            IGridConnection channelConnection = connect(endpoint.node, channelNode,
                    channelDirection);
            IGridConnection outerConnection = connect(endpoint.node, outerNode, outerDirection);
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

    /** Supplies one P2P channel path per free face on the source controller. */
    private static final class ControllerSource {
        private final TileMEChannelCaller caller;
        private final IGridNode controller;
        private final List<ControllerFace> faces = new ArrayList<>();
        private final List<IGridConnection> connections = new ArrayList<>();
        private int nextOutput;

        private ControllerSource(TileMEChannelCaller caller, IGridNode controller) {
            this.caller = caller;
            this.controller = controller;
        }

        private boolean attach() {
            DimensionalCoord controllerLocation;
            try {
                controllerLocation = controller.getGridBlock().getLocation();
            } catch (Throwable ignored) {
                return false;
            }
            if (controllerLocation == null || controller.getGrid() == null) {
                return false;
            }

            for (EnumFacing facing : CHANNEL_DIRECTION_ORDER) {
                AEPartLocation face = AEPartLocation.fromFacing(facing);
                if (isControllerFaceUsed(controller, face)) {
                    continue;
                }
                ControllerFace proxies = createFaceProxies(face, controllerLocation);
                if (proxies == null) {
                    destroy();
                    return false;
                }
                faces.add(proxies);

                IGridConnection channelConnection = connect(controller,
                        proxies.channel.getNode(), face);
                if (channelConnection == null) {
                    destroy();
                    return false;
                }
                connections.add(channelConnection);

                // The outer proxy is world-accessible and joins the controller
                // grid through AE2's neighbour discovery. Explicitly attaching
                // both nodes to this face competes with the channel proxy for
                // the controller's single face connection and can reject the
                // entire bridge.
                repath(controller, proxies.outer.getNode());
                if (!sameGrid(controller, proxies.channel.getNode())) {
                    destroy();
                    return false;
                }
            }
            return !faces.isEmpty();
        }

        @Nullable
        private ControllerFace createFaceProxies(AEPartLocation face,
                                                 DimensionalCoord controllerLocation) {
            EnumFacing outward = face.getFacing();
            BlockPos position = new BlockPos(
                    controllerLocation.x + outward.getFrontOffsetX(),
                    controllerLocation.y + outward.getFrontOffsetY(),
                    controllerLocation.z + outward.getFrontOffsetZ());
            DimensionalCoord location = new DimensionalCoord(
                    controllerLocation.getWorld(), position);
            EnumSet<EnumFacing> sides = EnumSet.of(outward.getOpposite());
            String suffix = face.toString();

            ProxyHost channelHost = new ProxyHost(location, sides);
            AENetworkProxy channel = new AENetworkProxy(channelHost,
                    proxyName("me_channel_caller_controller_channel", caller, suffix),
                    ItemStack.EMPTY, true);
            channelHost.setProxy(channel);
            channel.setFlags(GridFlags.REQUIRE_CHANNEL, GridFlags.COMPRESSED_CHANNEL);
            channel.setValidSides(sides);

            ProxyHost outerHost = new ProxyHost(location, sides);
            AENetworkProxy outer = new AENetworkProxy(outerHost,
                    proxyName("me_channel_caller_controller_outer", caller, suffix),
                    ItemStack.EMPTY, true);
            outerHost.setProxy(outer);
            outer.setFlags(GridFlags.DENSE_CAPACITY,
                    GridFlags.CANNOT_CARRY_COMPRESSED);
            outer.setValidSides(sides);
            channel.onReady();
            outer.onReady();
            if (channel.getNode() == null || outer.getNode() == null) {
                channel.invalidate();
                outer.invalidate();
                return null;
            }
            return new ControllerFace(face, channel, outer);
        }

        @Nullable
        private IGridNode acquireOuterNode() {
            if (nextOutput >= faces.size()) {
                return null;
            }
            return faces.get(nextOutput++).outer.getNode();
        }

        @Nullable
        private IGridNode getOuterNode(int index) {
            return index >= 0 && index < faces.size()
                    ? faces.get(index).outer.getNode() : null;
        }

        private boolean isAlive() {
            if (faces.isEmpty() || controller.getGrid() == null) {
                return false;
            }
            for (ControllerFace face : faces) {
                if (!face.channel.isActive() || !face.outer.isActive()
                        || !sameGrid(controller, face.channel.getNode())
                        || !sameGrid(controller, face.outer.getNode())
                        || !hasDirectConnection(controller, face.channel.getNode())) {
                    return false;
                }
            }
            return true;
        }

        private void destroy() {
            for (IGridConnection connection : new ArrayList<>(connections)) {
                try {
                    connection.destroy();
                } catch (Throwable ignored) {
                }
            }
            connections.clear();
            for (ControllerFace face : new ArrayList<>(faces)) {
                face.channel.invalidate();
                face.outer.invalidate();
            }
            faces.clear();
            nextOutput = 0;
        }
    }

    private static final class ControllerFace {
        private final AEPartLocation direction;
        private final AENetworkProxy channel;
        private final AENetworkProxy outer;

        private ControllerFace(AEPartLocation direction, AENetworkProxy channel,
                               AENetworkProxy outer) {
            this.direction = direction;
            this.channel = channel;
            this.outer = outer;
        }
    }

    private static boolean isControllerFaceUsed(IGridNode controller,
                                                AEPartLocation face) {
        if (controller == null || face == null || face.getFacing() == null) {
            return true;
        }
        DimensionalCoord location = null;
        try {
            location = controller.getGridBlock().getLocation();
        } catch (Throwable ignored) {
        }
        EnumFacing facing = face.getFacing();
        try {
            for (IGridConnection connection : controller.getConnections()) {
                if (connection == null) {
                    continue;
                }
                IGridNode other = connection.getOtherSide(controller);
                if (isProxyNode(other)) {
                    continue;
                }
                if (connection.getDirection(controller) == face) {
                    return true;
                }
                if (location != null && other != null && other.getGridBlock() != null) {
                    DimensionalCoord otherLocation = other.getGridBlock().getLocation();
                    if (otherLocation != null
                            && otherLocation.x == location.x + facing.getFrontOffsetX()
                            && otherLocation.y == location.y + facing.getFrontOffsetY()
                            && otherLocation.z == location.z + facing.getFrontOffsetZ()) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
            return true;
        }
        return false;
    }

    private static int countUnusedControllerFaces(IGridNode controller) {
        int count = 0;
        for (EnumFacing facing : CHANNEL_DIRECTION_ORDER) {
            if (!isControllerFaceUsed(controller, AEPartLocation.fromFacing(facing))) {
                count++;
            }
        }
        return count;
    }

    private static boolean isProxyNode(@Nullable IGridNode node) {
        try {
            return node != null && node.getGridBlock() != null
                    && node.getGridBlock().getMachine() instanceof ProxyHost;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String proxyName(String type, TileMEChannelCaller caller,
                                    String suffix) {
        return type + "_" + caller.getWorld().provider.getDimension() + "_"
                + caller.getPos().getX() + "_" + caller.getPos().getY() + "_"
                + caller.getPos().getZ() + "_" + suffix;
    }
}
