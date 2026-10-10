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
 * proxy is attached to each local network, while the two dense outer proxies
 * are connected to each other. This keeps the wireless edge from consuming a
 * normal channel for every path crossing it.</p>
 */
public final class MEChannelCallerNetwork {
    private static final long REFRESH_INTERVAL = 10L;
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
            List<TileMEChannelCaller> pair = entry.getValue();
            if (pair.size() != 2) {
                for (TileMEChannelCaller caller : pair) {
                    boolean powered = hasPoweredEndpoint(caller);
                    caller.setStatus(powered ? TileMEChannelCaller.STATUS_ERROR
                                    : TileMEChannelCaller.STATUS_OFF,
                            powered ? (pair.size() > 2 ? TileMEChannelCaller.REASON_AMBIGUOUS
                                    : TileMEChannelCaller.REASON_MISSING)
                                    : TileMEChannelCaller.REASON_NONE, 0);
                }
                destroyBridge(bridgeKey(entry.getKey()));
                continue;
            }

            TileMEChannelCaller first = pair.get(0);
            TileMEChannelCaller second = pair.get(1);
            Endpoint firstEndpoint = findEndpoint(first);
            Endpoint secondEndpoint = findEndpoint(second);
            boolean firstPowered = isPowered(firstEndpoint);
            boolean secondPowered = isPowered(secondEndpoint);
            String key = bridgeKey(entry.getKey());
            CallerBridge bridge = BRIDGES.get(key);

            if (firstEndpoint == null || secondEndpoint == null) {
                destroyBridge(key);
                first.setStatus(firstPowered ? TileMEChannelCaller.STATUS_ERROR
                                : TileMEChannelCaller.STATUS_OFF,
                        firstPowered ? TileMEChannelCaller.REASON_MISSING
                                : TileMEChannelCaller.REASON_NONE, 0);
                second.setStatus(secondPowered ? TileMEChannelCaller.STATUS_ERROR
                                : TileMEChannelCaller.STATUS_OFF,
                        secondPowered ? TileMEChannelCaller.REASON_MISSING
                                : TileMEChannelCaller.REASON_NONE, 0);
                continue;
            }

            boolean overloadedForFirst = countChannelUsers(secondEndpoint.node) > getTransferCapacity();
            boolean overloadedForSecond = countChannelUsers(firstEndpoint.node) > getTransferCapacity();

            if (overloadedForFirst || overloadedForSecond) {
                destroyBridge(key);
                bridge = null;
            }

            if (!firstPowered || !secondPowered) {
                destroyBridge(key);
                first.setStatus(TileMEChannelCaller.STATUS_OFF, TileMEChannelCaller.REASON_NONE, 0);
                second.setStatus(TileMEChannelCaller.STATUS_OFF, TileMEChannelCaller.REASON_NONE, 0);
                continue;
            }

            if (sameGrid(firstEndpoint.node, secondEndpoint.node)) {
                // The endpoints can become part of the same local network
                // after a cable change.  A previously-created wireless
                // bridge is no longer needed in that case.
                destroyBridge(key);
                bridge = null;
            } else if (!overloadedForFirst && !overloadedForSecond
                    && (bridge == null || !bridge.matches(firstEndpoint, secondEndpoint))) {
                destroyBridge(key);
                bridge = CallerBridge.create(first, firstEndpoint, second, secondEndpoint);
                if (bridge != null) {
                    BRIDGES.put(key, bridge);
                }
            }

