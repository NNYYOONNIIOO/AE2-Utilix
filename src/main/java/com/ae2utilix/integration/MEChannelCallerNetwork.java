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
import appeng.core.AEConfig;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Wires ME channel callers (senders) and ME channel listeners (receivers) into UUID
 * scoped links.
 *
 * <p>Every caller and listener owns one port per ME block it physically touches, and the
 * ports of one UUID are joined directly to each other. A sender port is a compressed
 * channel consumer, so it draws channels from exactly the touched face; a receiver port is
 * a dense channel carrier, so the touched block receives those channels. There is no
 * artificial channel limit in this class: how many channels travel is decided by AE2
 * itself, which reads its own limits (including the configured controller capacity) from
 * {@link AEConfig}. One UUID may hold any number of callers and listeners.</p>
 *
 * <p>Synthetic nodes are never world accessible and are never placed on a controller face
 * the mod does not physically touch, so a network that is not touched by a caller or
 * listener keeps its channel allocation untouched.</p>
 */
public final class MEChannelCallerNetwork {
    private static final long REFRESH_INTERVAL = 10L;
    private static final int MAX_GRID_SCAN = 4096;
    /** Only used when AE2's own configuration cannot be read. */
    private static final int FALLBACK_DENSE_CAPACITY = 32;
    private static final int FALLBACK_NORMAL_CAPACITY = 8;

    /** Deterministic face order, so a given world state always yields the same ports. */
    private static final EnumFacing[] FACE_ORDER = {
            EnumFacing.EAST, EnumFacing.UP, EnumFacing.SOUTH,
            EnumFacing.WEST, EnumFacing.DOWN, EnumFacing.NORTH
    };

    private static final Map<World, Set<TileMEChannelCaller>> CALLERS =
            new WeakHashMap<World, Set<TileMEChannelCaller>>();
    private static final Map<World, Long> NEXT_REFRESH = new WeakHashMap<World, Long>();
    private static final Set<World> DIRTY_WORLDS =
            Collections.newSetFromMap(new WeakHashMap<World, Boolean>());
    private static final Map<UUID, ChannelGroup> GROUPS = new HashMap<UUID, ChannelGroup>();

    private static final Comparator<TileMEChannelCaller> CALLER_ORDER =
            new Comparator<TileMEChannelCaller>() {
                @Override
                public int compare(TileMEChannelCaller first, TileMEChannelCaller second) {
                    int firstDimension = first == null || first.getWorld() == null
                            ? Integer.MIN_VALUE : first.getWorld().provider.getDimension();
                    int secondDimension = second == null || second.getWorld() == null
                            ? Integer.MIN_VALUE : second.getWorld().provider.getDimension();
                    if (firstDimension != secondDimension) {
                        return firstDimension < secondDimension ? -1 : 1;
                    }
                    long firstPosition = first == null || first.getPos() == null
                            ? Long.MIN_VALUE : first.getPos().toLong();
                    long secondPosition = second == null || second.getPos() == null
                            ? Long.MIN_VALUE : second.getPos().toLong();
                    if (firstPosition != secondPosition) {
                        return firstPosition < secondPosition ? -1 : 1;
                    }
                    return Integer.compare(System.identityHashCode(first),
                            System.identityHashCode(second));
                }
            };

    private MEChannelCallerNetwork() {
    }

    /** AE2's configured channel capacity per dense link; never a value of our own. */
    private static int denseChannelCapacity() {
        try {
            return Math.max(1, AEConfig.instance().getDenseChannelCapacity());
        } catch (Throwable ignored) {
            return FALLBACK_DENSE_CAPACITY;
        }
    }

    /** AE2's configured channel capacity per normal (non dense) link. */
    private static int normalChannelCapacity() {
        try {
            return Math.max(1, AEConfig.instance().getNormalChannelCapacity());
        } catch (Throwable ignored) {
            return FALLBACK_NORMAL_CAPACITY;
        }
    }

