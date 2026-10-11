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
    private static final Map<String, String> LAST_BRIDGE_FAILURES = new HashMap<>();

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
        MEChannelCallerSavedData.prune(world);
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
        Map<UUID, ChannelGroup> byId = new HashMap<>();
        Map<TileMEChannelCaller, Endpoint> endpointsByCaller = new IdentityHashMap<>();
        Map<TileMEChannelCaller, Integer> expectedPeers = new IdentityHashMap<>();
        Map<TileMEChannelCaller, Integer> connectedPeers = new IdentityHashMap<>();
        for (TileMEChannelCaller caller : callers) {
            UUID id = caller.getChannelId();
            if (id != null) {
                ChannelGroup group = byId.get(id);
                if (group == null) {
                    group = new ChannelGroup();
                    byId.put(id, group);
                }
                (caller.isListener() ? group.listeners : group.callers).add(caller);
                endpointsByCaller.put(caller, findEndpoint(caller));
            } else {
                boolean powered = hasPoweredEndpoint(caller);
                caller.setStatus(powered ? TileMEChannelCaller.STATUS_ERROR
                                : TileMEChannelCaller.STATUS_OFF,
                        powered ? TileMEChannelCaller.REASON_MISSING
                                : TileMEChannelCaller.REASON_NONE, 0);
            }
        }

        Set<String> liveBridgeKeys = new HashSet<>();
        for (Map.Entry<UUID, ChannelGroup> entry : byId.entrySet()) {
            ChannelGroup group = entry.getValue();
            for (TileMEChannelCaller caller : group.callers) {
                for (TileMEChannelCaller listener : group.listeners) {
                    increment(expectedPeers, caller);
                    increment(expectedPeers, listener);
                }
            }

            for (TileMEChannelCaller caller : group.callers) {
                Endpoint source = endpointsByCaller.get(caller);
                List<TileMEChannelCaller> remoteListeners = new ArrayList<>();
                List<Endpoint> remoteEndpoints = new ArrayList<>();
                String key = bridgeKey(entry.getKey(), caller);
                CallerBridge bridge = BRIDGES.get(key);
                for (TileMEChannelCaller listener : group.listeners) {
                    Endpoint destination = endpointsByCaller.get(listener);
                    if (source == null || source.node == null
                            || destination == null || destination.node == null) {
                        continue;
                    }
                    // A live bridge itself makes the two endpoint grids share
                    // an AE grid. Keep that pair in the bridge input on later
                    // refreshes; otherwise the merged grid would be mistaken
                    // for an ordinary local connection and the bridge would
                    // be destroyed every ten ticks.
                    boolean alreadyBridged = bridge != null && bridge.contains(listener);
                    if (sameGrid(source.node, destination.node) && !alreadyBridged) {
                        increment(connectedPeers, caller);
                        increment(connectedPeers, listener);
                    } else {
                        remoteListeners.add(listener);
                        remoteEndpoints.add(destination);
                    }
                }

                if (remoteListeners.isEmpty()) {
                    destroyBridge(key);
                    continue;
                }
                if (bridge != null && !bridge.matches(caller, source,
                        remoteListeners, remoteEndpoints)) {
                    destroyBridge(key);
                    bridge = null;
                }
                if (bridge == null) {
                    bridge = CallerBridge.create(entry.getKey(), caller, source,
                            remoteListeners, remoteEndpoints);
                    if (bridge != null) {
                        BRIDGES.put(key, bridge);
                    }
                }
                if (bridge != null && bridge.isAlive()) {
                    liveBridgeKeys.add(key);
                    clearBridgeFailure(key);
                    for (TileMEChannelCaller listener : remoteListeners) {
                        increment(connectedPeers, caller);
                        increment(connectedPeers, listener);
                    }
                } else if (bridge != null) {
                    reportBridgeFailure(key, entry.getKey(), bridge.getFailureReason());
                }
            }
        }

        for (String key : new ArrayList<>(BRIDGES.keySet())) {
            CallerBridge bridge = BRIDGES.get(key);
            if (bridge == null || !liveBridgeKeys.contains(key)) {
                destroyBridge(key);
            }
        }

        for (TileMEChannelCaller caller : callers) {
            Endpoint endpoint = endpointsByCaller.get(caller);
            boolean powered = isPowered(endpoint);
            int expected = value(expectedPeers, caller);
            int connected = value(connectedPeers, caller);
            boolean overloaded = connected > getTransferCapacity();
            boolean complete = expected > 0 && connected >= expected && !overloaded;
            caller.setStatus(overloaded ? TileMEChannelCaller.STATUS_ERROR
                            : complete && powered ? TileMEChannelCaller.STATUS_CONNECTED
                            : powered ? TileMEChannelCaller.STATUS_ERROR
                            : TileMEChannelCaller.STATUS_OFF,
                    overloaded ? TileMEChannelCaller.REASON_OVERLOADED
                            : complete || !powered ? TileMEChannelCaller.REASON_NONE
                            : TileMEChannelCaller.REASON_MISSING,
                    overloaded ? connected : expected > 0 ? connected : 0);
        }
    }

    private static void increment(Map<TileMEChannelCaller, Integer> values,
                                  TileMEChannelCaller caller) {
        values.put(caller, value(values, caller) + 1);
    }

    private static int value(Map<TileMEChannelCaller, Integer> values,
                              TileMEChannelCaller caller) {
        Integer value = values.get(caller);
        return value == null ? 0 : value;
    }

    private static final class ChannelGroup {
        private final List<TileMEChannelCaller> callers = new ArrayList<>();
        private final List<TileMEChannelCaller> listeners = new ArrayList<>();
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

    private static String bridgeKey(UUID id, TileMEChannelCaller caller) {
        if (id == null || caller == null || caller.getWorld() == null
                || caller.getPos() == null) {
            return "";
        }
        return id + ":" + caller.getWorld().provider.getDimension()
                + ":" + caller.getPos().toLong();
    }

    private static void reportBridgeFailure(String key, UUID id, String reason) {
        if (id == null || key == null || reason == null) {
            return;
        }
        if (!reason.equals(LAST_BRIDGE_FAILURES.put(key, reason))) {
            AE2Utilix.LOGGER.warn("ME channel caller UUID " + key
                    + " cannot connect: " + reason);
        }
    }

    private static void reportBridgeFailure(UUID id, String reason) {
        reportBridgeFailure(id == null ? "" : id.toString(), id, reason);
    }

    private static void clearBridgeFailure(String key) {
        if (key != null) {
            LAST_BRIDGE_FAILURES.remove(key);
        }
    }

    private static String describeCaller(TileMEChannelCaller caller) {
        if (caller == null || caller.getWorld() == null || caller.getPos() == null) {
            return "unknown caller";
        }
        return "dimension " + caller.getWorld().provider.getDimension()
                + " at " + caller.getPos();
    }

    private static String describeEndpoint(Endpoint endpoint) {
        if (endpoint == null) {
            return "no endpoint";
        }
        return (endpoint.controller ? "controller" : endpoint.cable ? "cable" : "grid host")
                + " on " + endpoint.direction;
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
        private static CallerBridge create(UUID id, TileMEChannelCaller caller,
                                           Endpoint source,
                                           List<TileMEChannelCaller> listeners,
                                           List<Endpoint> destinations) {
            if (caller == null || source == null || listeners == null
                    || destinations == null || listeners.isEmpty()
                    || listeners.size() != destinations.size()) {
                return null;
            }
            List<TileMEChannelCaller> endpoints = new ArrayList<>();
            List<Endpoint> nodes = new ArrayList<>();
            endpoints.add(caller);
            nodes.add(source);
            endpoints.addAll(listeners);
            nodes.addAll(destinations);
            CallerBridge bridge = create(endpoints, nodes);
            if (bridge == null) {
                reportBridgeFailure(bridgeKey(id, caller), id,
                        "could not create output-to-listener P2P paths from "
                                + describeCaller(caller));
            }
            return bridge;
        }

        @Nullable
        private static CallerBridge create(List<TileMEChannelCaller> callers,
                                           List<Endpoint> endpoints) {
            if (callers == null || endpoints == null || callers.size() < 2
                    || callers.size() != endpoints.size()) {
                return null;
            }

            int sourceIndex = 0;
            Endpoint sourceEndpoint = endpoints.get(sourceIndex);
            ControllerSource controllerSource = sourceEndpoint.controller
                    ? new ControllerSource(callers.get(sourceIndex), sourceEndpoint.node,
                    callers.size() - 1)
                    : null;
            CallerBridge result = new CallerBridge(callers, endpoints, sourceIndex,
                    controllerSource);
            if (controllerSource != null && !controllerSource.attach()) {
                reportBridgeFailure(callers.get(sourceIndex).getChannelId(),
                        "controller source setup failed at "
                                + describeCaller(callers.get(sourceIndex)) + ": "
                                + controllerSource.getFailureReason());
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
                    reportBridgeFailure(callers.get(sourceIndex).getChannelId(),
                            "destination local proxy setup failed at "
                                    + describeCaller(callers.get(i)) + " ("
                                    + describeEndpoint(endpoints.get(i)) + ")");
                    result.destroy();
                    return null;
                }

                IGridNode sourceOuter;
                if (controllerSource != null) {
                    sourceOuter = controllerSource.acquireOuterNode();
                    if (sourceOuter == null) {
                        reportBridgeFailure(callers.get(sourceIndex).getChannelId(),
                                "controller source has no outer proxy for destination "
                                        + describeCaller(callers.get(i)));
                        result.destroy();
                        return null;
                    }
                } else {
                    BridgeProxy source = new BridgeProxy(callers.get(sourceIndex),
                            sourceEndpoint, "source_" + outputIndex);
                    result.sourceProxies.add(source);
                    if (!source.attachLocal()) {
                        reportBridgeFailure(callers.get(sourceIndex).getChannelId(),
                                "source local proxy setup failed at "
                                        + describeCaller(callers.get(sourceIndex)) + " ("
                                        + describeEndpoint(sourceEndpoint) + ")");
                        result.destroy();
                        return null;
                    }
                    sourceOuter = source.outerNode;
                }

                IGridConnection connection = connectOuter(sourceOuter, output.outerNode);
                if (connection == null) {
                    reportBridgeFailure(callers.get(sourceIndex).getChannelId(),
                            "could not join source " + describeCaller(callers.get(sourceIndex))
                                    + " to destination " + describeCaller(callers.get(i)));
                    result.destroy();
                    return null;
                }
                result.outerConnections.add(connection);
                outputIndex++;
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
                AE2Utilix.LOGGER.debug("ME Channel Caller P2P connection failed", error);
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

        private boolean matches(TileMEChannelCaller caller, Endpoint source,
                                List<TileMEChannelCaller> listeners,
                                List<Endpoint> destinations) {
            if (listeners == null || destinations == null
                    || listeners.size() != destinations.size()) {
                return false;
            }
            List<TileMEChannelCaller> currentCallers = new ArrayList<>();
            List<Endpoint> currentEndpoints = new ArrayList<>();
            currentCallers.add(caller);
            currentEndpoints.add(source);
            currentCallers.addAll(listeners);
            currentEndpoints.addAll(destinations);
            return matches(currentCallers, currentEndpoints);
        }

        private boolean contains(TileMEChannelCaller caller) {
            return callers.contains(caller);
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

        private String getFailureReason() {
            if (outputProxies.size() != callers.size() - 1
                    || sourceProxies.size() + (controllerSource == null ? 0 : 1)
                    != callers.size() - 1 || outerConnections.size() != callers.size() - 1) {
                return "bridge proxy or connection count is incomplete";
            }
            if (controllerSource != null && !controllerSource.isAlive()) {
                return "controller source proxies are inactive or outside the controller grid";
            }
            for (BridgeProxy proxy : sourceProxies) {
                if (!proxy.isAlive()) {
                    return "source proxy is inactive or outside its local grid";
                }
            }
            for (int i = 0; i < outputProxies.size(); i++) {
                BridgeProxy output = outputProxies.get(i);
                if (!output.isAlive()) {
                    return "destination proxy is inactive or outside its local grid at "
                            + describeCaller(callers.get(i < sourceIndex ? i : i + 1));
                }
                if (!hasDirectConnection(getSourceOuter(i), output.outerNode)) {
                    return "outer bridge connection is missing for destination "
                            + describeCaller(callers.get(i < sourceIndex ? i : i + 1));
                }
            }
            return "bridge is not alive for an unknown reason";
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
            EnumSet<EnumFacing> outerSides = getOuterProxySides(endpoint);
            ProxyHost channelHost = new ProxyHost(location, noSides);
            channelProxy = new AENetworkProxy(channelHost,
                    proxyName("me_channel_caller_channel", caller, suffix),
                    ItemStack.EMPTY, false);
            channelHost.setProxy(channelProxy);
            channelProxy.setFlags(GridFlags.REQUIRE_CHANNEL, GridFlags.COMPRESSED_CHANNEL);
            channelProxy.setValidSides(noSides);

            // The outer node is the equivalent of AE2's P2P outerProxy.  It
            // must be world-accessible on the side facing the adjacent AE2
            // host; leaving its side set empty makes onReady() create an
            // isolated node that can never join the endpoint grid.
            ProxyHost outerHost = new ProxyHost(location, outerSides);
            outerProxy = new AENetworkProxy(outerHost,
                    proxyName("me_channel_caller_outer", caller, suffix),
                    ItemStack.EMPTY, true);
            outerHost.setProxy(outerProxy);
            outerProxy.setFlags(GridFlags.DENSE_CAPACITY,
                    GridFlags.CANNOT_CARRY_COMPRESSED);
            outerProxy.setValidSides(outerSides);
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
            AEPartLocation outerDirection = endpoint.cable && endpoint.direction != null
                    ? endpoint.direction : AEPartLocation.INTERNAL;
            IGridConnection channelConnection = connect(endpoint.node, channelNode,
                    channelDirection);
            if (channelConnection == null || !sameGrid(endpoint.node, channelNode)) {
                return false;
            }

            // onReady() normally discovers the adjacent cable through the
            // outer proxy's valid side.  A controller or a non-cable host has
            // no such world-facing connection, so retain the explicit
            // fallback used by the old implementation.
            if (!sameGrid(endpoint.node, outerNode)) {
                IGridConnection outerConnection = connect(endpoint.node, outerNode,
                        outerDirection);
                if (outerConnection == null) {
                    return false;
                }
                if (outerConnection != channelConnection
                        && !connections.contains(outerConnection)) {
                    connections.add(outerConnection);
                }
            }
            if (!connections.contains(channelConnection)) {
                connections.add(channelConnection);
            }
            return sameGrid(endpoint.node, channelNode) && sameGrid(endpoint.node, outerNode);
        }

        private static EnumSet<EnumFacing> getOuterProxySides(@Nullable Endpoint endpoint) {
            if (endpoint == null || endpoint.direction == null
                    || endpoint.direction.getFacing() == null) {
                return EnumSet.noneOf(EnumFacing.class);
            }
            // Endpoint.direction is the face on the adjacent block pointing
            // back at the caller.  The synthetic world node is located at the
            // caller, so its world-facing side is the opposite face.
            return EnumSet.of(endpoint.direction.getFacing().getOpposite());
        }

        private boolean isAlive() {
            // Do not require the synthetic channel or outer node to be
            // active here. A listener can intentionally start with an
            // unpowered network and no available channel; the outer dense
            // connection is what transports the sender's power first. If
            // active() is checked at this point, the bridge destroys itself
            // before it can provide that power.
            return channelNode != null && outerNode != null
                    && endpoint.node.getGrid() != null
                    && sameGrid(endpoint.node, channelNode)
                    && sameGrid(endpoint.node, outerNode)
                    && hasDirectConnection(endpoint.node, channelNode)
                    && hasDirectConnection(endpoint.node, outerNode);
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
        private final int requiredFaces;
        private final List<ControllerFace> faces = new ArrayList<>();
        private final List<IGridConnection> connections = new ArrayList<>();
        private int nextOutput;
        private String failureReason = "controller source setup failed";

        private ControllerSource(TileMEChannelCaller caller, IGridNode controller,
                                 int requiredFaces) {
            this.caller = caller;
            this.controller = controller;
            this.requiredFaces = Math.max(1, requiredFaces);
        }

        private boolean attach() {
            DimensionalCoord controllerLocation;
            try {
                controllerLocation = controller.getGridBlock().getLocation();
            } catch (Throwable ignored) {
                failureReason = "controller grid block has no location";
                return false;
            }
            if (controllerLocation == null || controller.getGrid() == null) {
                failureReason = "controller has no location or is not in an AE grid";
                return false;
            }

            for (EnumFacing facing : CHANNEL_DIRECTION_ORDER) {
                if (faces.size() >= requiredFaces) {
                    break;
                }
                AEPartLocation face = AEPartLocation.fromFacing(facing);
                if (isControllerFaceUsed(controller, face)) {
                    continue;
                }
                ControllerFace proxies = createFaceProxies(face, controllerLocation);
                if (proxies == null) {
                    failureReason = "could not create proxies for controller face " + face;
                    destroy();
                    return false;
                }
                faces.add(proxies);

                IGridConnection channelConnection = connect(controller,
                        proxies.channel.getNode(), face);
                if (channelConnection == null) {
                    failureReason = "could not connect channel proxy on controller face " + face;
                    destroy();
                    return false;
                }
                connections.add(channelConnection);

                IGridConnection branchConnection = connect(proxies.channel.getNode(),
                        proxies.outer.getNode(), AEPartLocation.INTERNAL);
                if (branchConnection == null) {
                    failureReason = "could not connect outer proxy to channel proxy on face "
                            + face;
                    destroy();
                    return false;
                }
                connections.add(branchConnection);
                repath(proxies.channel.getNode(), proxies.outer.getNode());
                if (!sameGrid(controller, proxies.channel.getNode())
                        || !sameGrid(controller, proxies.outer.getNode())) {
                    failureReason = "controller proxy pair did not join the source grid on face "
                            + face;
                    destroy();
                    return false;
                }
            }
            if (faces.size() < requiredFaces) {
                failureReason = "controller has " + faces.size()
                        + " unused faces but " + requiredFaces
                        + " listener paths are required";
                return false;
            }
            return true;
        }

        private String getFailureReason() {
            if (faces.size() < requiredFaces) {
                return "controller has " + faces.size()
                        + " attached proxy faces but " + requiredFaces
                        + " listener paths are required";
            }
            if (controller.getGrid() == null) {
                return "controller is no longer in an AE grid";
            }
            for (ControllerFace face : faces) {
                IGridNode channel = face.channel.getNode();
                IGridNode outer = face.outer.getNode();
                if (!face.channel.isActive() || channel == null) {
                    return "channel proxy is inactive on controller face " + face.direction;
                }
                if (!face.outer.isActive() || outer == null) {
                    return "outer proxy is inactive on controller face " + face.direction;
                }
                if (!sameGrid(controller, channel)) {
                    return "channel proxy is outside the source grid on face " + face.direction;
                }
                if (!sameGrid(controller, outer)) {
                    return "outer proxy is outside the source grid on face " + face.direction;
                }
                if (!hasDirectConnection(controller, channel)) {
                    return "controller-to-channel edge is missing on face " + face.direction;
                }
                if (!hasDirectConnection(channel, outer)) {
                    return "channel-to-outer edge is missing on face " + face.direction;
                }
            }
            return failureReason;
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
            if (faces.size() < requiredFaces || controller.getGrid() == null) {
                return false;
            }
            for (ControllerFace face : faces) {
                if (!face.channel.isActive() || !face.outer.isActive()
                        || !sameGrid(controller, face.channel.getNode())
                        || !sameGrid(controller, face.outer.getNode())
                        || !hasDirectConnection(controller, face.channel.getNode())
                        || !hasDirectConnection(face.channel.getNode(), face.outer.getNode())) {
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