            if (bridge != null) {
                liveBridgeKeys.add(key);
            }
            boolean connected = sameGrid(firstEndpoint.node, secondEndpoint.node)
                    || (bridge != null && bridge.isAlive());
            int firstRemoteChannels = countChannelUsers(secondEndpoint.node);
            int secondRemoteChannels = countChannelUsers(firstEndpoint.node);
            first.setStatus(connected && !overloadedForFirst
                            ? TileMEChannelCaller.STATUS_CONNECTED : TileMEChannelCaller.STATUS_ERROR,
                    overloadedForFirst ? TileMEChannelCaller.REASON_OVERLOADED
                            : connected ? TileMEChannelCaller.REASON_NONE
                            : TileMEChannelCaller.REASON_MISSING,
                    firstRemoteChannels);
            second.setStatus(connected && !overloadedForSecond
                            ? TileMEChannelCaller.STATUS_CONNECTED : TileMEChannelCaller.STATUS_ERROR,
                    overloadedForSecond ? TileMEChannelCaller.REASON_OVERLOADED
                            : connected ? TileMEChannelCaller.REASON_NONE
                            : TileMEChannelCaller.REASON_MISSING,
                    secondRemoteChannels);
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
        World world = caller.getWorld();
        BlockPos origin = caller.getPos();
        for (EnumFacing side : EnumFacing.values()) {
            BlockPos adjacent = origin.offset(side);
            TileEntity target = world.getTileEntity(adjacent);
            Endpoint endpoint = findEndpoint(target, side.getOpposite());
            if (endpoint != null && endpoint.node.getGrid() != null) {
                return endpoint;
            }
        }
        return null;
    }

    @Nullable
    private static Endpoint findEndpoint(@Nullable TileEntity target, EnumFacing targetSide) {
        if (target == null) {
            return null;
        }
        AEPartLocation side = AEPartLocation.fromFacing(targetSide);

        if (target instanceof IPartHost) {
            IPart part = ((IPartHost) target).getPart(targetSide);
            if (part != null) {
                IGridNode node = part.getExternalFacingNode();
                if (node == null) node = part.getGridNode();
                if (node != null && node.getGrid() != null) {
                    return new Endpoint(node, side, isController(node));
                }
            }
        }

        if (target instanceof TileCableBus) {
            TileCableBus cable = (TileCableBus) target;
            IGridNode node = cable.getGridNode(side);
            if (node == null) node = cable.getGridNode(AEPartLocation.INTERNAL);
            if (node != null && node.getGrid() != null) {
                return new Endpoint(node, side, false);
            }
        }

        if (target instanceof IGridHost) {
            IGridHost host = (IGridHost) target;
            IGridNode node = host.getGridNode(side);
            if (node == null) node = host.getGridNode(AEPartLocation.INTERNAL);
            if (node != null && node.getGrid() != null) {
                return new Endpoint(node, side, isController(node));
            }
        }
        return null;
    }

    private static boolean isController(IGridNode node) {
        try {
            return node.hasFlag(GridFlags.DENSE_CAPACITY)
                    && node.hasFlag(GridFlags.CANNOT_CARRY);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean sameGrid(IGridNode first, IGridNode second) {
        return first != null && second != null && first.getGrid() != null
                && first.getGrid() == second.getGrid();
    }

    private static String bridgeKey(UUID id) {
        return id == null ? "" : id.toString();
    }

    private static void removeBridgesFor(TileMEChannelCaller caller) {
        for (String key : new ArrayList<>(BRIDGES.keySet())) {
            CallerBridge bridge = BRIDGES.get(key);
            if (bridge != null && (bridge.first == caller || bridge.second == caller)) {
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
        private final AEPartLocation direction;
        private final boolean controller;

        private Endpoint(IGridNode node, AEPartLocation direction, boolean controller) {
            this.node = node;
            this.direction = direction;
            this.controller = controller;
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

    private static final class CallerBridge {
        private final TileMEChannelCaller first;
        private final TileMEChannelCaller second;
        private final Endpoint firstEndpoint;
        private final Endpoint secondEndpoint;
        private final BridgeProxy firstProxy;
        private final BridgeProxy secondProxy;
        @Nullable
        private IGridConnection outerConnection;

        private CallerBridge(TileMEChannelCaller first, Endpoint firstEndpoint,
                             TileMEChannelCaller second, Endpoint secondEndpoint,
                             BridgeProxy firstProxy, BridgeProxy secondProxy) {
            this.first = first;
            this.second = second;
            this.firstEndpoint = firstEndpoint;
            this.secondEndpoint = secondEndpoint;
            this.firstProxy = firstProxy;
            this.secondProxy = secondProxy;
        }

        @Nullable
        private static CallerBridge create(TileMEChannelCaller first, Endpoint firstEndpoint,
                                           TileMEChannelCaller second, Endpoint secondEndpoint) {
            BridgeProxy firstProxy = new BridgeProxy(first, firstEndpoint);
            BridgeProxy secondProxy = new BridgeProxy(second, secondEndpoint);
            CallerBridge result = new CallerBridge(first, firstEndpoint, second, secondEndpoint,
                    firstProxy, secondProxy);
            if (!firstProxy.attach() || !secondProxy.attach()) {
                result.destroy();
                return null;
            }
            try {
                result.outerConnection = AEApi.instance().grid().createGridConnection(
                        firstProxy.outerNode, secondProxy.outerNode);
                repath(firstProxy.outerNode, secondProxy.outerNode);
                return result;
            } catch (Throwable error) {
                AE2Utilix.LOGGER.debug("ME Channel Caller outer AE connection failed", error);
                result.destroy();
                return null;
            }
        }

        private boolean matches(Endpoint firstEndpoint, Endpoint secondEndpoint) {
            return this.firstEndpoint.node == firstEndpoint.node
                    && this.secondEndpoint.node == secondEndpoint.node
                    && isAlive();
        }

        private boolean isAlive() {
            return firstProxy.isAlive() && secondProxy.isAlive()
                    && (sameGrid(firstEndpoint.node, secondEndpoint.node)
                    || (outerConnection != null
                    && hasDirectConnection(firstProxy.outerNode, secondProxy.outerNode)));
        }

        private void destroy() {
            if (outerConnection != null) {
                try {
                    outerConnection.destroy();
                } catch (Throwable ignored) {
                }
                outerConnection = null;
            }
            firstProxy.destroy();
            secondProxy.destroy();
            repath(firstEndpoint.node, secondEndpoint.node);
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
            if (endpoint.controller && endpoint.direction != null
                    && endpoint.direction.getFacing() != null) {
                validSides.add(endpoint.direction.getFacing().getOpposite());
            }
            ProxyHost channelHost = new ProxyHost(location, validSides);
            channelProxy = new AENetworkProxy(channelHost,
                    "me_channel_caller_channel_" + caller.getPos(), ItemStack.EMPTY, endpoint.controller);
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
            IGridConnection channelConnection = connect(endpoint.node, channelNode, endpoint.direction);
            IGridConnection outerConnection = connect(endpoint.node, outerNode, endpoint.direction);
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