    /**
     * Channels one touched face can actually provide, read from that face's own node
     * instead of assuming the configured maximum: a face on a normal cable only supplies
     * the normal capacity, a face on dense cable or a controller supplies the dense
     * capacity, and a node that cannot carry channels supplies none.
     */
    private static int faceChannelCapacity(@Nullable IGridNode node) {
        try {
            if (node == null) {
                return 0;
            }
            if (node.hasFlag(GridFlags.CANNOT_CARRY)) {
                // A controller cannot carry channels but its faces hand them out.
                return node.hasFlag(GridFlags.DENSE_CAPACITY) ? denseChannelCapacity() : 0;
            }
            return node.hasFlag(GridFlags.DENSE_CAPACITY)
                    ? denseChannelCapacity() : normalChannelCapacity();
        } catch (Throwable ignored) {
            return 0;
        }
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
        // The block is gone, so its ports must not survive into the next pass.
        for (UUID id : new ArrayList<UUID>(GROUPS.keySet())) {
            ChannelGroup group = GROUPS.get(id);
            if (group != null && group.owns(caller)) {
                destroyGroup(GROUPS.remove(id));
            }
        }
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
        if (CALLERS.isEmpty()) {
            return;
        }
        World world = event.world;
        long now = world.getTotalWorldTime();
        Long next = NEXT_REFRESH.get(world);
        if (!DIRTY_WORLDS.contains(world) && next != null && now < next) {
            return;
        }
        DIRTY_WORLDS.clear();
        scheduleNextRefresh();
        MEChannelCallerSavedData.prune(world);
        reconcile(snapshotCallers());
    }

    private static void scheduleNextRefresh() {
        for (World world : new ArrayList<World>(CALLERS.keySet())) {
            if (world != null) {
                NEXT_REFRESH.put(world, world.getTotalWorldTime() + REFRESH_INTERVAL);
            }
        }
    }

    /**
     * Every loaded server side caller. UUID matching is global, so the snapshot spans all
     * currently loaded dimensions even though the registry stays grouped by world.
     */
    private static List<TileMEChannelCaller> snapshotCallers() {
        List<TileMEChannelCaller> callers = new ArrayList<TileMEChannelCaller>();
        for (Map.Entry<World, Set<TileMEChannelCaller>> entry
                : new ArrayList<Map.Entry<World, Set<TileMEChannelCaller>>>(CALLERS.entrySet())) {
            World world = entry.getKey();
            Set<TileMEChannelCaller> registered = entry.getValue();
            if (world == null || registered == null) {
                continue;
            }
            for (TileMEChannelCaller caller : new ArrayList<TileMEChannelCaller>(registered)) {
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

    private static void reconcile(List<TileMEChannelCaller> callers) {
        Map<TileMEChannelCaller, List<PortSpec>> specsByCaller =
                new IdentityHashMap<TileMEChannelCaller, List<PortSpec>>();
        Map<UUID, List<TileMEChannelCaller>> membersById =
                new LinkedHashMap<UUID, List<TileMEChannelCaller>>();
        for (TileMEChannelCaller caller : callers) {
            specsByCaller.put(caller, describePorts(caller));
            UUID id = caller.getChannelId();
            if (id != null) {
                List<TileMEChannelCaller> members = membersById.get(id);
                if (members == null) {
                    members = new ArrayList<TileMEChannelCaller>();
                    membersById.put(id, members);
                }
                members.add(caller);
            }
        }

        // One budget per controller face is shared by every sender face it feeds, so the
        // faces can never add up to more than the controller face can hand out.
        Set<IGridNode> portNodes = allPortNodes(callers);
        Map<Object, Integer> remaining = new IdentityHashMap<Object, Integer>();

        // A UUID whose last block was removed drops its links entirely.
        for (UUID id : new ArrayList<UUID>(GROUPS.keySet())) {
            if (!membersById.containsKey(id)) {
                destroyGroup(GROUPS.remove(id));
            }
        }

        for (Map.Entry<UUID, List<TileMEChannelCaller>> entry : membersById.entrySet()) {
            Collections.sort(entry.getValue(), CALLER_ORDER);
            List<PortSpec> specs = new ArrayList<PortSpec>();
            for (TileMEChannelCaller caller : entry.getValue()) {
                List<PortSpec> callerSpecs = specsByCaller.get(caller);
                if (callerSpecs != null) {
                    specs.addAll(callerSpecs);
                }
            }

            ChannelGroup group = GROUPS.get(entry.getKey());
            if (group != null && (!group.matches(specs) || !group.isAlive())) {
                destroyGroup(group);
                group = null;
            }
            if (group == null) {
                group = ChannelGroup.create(entry.getKey(), specs);
                if (group == null) {
                    GROUPS.remove(entry.getKey());
                } else {
                    GROUPS.put(entry.getKey(), group);
                }
            } else {
                group.syncProvided(specs);
            }
            if (group != null) {
                int ours = group.occupied;
                assignProvidedCapacity(entry.getValue(), specs, portNodes, remaining, ours);
                if (group.specs != specs) {
                    group.syncProvided(specs);
                }
                // Channel usage changes every tick, so it must be re-read, never cached.
                group.refreshMetrics();
            }

            for (TileMEChannelCaller caller : entry.getValue()) {
                applyStatus(caller, group, specsByCaller.get(caller));
            }
        }

        for (TileMEChannelCaller caller : callers) {
            if (caller.getChannelId() == null) {
                applyStatus(caller, null, specsByCaller.get(caller));
            }
        }
    }

    private static void applyStatus(TileMEChannelCaller caller, @Nullable ChannelGroup group,
                                    @Nullable List<PortSpec> specs) {
        if (caller == null) {
            return;
        }
        if (caller.getChannelId() == null) {
            boolean powered = isPowered(specs);
            caller.setStatus(powered ? TileMEChannelCaller.STATUS_ERROR
                            : TileMEChannelCaller.STATUS_OFF,
                    powered ? TileMEChannelCaller.REASON_MISSING
                            : TileMEChannelCaller.REASON_NONE, 0, 0);
            return;
        }
        if (specs == null || specs.isEmpty()) {
            caller.setStatus(TileMEChannelCaller.STATUS_ERROR,
                    TileMEChannelCaller.REASON_AMBIGUOUS, 0, 0);
            return;
        }
        if (group == null) {
            caller.setStatus(isPowered(specs) ? TileMEChannelCaller.STATUS_ERROR
                            : TileMEChannelCaller.STATUS_OFF,
                    TileMEChannelCaller.REASON_MISSING, 0, 0);
            return;
        }

        boolean powered = group.isPowered();
        if (group.overloaded) {
            caller.setStatus(TileMEChannelCaller.STATUS_ERROR,
                    TileMEChannelCaller.REASON_OVERLOADED, group.occupied, group.maxChannels);
        } else if (powered && group.isComplete()) {
            caller.setStatus(TileMEChannelCaller.STATUS_CONNECTED,
                    TileMEChannelCaller.REASON_NONE, group.occupied, group.maxChannels);
        } else if (powered) {
            caller.setStatus(TileMEChannelCaller.STATUS_ERROR,
                    TileMEChannelCaller.REASON_MISSING, 0, 0);
        } else {
            caller.setStatus(TileMEChannelCaller.STATUS_OFF,
                    TileMEChannelCaller.REASON_NONE, 0, 0);
        }
    }

    private static boolean isPowered(@Nullable List<PortSpec> specs) {
        if (specs == null) {
            return false;
        }
        for (PortSpec spec : specs) {
            if (spec != null && isPowered(spec.target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPowered(@Nullable IGridNode node) {
        try {
            if (node == null || node.getGrid() == null) {
                return false;
            }
            IEnergyGrid energy = node.getGrid().getCache(IEnergyGrid.class);
            return energy != null && energy.isNetworkPowered();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** One port per ME block the caller physically touches. */
    private static List<PortSpec> describePorts(TileMEChannelCaller caller) {
        List<PortSpec> specs = new ArrayList<PortSpec>();
        World world = caller.getWorld();
        BlockPos pos = caller.getPos();
        if (world == null || pos == null) {
            return specs;
        }
        for (EnumFacing facing : FACE_ORDER) {
            BlockPos targetPos = pos.offset(facing);
            if (!world.isBlockLoaded(targetPos)) {
                continue;
            }
            TileEntity target = world.getTileEntity(targetPos);
            if (target == null || target instanceof TileMEChannelCaller) {
                continue;
            }
            IGridNode node = findFaceNode(target, facing.getOpposite());
            if (node != null) {
                specs.add(new PortSpec(caller, facing, node));
            }
        }
        return specs;
    }

    /**
     * Resolves the node that represents the face of the touched block. Only that single
     * face is used; the rest of the touched network is intentionally not inspected.
     */
    @Nullable
    private static IGridNode findFaceNode(TileEntity target, EnumFacing fromTargetToCaller) {
        AEPartLocation side = AEPartLocation.fromFacing(fromTargetToCaller);
        try {
            if (target instanceof TileCableBus) {
                TileCableBus cable = (TileCableBus) target;
                IGridNode node = cable.getGridNode(side);
                if (node == null) {
                    node = cable.getGridNode(AEPartLocation.INTERNAL);
                }
                return node;
            }
            if (target instanceof IPartHost) {
                IPart part = ((IPartHost) target).getPart(fromTargetToCaller);
                if (part != null) {
                    IGridNode node = part.getExternalFacingNode();
                    if (node == null) {
                        node = part.getGridNode();
                    }
                    if (node != null) {
                        return node;
                    }
                }
            }
            if (target instanceof IGridHost) {
                IGridHost host = (IGridHost) target;
                IGridNode node = host.getGridNode(side);
                if (node == null) {
                    node = host.getGridNode(AEPartLocation.INTERNAL);
                }
                return node;
            }
        } catch (Throwable error) {
            AE2Utilix.LOGGER.debug(
                    "ME channel caller could not read the node of a touched block", error);
        }
        return null;
    }

    private static void destroyGroup(@Nullable ChannelGroup group) {
        if (group != null) {
            group.destroy();
        }
    }

    /** AE2's controllers are the only blocks that hand out channels without carrying them. */
    private static boolean isChannelSource(@Nullable IGridNode node) {
        try {
            return node != null && node.hasFlag(GridFlags.CANNOT_CARRY)
                    && node.hasFlag(GridFlags.DENSE_CAPACITY);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Nodes a walk must never cross: every synthetic port and every block of ours. */
    private static Set<IGridNode> allPortNodes(List<TileMEChannelCaller> callers) {
        Set<IGridNode> nodes = Collections.newSetFromMap(
                new IdentityHashMap<IGridNode, Boolean>());
        for (ChannelGroup group : GROUPS.values()) {
            if (group == null) {
                continue;
            }
            for (Port port : group.ports) {
                IGridNode node = port.node();
                if (node != null) {
                    nodes.add(node);
                }
            }
        }
        for (TileMEChannelCaller caller : callers) {
            IGridNode owner = caller == null ? null : caller.getProxy().getNode();
            if (owner != null) {
                nodes.add(owner);
            }
        }
        return nodes;
    }

    /**
     * Identity of the controller-less network a face belongs to. Every node reachable from
     * that face without crossing one of our own blocks is part of the same network, so the
     * smallest identity in that set is stable for all faces of one network.
     */
    @Nullable
    private static Object networkKey(@Nullable IGridNode face, Set<IGridNode> excluded) {
        if (face == null) {
            return null;
        }
        Set<IGridNode> visited = Collections.newSetFromMap(
                new IdentityHashMap<IGridNode, Boolean>());
        ArrayDeque<IGridNode> queue = new ArrayDeque<IGridNode>();
        visited.add(face);
        queue.add(face);
        int best = System.identityHashCode(face);
        int scanned = 0;
        while (!queue.isEmpty() && scanned++ < MAX_GRID_SCAN) {
            IGridNode node = queue.poll();
            List<IGridConnection> connections = new ArrayList<IGridConnection>();
            try {
                for (IGridConnection connection : node.getConnections()) {
                    connections.add(connection);
                }
            } catch (Throwable ignored) {
            }
            for (IGridConnection connection : connections) {
                IGridNode other;
                try {
                    other = connection.getOtherSide(node);
                } catch (Throwable ignored) {
                    continue;
                }
                if (other == null || excluded.contains(other) || !visited.add(other)) {
                    continue;
                }
                best = Math.min(best, System.identityHashCode(other));
                queue.add(other);
            }
        }
        return Integer.valueOf(best);
    }

    /**
     * Works out how many channels every sender face can really bring in.
     *
     * <p>A controller face is one shared pool of channels: when several caller faces trace
     * back to the same controller face, each one takes what its own path can carry and
     * leaves the rest for the others, so they can never add up to more than the controller
     * face can hand out. This is what stops a dense cable attached to a caller from making
     * channels appear out of nowhere.</p>
     *
     * <p>When a UUID has no controller anywhere on its caller side, AE2 only serves such a
     * network up to the normal capacity, so every caller face reports that same ceiling.</p>
     */
    private static void assignProvidedCapacity(List<TileMEChannelCaller> members,
                                               List<PortSpec> specs,
                                               Set<IGridNode> portNodes,
                                               Map<Object, Integer> remaining,
                                               int ours) {
        List<PortSpec> senders = new ArrayList<PortSpec>();
        for (PortSpec spec : specs) {
            if (spec.sender) {
                senders.add(spec);
            }
        }
        if (senders.isEmpty()) {
            return;
        }
        Map<PortSpec, SourceRef> refs = new IdentityHashMap<PortSpec, SourceRef>();
        boolean anyController = false;
        for (PortSpec spec : senders) {
            SourceRef ref = traceSource(spec, portNodes, ours);
            if (ref != null) {
                refs.put(spec, ref);
                anyController = true;
            }
        }
        if (!anyController) {
            // Without a controller AE2 only serves such a network up to the normal
            // capacity, and every block in that network sees the very same number. It is
            // therefore reported once per source network instead of once per touched face,
            // which would otherwise multiply that supply by the number of faces.
            Map<PortSpec, Object> keys = new IdentityHashMap<PortSpec, Object>();
            Map<Object, Integer> levelByNetwork = new IdentityHashMap<Object, Integer>();
            for (PortSpec spec : senders) {
                Object key = networkKey(spec.target, portNodes);
                keys.put(spec, key);
                int level = faceChannelCapacity(spec.target);
                Integer best = levelByNetwork.get(key);
                if (best == null || level > best) {
                    levelByNetwork.put(key, level);
                }
            }
            Map<Object, Boolean> assigned = new IdentityHashMap<Object, Boolean>();
            for (PortSpec spec : senders) {
                Object key = keys.get(spec);
                if (key == null || assigned.containsKey(key)) {
                    spec.provided = 0;
                    continue;
                }
                assigned.put(key, Boolean.TRUE);
                Integer level = levelByNetwork.get(key);
                spec.provided = Math.min(level == null ? 0 : level, normalChannelCapacity());
            }
            return;
        }
        for (PortSpec spec : senders) {
            SourceRef ref = refs.get(spec);
            if (ref == null) {
                // This face cannot reach a controller while another face of the same UUID
                // can, so it contributes nothing to the group's supply.
                spec.provided = 0;
                continue;
            }
            Integer left = remaining.get(ref.faceKey);
            int budget = left == null ? faceBudget(ref, ours) : left;
            int give = Math.max(0, Math.min(ref.bottleneck, budget));
            remaining.put(ref.faceKey, Math.max(0, budget - give));
            spec.provided = give;
        }
    }

    /** Channels left on the controller face that pays for this path. */
    private static int faceBudget(SourceRef ref, int ours) {
        int capacity = denseChannelCapacity();
        if (!(ref.faceKey instanceof IGridConnection)) {
            // The caller touches the controller itself, so the face is dedicated to it.
            return capacity;
        }
        int used = 0;
        try {
            used = ((IGridConnection) ref.faceKey).getUsedChannels();
        } catch (Throwable ignored) {
        }
        return Math.max(0, capacity - Math.max(0, used - ours));
    }

    /** Channels still free on a carrying node, ignoring what this very link already sends. */
    private static int remainingAt(@Nullable IGridNode node, int ours) {
        int level = faceChannelCapacity(node);
        if (level <= 0) {
            return 0;
        }
        return Math.max(0, level - Math.max(0, usedChannelsAt(node) - ours));
    }

    /**
     * Channels that already flow through a node. AE2 only exposes this per connection, so
     * the busiest edge of the node is used as its current load, which is exactly the edge
     * leading further up toward the channel source.
     */
    private static int usedChannelsAt(@Nullable IGridNode node) {
        if (node == null) {
            return 0;
        }
        int used = 0;
        try {
            for (IGridConnection connection : node.getConnections()) {
                if (connection != null) {
                    used = Math.max(used, connection.getUsedChannels());
                }
            }
        } catch (Throwable ignored) {
        }
        return used;
    }

    /** The controller face a caller is fed from, plus what that path can carry. */
    private static final class SourceRef {
        private final Object faceKey;
        private final int bottleneck;

        private SourceRef(Object faceKey, int bottleneck) {
            this.faceKey = faceKey;
            this.bottleneck = bottleneck;
        }
    }

    /**
     * Traces a sender face back to its controller by widest path: the narrowest carrying
     * node between the controller and the face decides how much that path can carry, and
     * the edge leaving the controller identifies the controller face that pays for it.
     */
    @Nullable
    private static SourceRef traceSource(PortSpec spec, Set<IGridNode> excluded, int ours) {
        IGridNode face = spec.target;
        if (face == null || faceChannelCapacity(face) <= 0) {
            return null;
        }
        if (isChannelSource(face)) {
            // The caller touches a controller directly; each caller face is its own face.
            return new SourceRef(spec, denseChannelCapacity());
        }
        int faceLevel = remainingAt(face, ours);
        Map<IGridNode, Integer> best = new IdentityHashMap<IGridNode, Integer>();
        ArrayDeque<IGridNode> queue = new ArrayDeque<IGridNode>();
        best.put(face, faceLevel);
        queue.add(face);
        SourceRef result = null;
        int scanned = 0;
        while (!queue.isEmpty() && scanned++ < MAX_GRID_SCAN) {
            IGridNode node = queue.poll();
            Integer running = best.get(node);
            if (running == null) {
                continue;
            }
            List<IGridConnection> connections = new ArrayList<IGridConnection>();
            try {
                for (IGridConnection connection : node.getConnections()) {
                    connections.add(connection);
                }
            } catch (Throwable ignored) {
            }
            for (IGridConnection connection : connections) {
                IGridNode other;
                try {
                    other = connection.getOtherSide(node);
                } catch (Throwable ignored) {
                    continue;
                }
                if (other == null || excluded.contains(other)) {
                    continue;
                }
                if (isChannelSource(other)) {
                    // The edge leaving the controller is the controller face itself.
                    if (result == null || running > result.bottleneck) {
                        result = new SourceRef(connection, running);
                    }
                    continue;
                }
                int level = faceChannelCapacity(other);
                if (level <= 0) {
                    continue;
                }
                int candidate = Math.min(running, remainingAt(other, ours));
                Integer existing = best.get(other);
                if (existing == null || candidate > existing) {
                    best.put(other, candidate);
                    queue.add(other);
                }
            }
        }
        return result;
    }

    /** The ME block touched through one face, plus the caller that touches it. */
    private static final class PortSpec {
        private final TileMEChannelCaller owner;
        private final EnumFacing face;
        private final IGridNode target;
        private final boolean sender;
        /** Channels this face can actually bring in, filled in once per reconcile pass. */
        private int provided;

        private PortSpec(TileMEChannelCaller owner, EnumFacing face, IGridNode target) {
            this.owner = owner;
            this.face = face;
            this.target = target;
            this.sender = !owner.isListener();
        }

        private boolean sameAs(@Nullable PortSpec other) {
            return other != null && owner == other.owner && face == other.face
                    && sender == other.sender && target == other.target;
        }
    }

    /** Every port that shares one UUID, linked sender to receiver. */
    private static final class ChannelGroup {
        private final UUID id;
        private final List<PortSpec> specs;
        private final List<Port> ports = new ArrayList<Port>();
        private final List<IGridConnection> links = new ArrayList<IGridConnection>();

        private int occupied;
        private int maxChannels;
        private boolean overloaded;

        private ChannelGroup(UUID id, List<PortSpec> specs) {
            this.id = id;
            this.specs = specs;
        }

        @Nullable
        private static ChannelGroup create(UUID id, List<PortSpec> specs) {
            if (id == null || specs == null || specs.isEmpty()) {
                return null;
            }
            ChannelGroup group = new ChannelGroup(id, specs);
            group.buildPorts();
            if (group.ports.isEmpty()) {
                group.destroy();
                return null;
            }
            return group;
        }

        private void buildPorts() {
            List<Port> senders = new ArrayList<Port>();
            List<Port> listeners = new ArrayList<Port>();
            int index = 0;
            for (PortSpec spec : specs) {
                Port port = Port.create(spec, index++);
                if (port == null) {
                    AE2Utilix.LOGGER.warn("ME channel caller port could not be created for UUID "
                            + id + " at " + describe(spec));
                    continue;
                }
                ports.add(port);
                if (spec.sender) {
                    senders.add(port);
                } else {
                    listeners.add(port);
                }
            }
            // AE2 gives every node exactly one route back to the controller, so a fully
            // meshed link would make the whole receiver side hang off a single sender and
            // share that one port's channel budget. Pairing each receiver with its own
            // sender keeps the pairs on separate paths, which is what lets every touched
            // face carry up to AE2's configured capacity independently.
            if (senders.isEmpty() || listeners.isEmpty()) {
                return;
            }
            for (int i = 0; i < listeners.size(); i++) {
                Port sender = senders.get(i % senders.size());
                IGridConnection link = sender.linkTo(listeners.get(i).node());
                if (link != null) {
                    links.add(link);
                }
            }
        }

        /** Re-reads the live channel usage; never cached across passes. */
        private void refreshMetrics() {
            int senders = 0;
            int receivers = 0;
            int granted = 0;
            int supplied = 0;
            for (Port port : ports) {
                if (port.spec.sender) {
                    senders++;
                    supplied += port.usedChannels();
                } else {
                    receivers++;
                    granted += port.usedChannels();
                }
            }
            this.occupied = granted;
            // The reported limit is what the caller side can really be fed, as worked out
            // for this pass by the shared controller face accounting.
            this.maxChannels = totalProvided();
            // Overload needs both halves to be true: this link must actually be drawing
            // everything the source can feed it, and the receiver side must still want
            // more than it received. A link with spare supply is never reported as
            // overloaded just because some devices elsewhere were refused channels.
            this.overloaded = senders > 0 && receivers > 0
                    && supplied >= maxChannels
                    && requestedCapacity() > supplied;
        }

        /** Sum of what the source side can feed into the touched sender faces. */
        private int totalProvided() {
            int total = 0;
            for (PortSpec spec : specs) {
                total += spec.provided;
            }
            return total;
        }

        /** Copies this pass's allowances onto the cached specs of a kept group. */
        private void syncProvided(List<PortSpec> fresh) {
            if (fresh == null || fresh.size() != specs.size()) {
                return;
            }
            for (int i = 0; i < specs.size(); i++) {
                specs.get(i).provided = fresh.get(i).provided;
            }
        }

        /**
         * Channels the receiver side wants through this link, summed over the touched
         * faces with every face capped at its own capacity. Each device is attributed to
         * the face AE2 would route it through, so nothing is counted twice and a cable
         * that is overloaded by its own devices only ever counts as one face.
         */
        private int requestedCapacity() {
            List<Port> receivers = new ArrayList<Port>();
            List<Integer> faceLimits = new ArrayList<Integer>();
            for (Port port : ports) {
                if (!port.spec.sender) {
                    receivers.add(port);
                    faceLimits.add(faceChannelCapacity(port.spec.target));
                }
            }
            if (receivers.isEmpty()) {
                return 0;
            }
            int[] demand = new int[receivers.size()];
            Set<IGridNode> portNodes = Collections.newSetFromMap(
                    new IdentityHashMap<IGridNode, Boolean>());
            for (Port port : ports) {
                IGridNode node = port.node();
                if (node != null) {
                    portNodes.add(node);
                }
                IGridNode owner = port.spec.owner.getProxy().getNode();
                if (owner != null) {
                    portNodes.add(owner);
                }
            }
            Map<IGridNode, Integer> origin = new IdentityHashMap<IGridNode, Integer>();
            ArrayDeque<IGridNode> queue = new ArrayDeque<IGridNode>();
            for (int i = 0; i < receivers.size(); i++) {
                IGridNode start = receivers.get(i).spec.target;
                if (start != null && !origin.containsKey(start)) {
                    origin.put(start, i);
                    queue.add(start);
                }
            }
            int scanned = 0;
            while (!queue.isEmpty() && scanned++ < MAX_GRID_SCAN) {
                IGridNode node = queue.poll();
                if (portNodes.contains(node)) {
                    // Never walk out of the receiver side through a synthetic port.
                    continue;
                }
                Integer source = origin.get(node);
                if (source == null) {
                    continue;
                }
                try {
                    if (node.hasFlag(GridFlags.REQUIRE_CHANNEL)) {
                        demand[source]++;
                    }
                } catch (Throwable ignored) {
                }
                List<IGridConnection> connections = new ArrayList<IGridConnection>();
                try {
                    for (IGridConnection connection : node.getConnections()) {
                        connections.add(connection);
                    }
                } catch (Throwable ignored) {
                }
                for (IGridConnection connection : connections) {
                    try {
                        IGridNode other = connection.getOtherSide(node);
                        if (other != null && !origin.containsKey(other)) {
                            origin.put(other, source);
                            queue.add(other);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            int total = 0;
            for (int i = 0; i < demand.length; i++) {
                // Each face can only ever deliver what that face itself can carry.
                total += Math.min(demand[i], faceLimits.get(i));
            }
            return total;
        }

        private boolean matches(List<PortSpec> current) {
            if (current == null || current.size() != specs.size()) {
                return false;
            }
            for (int i = 0; i < specs.size(); i++) {
                if (!specs.get(i).sameAs(current.get(i))) {
                    return false;
                }
            }
            return true;
        }

        private boolean owns(TileMEChannelCaller caller) {
            for (PortSpec spec : specs) {
                if (spec.owner == caller) {
                    return true;
                }
            }
            return false;
        }

        private boolean isAlive() {
            // A port that failed to attach must be retried instead of staying missing.
            if (ports.size() != specs.size()) {
                return false;
            }
            for (Port port : ports) {
                if (!port.isAlive()) {
                    return false;
                }
            }
            return true;
        }

        private boolean isPowered() {
            return MEChannelCallerNetwork.isPowered(specs);
        }

        private boolean isComplete() {
            boolean sender = false;
            boolean listener = false;
            for (Port port : ports) {
                if (port.spec.sender) {
                    sender = true;
                } else {
                    listener = true;
                }
            }
            return sender && listener;
        }

        private void destroy() {
            for (IGridConnection link : new ArrayList<IGridConnection>(links)) {
                destroyConnection(link);
            }
            links.clear();
            for (Port port : new ArrayList<Port>(ports)) {
                port.destroy();
            }
            ports.clear();
        }
    }

    /** A synthetic AE2 node attached to exactly one touched face. */
    private static final class Port {
        private final PortSpec spec;
        private final AENetworkProxy proxy;
        @Nullable
        private IGridConnection attachment;

        private Port(PortSpec spec, AENetworkProxy proxy) {
            this.spec = spec;
            this.proxy = proxy;
        }

        @Nullable
        private static Port create(PortSpec spec, int index) {
            DimensionalCoord location = new DimensionalCoord(
                    spec.owner.getWorld(), spec.owner.getPos());
            ProxyHost host = new ProxyHost(location);
            AENetworkProxy proxy = new AENetworkProxy(host,
                    "me_channel_caller_port_" + index, ItemStack.EMPTY, false);
            host.setProxy(proxy);
            // Both ends are pure dense path nodes. Requesting a channel here would spend
            // one of this node's own slots (AE2 counts the requirement on the node it
            // belongs to), so a 32 channel link could only pass 31 channels through.
            // Leaving the flags off keeps the whole capacity available for the traffic and
            // lets AE2's controller stay the only authority on channel numbers.
            proxy.setFlags(GridFlags.DENSE_CAPACITY);
            proxy.setValidSides(EnumSet.noneOf(EnumFacing.class));
            proxy.onReady();
            IGridNode node = proxy.getNode();
            if (node == null) {
                proxy.invalidate();
                return null;
            }
            IGridConnection attachment = connect(spec.target, node);
            if (attachment == null) {
                proxy.invalidate();
                return null;
            }
            Port port = new Port(spec, proxy);
            port.attachment = attachment;
            return port;
        }

        @Nullable
        private IGridNode node() {
            return proxy.getNode();
        }

        @Nullable
        private IGridConnection linkTo(@Nullable IGridNode other) {
            if (other == null) {
                return null;
            }
            return connect(proxy.getNode(), other);
        }

        /** Channels currently routed through this port's link to its touched block. */
        private int usedChannels() {
            try {
                if (attachment == null || proxy.getNode() == null) {
                    return 0;
                }
                return Math.max(0, attachment.getUsedChannels());
            } catch (Throwable ignored) {
                return 0;
            }
        }

        private boolean isAlive() {
            IGridNode node = proxy.getNode();
            if (node == null || node.getGrid() == null) {
                return false;
            }
            return attachment != null && isAttached(attachment, node, spec.target);
        }

        private void destroy() {
            if (attachment != null) {
                destroyConnection(attachment);
                attachment = null;
            }
            try {
                proxy.invalidate();
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean isAttached(IGridConnection connection, IGridNode from,
                                      IGridNode expected) {
        try {
            return connection != null && connection.getOtherSide(from) == expected;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Creates an internal edge. Internal edges are invisible to AE2's per-side connection
     * discovery, so a synthetic port can never make a cable or machine believe it has a
     * physical neighbour it does not have.
     */
    @Nullable
    private static IGridConnection connect(@Nullable IGridNode first, @Nullable IGridNode second) {
        if (first == null || second == null || first == second) {
            return null;
        }
        try {
            for (IGridConnection existing : first.getConnections()) {
                if (existing != null && existing.getOtherSide(first) == second) {
                    return existing;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            return GridConnection.create(first, second, AEPartLocation.INTERNAL);
        } catch (Throwable error) {
            try {
                return AEApi.instance().grid().createGridConnection(first, second);
            } catch (Throwable ignored) {
                AE2Utilix.LOGGER.debug("ME channel caller could not link synthetic nodes", error);
                return null;
            }
        }
    }

    private static void destroyConnection(@Nullable IGridConnection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.destroy();
        } catch (Throwable ignored) {
        }
    }

    private static String describe(PortSpec spec) {
        return (spec.sender ? "sender" : "listener") + " face " + spec.face
                + " (dimension " + spec.owner.getWorld().provider.getDimension()
                + ", position " + spec.owner.getPos() + ")";
    }

    /** Lightweight host for a synthetic node; it is never world accessible. */
    private static final class ProxyHost implements IGridProxyable {
        private final DimensionalCoord location;
        private AENetworkProxy proxy;

        private ProxyHost(DimensionalCoord location) {
            this.location = location;
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
            return AECableType.NONE;
        }
    }
}
