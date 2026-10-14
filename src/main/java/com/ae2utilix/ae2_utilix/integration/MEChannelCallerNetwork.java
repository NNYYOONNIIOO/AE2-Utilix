package com.ae2utilix.ae2_utilix.integration;

import ae2.api.networking.GridFlags;
import ae2.api.networking.GridHelper;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridConnection;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeListener;
import ae2.api.networking.IManagedGridNode;
import ae2.api.networking.energy.IEnergyService;
import ae2.api.parts.IPart;
import ae2.api.parts.IPartHost;
import ae2.api.util.AECableType;
import ae2.api.util.AEPartLocation;
import ae2.api.util.DimensionalBlockPos;
import ae2.core.AEConfig;
import ae2.me.GridConnection;
import ae2.tile.networking.TileCableBus;
import com.ae2utilix.ae2_utilix.AE2Utilix;
import com.ae2utilix.ae2_utilix.block.TileMEChannelCaller;
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


    /**
     * Listener for the synthetic port nodes. They own no persistent state and never take
     * part in world discovery, so none of the callbacks need to do anything.
     */
    private static final IGridNodeListener<Object> NOOP_LISTENER = new IGridNodeListener<Object>() {
        @Override
        public void onSaveChanges(Object nodeOwner, IGridNode node) {
        }
    };

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
    /**
     * Channel capacity of a dense link, following AE2's configured channel mode so the
     * caller never reports a figure the network itself would not hand out.
     */
    private static int denseChannelCapacity() {
        return 32 * channelCapacityFactor();
    }

    /** Channel capacity of a normal (non dense) link. */
    private static int normalChannelCapacity() {
        return 8 * channelCapacityFactor();
    }

    /** AE2's cable capacity multiplier, 1 when it cannot be read. */
    private static int channelCapacityFactor() {
        try {
            return Math.max(1, AEConfig.instance().getChannelMode().getCableCapacityFactor());
        } catch (Throwable ignored) {
            return 1;
        }
    }

    /**
     * Channels one touched face can actually provide, read from that face own node
     * instead of assuming the configured maximum: a face on a normal cable only supplies
     * the normal capacity, a face on dense cable or a controller supplies the dense
     * capacity, and a node that cannot carry channels supplies none.
     */
    private static int faceChannelCapacity(@Nullable IGridNode node) {
        return nodeCapacity(node);
    }

    /**
     * AE2 own capacity for a node, so the caller follows the configured channel mode.
     * Returns 0 for nodes that cannot carry channels at all.
     */
    private static int nodeCapacity(@Nullable IGridNode node) {
        if (node == null) {
            return 0;
        }
        try {
            if (node.hasFlag(GridFlags.CANNOT_CARRY)) {
                // A controller does not carry channels itself, but its faces hand them out.
                return node.hasFlag(GridFlags.DENSE_CAPACITY) ? denseChannelCapacity() : 0;
            }
        } catch (Throwable ignored) {
            return 0;
        }
        try {
            if (node instanceof ae2.me.GridNode gridNode) {
                // AE2 own figure, so the caller follows the configured channel mode.
                return Math.max(0, gridNode.getMaxChannels());
            }
        } catch (Throwable ignored) {
            // Fall through to the flag based estimate below.
        }
        try {
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
                if (group.specs != specs) {
                    group.syncProvided(specs);
                }
                group.captureDraw(specs);
                assignProvidedCapacity(entry.getValue(), specs, portNodes, remaining);
                if (group.specs != specs) {
                    group.syncProvided(specs);
                }
                if (com.ae2utilix.ae2_utilix.config.AE2UtilixConfig.debugChannelCaller) {
                    debugGroup(entry.getKey(), group, specs);
                }
                // The senders that can actually supply channels may have changed, which is
                // the moment the listener pairing has to be rebuilt.
                group.rebuildLinks();
                group.sourceGrid = senderGrid(entry.getValue());
                long seen = Long.MAX_VALUE;
                long earliest = Long.MAX_VALUE;
                for (TileMEChannelCaller caller : entry.getValue()) {
                    long order = caller.getPlacementOrder();
                    if (order < seen) {
                        seen = order;
                        earliest = caller.getPos().toLong();
                    }
                }
                group.firstSeen = seen;
                group.firstPosition = earliest;
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

    /**
     * Logs the raw inputs behind one group channel budget.
     *
     * <p>Only runs while {@code debugChannelCaller} is enabled. Each sender face prints the
     * capacity AE2 reports for the touched node, how much AE2 already uses on it, and the
     * resulting allowance, which is what tells apart a wrong capacity from a wrong usage
     * figure.</p>
     */
    private static void debugGroup(UUID id, ChannelGroup group, List<PortSpec> specs) {
        StringBuilder line = new StringBuilder();
        line.append("channel-caller uuid=").append(id)
                .append(" occupied=").append(group.occupied)
                .append(" carried=").append(group.suppliedChannels())
                .append(" provided=").append(group.totalProvided())
                .append(" supplyingFaces=").append(group.supplyingCount());
        for (PortSpec spec : specs) {
            line.append(" | ").append(spec.sender ? "S" : "L").append(" face=").append(spec.face)
                    .append(" cap=").append(nodeCapacity(spec.target))
                    .append(" used=").append(usedChannelsAt(spec.target))
                    .append(" free=").append(remainingAt(spec.target, spec.ownDraw))
                    .append(" ownDraw=").append(spec.ownDraw)
                    .append(" canSupply=").append(spec.canSupply)
                    .append(" provided=").append(spec.provided);
        }
        AE2Utilix.LOGGER.info(line.toString());
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
            if (node == null || node.grid() == null) {
                return false;
            }
            IEnergyService energy = node.grid().getEnergyService();
            return energy != null && energy.isNetworkPowered();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Sender-side specs of one caller, used when matching it to a terminal's network. */
    static List<PortSpec> describeSenderSpecs(TileMEChannelCaller caller) {
        List<PortSpec> result = new ArrayList<PortSpec>();
        if (caller == null) {
            return result;
        }
        for (PortSpec spec : describePorts(caller)) {
            if (spec.sender) {
                result.add(spec);
            }
        }
        return result;
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
        EnumFacing side = fromTargetToCaller;
        try {
            if (target instanceof IPartHost) {
                // Look at the part on exactly this side and never fall back to the cable
                // bus centre: a cable anchor or quartz fibre occupies that side as a
                // deliberate channel break, and the centre node would step straight over
                // it. A side whose part has no node simply provides no channels.
                IPart part = ((IPartHost) target).getPart(fromTargetToCaller);
                if (part == null) {
                    return target instanceof TileCableBus
                            ? centerNode((TileCableBus) target) : null;
                }
                IGridNode node = part.getExternalFacingNode();
                if (node == null) {
                    node = part.getGridNode();
                }
                return node;
            }
            // Any other AE block exposes its node through the capability on that side.
            return GridHelper.getExposedNode(target.getWorld(), target.getPos(), side);
        } catch (Throwable error) {
            AE2Utilix.LOGGER.debug(
                    "ME channel caller could not read the node of a touched block", error);
        }
        return null;
    }

    /** The channel carrying centre of a plain cable bus side that holds no part. */
    @Nullable
    private static IGridNode centerNode(TileCableBus cable) {
        try {
            return GridHelper.getExposedNode(cable.getWorld(), cable.getPos(), EnumFacing.UP);
        } catch (Throwable ignored) {
            return null;
        }
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
            IGridNode owner = caller == null ? null : caller.getMainNode().getNode();
            if (owner != null) {
                nodes.add(owner);
            }
        }
        return nodes;
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
                                               Map<Object, Integer> remaining) {
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
            SourceRef ref = traceSource(spec, portNodes);
            if (ref != null) {
                refs.put(spec, ref);
                anyController = true;
            }
        }
        if (!anyController) {
            // Ad-hoc networks are limited as a whole, not per cable: AE2 hands every
            // block in the network the same used figure, so what is left for us is that
            // shared ceiling minus what the network already carries.
            int level = 0;
            int used = 0;
            int ourDraw = 0;
            for (PortSpec spec : senders) {
                level = Math.max(level, faceChannelCapacity(spec.target));
                used = Math.max(used, usedChannelsAt(spec.target));
                ourDraw += spec.ownDraw;
            }
            int limit = Math.min(level, normalChannelCapacity());
            int shared = Math.max(0, limit - Math.max(0, used - ourDraw));
            for (int i = 0; i < senders.size(); i++) {
                PortSpec spec = senders.get(i);
                spec.canSupply = shared > 0;
                spec.provided = i == 0 ? shared : 0;
            }
            return;
        }
        Map<Object, Integer> ourDraw = new IdentityHashMap<Object, Integer>();
        for (PortSpec spec : senders) {
            SourceRef ref = refs.get(spec);
            if (ref == null) {
                continue;
            }
            for (Object resource : ref.resources) {
                Integer previous = ourDraw.get(resource);
                ourDraw.put(resource, (previous == null ? 0 : previous) + spec.ownDraw);
            }
        }

        for (PortSpec spec : senders) {
            SourceRef ref = refs.get(spec);
            if (ref == null) {
                // This face cannot reach a controller while another face of the same UUID
                // can, so it contributes nothing to the group's supply.
                spec.canSupply = false;
                spec.provided = 0;
                continue;
            }
            // Every resource on the way to the controller is shared. AE2's usage already
            // counts every channel this UUID pulls through that resource, so the channels
            // this group is responsible for are added back once per resource. A normal
            // cable with seven of its eight channels taken by other devices therefore
            // reports a single channel, while one that is carrying our own traffic keeps
            // reporting the full capacity this link may use.
            int give = Integer.MAX_VALUE;
            for (Object resource : ref.resources) {
                int available = availableChannels(resource, remaining,
                        ourContribution(ourDraw, resource));
                if (available < give) {
                    give = available;
                }
                if (give <= 0) {
                    break;
                }
            }
            if (give == Integer.MAX_VALUE || give < 0) {
                give = 0;
            }
            for (Object resource : ref.resources) {
                int available = availableChannels(resource, remaining,
                        ourContribution(ourDraw, resource));
                remaining.put(resource, Math.max(0, available - give));
            }
            spec.canSupply = give > 0;
            spec.provided = give;
        }
    }

    /** Channels this group already sends through one shared resource, added back once. */
    private static int ourContribution(Map<Object, Integer> ourDraw, Object resource) {
        Integer value = ourDraw.get(resource);
        return value == null ? 0 : value;
    }

    /** Channels a shared resource can still hand out in this pass. */
    private static int availableChannels(Object resource, Map<Object, Integer> remaining,
                                         int ours) {
        Integer cached = remaining.get(resource);
        return cached != null ? cached : liveRemaining(resource, ours);
    }

    /**
     * Channels a shared resource can still hand to this link in total, read live from
     * AE2's own figures. AE2's usage already contains the channels this link currently
     * draws, so they are added back: the figure is what the resource can carry for us,
     * not just what is still unused.
     */
    private static int liveRemaining(Object resource, int ours) {
        int capacity = resourceCapacity(resource);
        if (capacity <= 0) {
            return 0;
        }
        int used = resourceUsage(resource);
        return Math.max(0, capacity - Math.max(0, used - ours));
    }

    private static int resourceCapacity(Object resource) {
        if (resource instanceof IGridConnection) {
            // A link is only as wide as the narrowest block it joins, so a normal cable
            // must not be credited the dense figure just because it touches one.
            IGridConnection connection = (IGridConnection) resource;
            int narrowest = 0;
            try {
                int first = faceChannelCapacity(connection.a());
                int second = faceChannelCapacity(connection.b());
                if (first <= 0) {
                    narrowest = second;
                } else if (second <= 0) {
                    narrowest = first;
                } else {
                    narrowest = Math.min(first, second);
                }
            } catch (Throwable ignored) {
            }
            return Math.max(0, narrowest);
        }
        if (resource instanceof IGridNode) {
            return faceChannelCapacity((IGridNode) resource);
        }
        // A directly touched controller face belongs to that one caller alone.
        return denseChannelCapacity();
    }

    private static int resourceUsage(Object resource) {
        if (resource instanceof IGridConnection) {
            return usedChannelsOf((IGridConnection) resource);
        }
        if (resource instanceof IGridNode) {
            return usedChannelsAt((IGridNode) resource);
        }
        return 0;
    }

    private static int usedChannelsOf(@Nullable IGridConnection connection) {
        try {
            return connection == null ? 0 : Math.max(0, connection.getUsedChannels());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** The network behind a group's sender faces, used to match a wireless terminal. */
    @Nullable
    private static IGrid senderGrid(List<TileMEChannelCaller> members) {
        for (TileMEChannelCaller caller : members) {
            if (caller == null) {
                continue;
            }
            for (PortSpec spec : describeSenderSpecs(caller)) {
                IGrid grid = gridOfNode(spec.target);
                if (grid != null) {
                    return grid;
                }
            }
        }
        return null;
    }

    @Nullable
    private static IGrid gridOfNode(@Nullable IGridNode node) {
        try {
            return node == null ? null : node.grid();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Picks the UUID that still has the most channels left on the given network, which is
     * what a freshly placed caller binds to when the player carries a wireless terminal.
     * Ties fall back to the UUID whose caller was placed first.
     */
    @Nullable
    public static UUID selectBestChannelId(@Nullable IGrid network) {
        if (network == null) {
            return null;
        }
        UUID best = null;
        int bestRemaining = Integer.MIN_VALUE;
        long bestSeen = Long.MAX_VALUE;
        long bestPos = Long.MAX_VALUE;
        for (Map.Entry<UUID, ChannelGroup> entry : GROUPS.entrySet()) {
            ChannelGroup group = entry.getValue();
            if (group == null || group.sourceGrid != network) {
                continue;
            }
            int remaining = group.maxChannels - group.occupied;
            if (remaining < 0) {
                remaining = 0;
            }
            long pos = group.firstPosition;
            boolean better = remaining > bestRemaining
                    || (remaining == bestRemaining && (group.firstSeen < bestSeen
                    || (group.firstSeen == bestSeen && pos < bestPos)));
            if (better) {
                best = entry.getKey();
                bestRemaining = remaining;
                bestSeen = group.firstSeen;
                bestPos = pos;
            }
        }
        return best;
    }

    /**
     * Channels still free on a carrying node, in AE2's own terms.
     *
     * <p>AE2 counts the channels it allocated to this node's whole subtree, which is
     * exactly the traffic the node already carries, and refuses a new channel as soon as
     * that figure reaches the node's capacity. That makes "capacity - used" the number of
     * channels this node can still hand out, and it is why a normal cable with seven of its
     * eight channels allocated must report a single channel rather than eight.</p>
     */
    private static int remainingAt(@Nullable IGridNode node, int ours) {
        int level = faceChannelCapacity(node);
        if (level <= 0) {
            return 0;
        }
        return Math.max(0, level - Math.max(0, usedChannelsAt(node) - ours));
    }

    /**
     * Channels that already flow through a node.
     *
     * <p>{@code GridNode.getUsedChannels()} reports what AE2 own pathing assigned to that
     * node, which is exactly the number the caller must subtract from the capacity. The
     * per-connection figure is only a fallback for nodes that do not expose their count.</p>
     */
    private static int usedChannelsAt(@Nullable IGridNode node) {
        if (node == null) {
            return 0;
        }
        int used = 0;
        try {
            if (node instanceof ae2.me.GridNode gridNode) {
                used = Math.max(0, gridNode.getUsedChannels());
            }
        } catch (Throwable ignored) {
            // Fall through to the connection scan below.
        }
        try {
            for (IGridConnection connection : node.getConnections()) {
                if (connection != null) {
                    used = Math.max(used, Math.max(0, connection.getUsedChannels()));
                }
            }
        } catch (Throwable ignored) {
        }
        return used;
    }

    /** The controller face a caller is fed from, plus what that path can carry. */
    private static final class SourceRef {
        /** Every shared resource between this face and the controller, nearest first. */
        private final List<Object> resources;

        private SourceRef(List<Object> resources) {
            this.resources = resources;
        }
    }

    /**
     * Traces a sender face back to its controller by widest path: the narrowest carrying
     * node between the controller and the face decides how much that path can carry, and
     * the edge leaving the controller identifies the controller face that pays for it.
     */
    @Nullable
    private static SourceRef traceSource(PortSpec spec, Set<IGridNode> excluded) {
        IGridNode face = spec.target;
        if (face == null || faceChannelCapacity(face) <= 0) {
            return null;
        }
        if (isChannelSource(face)) {
            // The caller touches a controller directly; each caller face is its own face.
            List<Object> direct = new ArrayList<Object>();
            direct.add(spec);
            return new SourceRef(direct);
        }
        int faceLevel = remainingAt(face, spec.ownDraw);
        Map<IGridNode, Integer> best = new IdentityHashMap<IGridNode, Integer>();
        Map<IGridNode, List<Object>> paths = new IdentityHashMap<IGridNode, List<Object>>();
        ArrayDeque<IGridNode> queue = new ArrayDeque<IGridNode>();
        best.put(face, faceLevel);
        List<Object> startPath = new ArrayList<Object>();
        startPath.add(face);
        paths.put(face, startPath);
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
                    List<Object> path = new ArrayList<Object>(paths.get(node));
                    path.add(connection);
                    Integer known = best.get(other);
                    if (result == null || (known == null || running > known)) {
                        best.put(other, running);
                        result = new SourceRef(path);
                    }
                    continue;
                }
                int level = faceChannelCapacity(other);
                if (level <= 0) {
                    continue;
                }
                int candidate = Math.min(running, remainingAt(other, spec.ownDraw));
                Integer existing = best.get(other);
                if (existing == null || candidate > existing) {
                    best.put(other, candidate);
                    List<Object> path = new ArrayList<Object>(paths.get(node));
                    path.add(connection);
                    path.add(other);
                    paths.put(other, path);
                    queue.add(other);
                }
            }
        }
        return result;
    }

    /** The ME block touched through one face, plus the caller that touches it. */
    static final class PortSpec {
        private final TileMEChannelCaller owner;
        private final EnumFacing face;
        private final IGridNode target;
        private final boolean sender;
        /** Channels this face can actually bring in, filled in once per reconcile pass. */
        private int provided;
        /** Whether this face reaches a channel source at all; drives listener pairing. */
        private boolean canSupply;
        /** Channels this face currently draws out of the touched block. */
        private int ownDraw;

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
        /** Sender each listening face is currently wired to, so relinking only happens on change. */
        private Port[] lastAssignment = new Port[0];

        private int occupied;
        private int maxChannels;
        private boolean overloaded;
        /** Grid the group's sender faces sit on, used to match a wireless terminal's network. */
        @Nullable
        private IGrid sourceGrid;
        /** Lowest registration order of its members, so ties keep the earlier block. */
        private long firstSeen = Long.MAX_VALUE;
        /** Packed position of its earliest member, the final deterministic tie break. */
        private long firstPosition = Long.MAX_VALUE;

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
            int index = 0;
            for (PortSpec spec : specs) {
                Port port = Port.create(spec, index++);
                if (port == null) {
                    AE2Utilix.LOGGER.warn("ME channel caller port could not be created for UUID "
                            + id + " at " + describe(spec));
                    continue;
                }
                ports.add(port);
                // Listeners are joined to senders in rebuildLinks(), once this pass has
                // worked out which senders actually reach a channel source.
            }
            rebuildLinks();
        }

        /**
         * Joins every listener to the senders that actually reach a channel source.
         *
         * <p>AE2 gives every node exactly one route back to the controller and counts the
         * channels of the whole subtree on it, so a fully meshed link would make the whole
         * receiver side hang off a single port's budget, while a fixed round robin starves
         * every listener that happened to be paired with a sender which supplies nothing.
         * Linking to the supplying senders only, and re-running the pairing whenever the
         * set of supplying faces changes, keeps each face on its own path without ever
         * leaving a listener without a source.</p>
         */
        private void rebuildLinks() {
            List<Port> receivers = new ArrayList<Port>();
            List<Port> supplying = new ArrayList<Port>();
            for (Port port : ports) {
                if (port.spec.sender) {
                    if (port.spec.canSupply) {
                        supplying.add(port);
                    }
                } else {
                    receivers.add(port);
                }
            }
            if (supplying.isEmpty() || receivers.isEmpty()) {
                // Nothing can be served, so any stale wiring must go.
                dropLinks();
                return;
            }

            // Every sender face is a pool of channels, and a listening face is a demand that
            // must fit inside a single pool, because AE2 gives each node exactly one route
            // back to its controller. Filling the biggest demands first and always choosing
            // the tightest pool that still fits keeps the whole supply usable: a 25 channel
            // sender is never wasted on a face that needs 32, and two smaller faces may
            // share one 32 channel sender. That is the "sum then split" behaviour a plain
            // one-to-one pairing cannot provide.
            int[] demand = computeReceiverDemand(receivers);
            List<Integer> order = new ArrayList<Integer>();
            for (int i = 0; i < receivers.size(); i++) {
                order.add(i);
            }
            Collections.sort(order, new Comparator<Integer>() {
                @Override
                public int compare(Integer first, Integer second) {
                    return Integer.compare(demand[second], demand[first]);
                }
            });

            List<Port> pools = new ArrayList<Port>(supplying);
            Collections.sort(pools, new Comparator<Port>() {
                @Override
                public int compare(Port first, Port second) {
                    return Integer.compare(second.spec.provided, first.spec.provided);
                }
            });
            int[] remaining = new int[pools.size()];
            for (int i = 0; i < pools.size(); i++) {
                remaining[i] = Math.max(0, pools.get(i).spec.provided);
            }

            Port[] assignment = new Port[receivers.size()];
            for (int index = 0; index < order.size(); index++) {
                int receiver = order.get(index);
                int need = Math.max(0, demand[receiver]);
                int best = -1;
                for (int pool = 0; pool < pools.size(); pool++) {
                    if (remaining[pool] < need || remaining[pool] <= 0) {
                        continue;
                    }
                    if (need == 0) {
                        // A face with nothing behind it still needs a live route so newly
                        // placed devices can be served without waiting for a relink.
                        best = pool;
                        break;
                    }
                    if (best < 0 || remaining[pool] < remaining[best]) {
                        best = pool;
                    }
                }
                if (best < 0) {
                    // No pool is big enough on its own; use the largest one left so the
                    // face still gets whatever it can, and the deficit stays visible.
                    for (int pool = 0; pool < pools.size(); pool++) {
                        if (remaining[pool] <= 0) {
                            continue;
                        }
                        if (best < 0 || remaining[pool] > remaining[best]) {
                            best = pool;
                        }
                    }
                }
                if (best < 0) {
                    assignment[receiver] = null;
                    continue;
                }
                assignment[receiver] = pools.get(best);
                if (need > 0) {
                    remaining[best] = Math.max(0, remaining[best] - Math.min(need, remaining[best]));
                }
            }

            boolean same = assignment.length == lastAssignment.length && links.size() == receivers.size();
            if (same) {
                for (int i = 0; i < assignment.length; i++) {
                    if (assignment[i] != lastAssignment[i]) {
                        same = false;
                        break;
                    }
                }
            }
            if (same) {
                return;
            }

            dropLinks();
            for (int i = 0; i < receivers.size(); i++) {
                if (assignment[i] == null) {
                    continue;
                }
                IGridConnection link = assignment[i].linkTo(receivers.get(i).node());
                if (link != null) {
                    links.add(link);
                }
            }
            lastAssignment = assignment;
        }

        /**
         * Channels each listening face needs, counted from the devices reachable behind it
         * and capped at that face's own capacity. Devices behind a synthetic port are never
         * followed, so the sender side is not counted as receiver demand.
         */
        private int[] computeReceiverDemand(List<Port> receivers) {
            int[] demand = new int[receivers.size()];
            int[] faceLimits = new int[receivers.size()];
            Set<IGridNode> portNodes = Collections.newSetFromMap(
                    new IdentityHashMap<IGridNode, Boolean>());
            for (Port port : ports) {
                IGridNode node = port.node();
                if (node != null) {
                    portNodes.add(node);
                }
                IGridNode owner = port.spec.owner.getMainNode().getNode();
                if (owner != null) {
                    portNodes.add(owner);
                }
            }
            Map<IGridNode, Integer> origin = new IdentityHashMap<IGridNode, Integer>();
            ArrayDeque<IGridNode> queue = new ArrayDeque<IGridNode>();
            for (int i = 0; i < receivers.size(); i++) {
                faceLimits[i] = Math.max(0, faceChannelCapacity(receivers.get(i).spec.target));
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
            for (int i = 0; i < demand.length; i++) {
                demand[i] = Math.min(demand[i], faceLimits[i]);
            }
            return demand;
        }

        /**
         * Copies each port's live draw onto the matching spec, so supply accounting can add
         * back exactly the channels this one face is responsible for.
         */
        private void captureDraw(List<PortSpec> target) {
            if (target == null || target.size() != ports.size()) {
                return;
            }
            // Ports are built in the same order as the specs they describe, and matches()
            // verifies that order every pass, so index alignment is what ties a port to the
            // fresh spec list of this pass.
            for (int i = 0; i < target.size(); i++) {
                Port port = ports.get(i);
                if (target.get(i).sender) {
                    target.get(i).ownDraw = port.usedChannels();
                } else {
                    target.get(i).ownDraw = 0;
                }
            }
        }

        /** Severs the current listener links so a rebuilt pairing never leaves ghosts. */
        private void dropLinks() {
            for (IGridConnection link : new ArrayList<IGridConnection>(links)) {
                destroyConnection(link);
            }
            links.clear();
            lastAssignment = new Port[0];
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

        /** Channels currently flowing through every sender face of this group. */
        private int suppliedChannels() {
            int supplied = 0;
            for (Port port : ports) {
                if (port.spec.sender) {
                    supplied += port.usedChannels();
                }
            }
            return supplied;
        }



        /**
         * Channels this group's sender faces currently pull out of the upstream cables.
         * AE2 counts those in the touched nodes, so they are added back when working out
         * how many channels the path can carry for this link in total.
         */
        private int senderDraw() {
            int drawn = 0;
            for (Port port : ports) {
                if (port.spec.sender) {
                    drawn += port.usedChannels();
                }
            }
            return drawn;
        }

        /** Sender faces that currently reach a channel source. */
        private int supplyingCount() {
            int count = 0;
            for (Port port : ports) {
                if (port.spec.sender && port.spec.canSupply) {
                    count++;
                }
            }
            return count;
        }

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
                specs.get(i).canSupply = fresh.get(i).canSupply;
                specs.get(i).ownDraw = fresh.get(i).ownDraw;
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
            for (Port port : ports) {
                if (!port.spec.sender) {
                    receivers.add(port);
                }
            }
            if (receivers.isEmpty()) {
                return 0;
            }
            int[] demand = computeReceiverDemand(receivers);
            int total = 0;
            for (int value : demand) {
                total += value;
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
            boolean supplying = false;
            for (Port port : ports) {
                if (port.spec.sender) {
                    sender = true;
                    supplying |= port.spec.canSupply;
                } else {
                    listener = true;
                }
            }
            // A link with no sender reaching a channel source has nothing to hand over, so
            // it reports "missing" instead of a connected link carrying zero channels.
            return sender && listener && supplying;
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
        private final IManagedGridNode node;
        @Nullable
        private IGridConnection attachment;

        private Port(PortSpec spec, IManagedGridNode node) {
            this.spec = spec;
            this.node = node;
        }

        @Nullable
        private static Port create(PortSpec spec, int index) {
            World world = spec.owner.getWorld();
            BlockPos pos = spec.owner.getPos();
            if (world == null || pos == null || world.isRemote) {
                return null;
            }
            // Both ends are pure dense path nodes. Requesting a channel here would spend
            // one of this node's own slots (AE2 counts the requirement on the node it
            // belongs to), so a 32 channel link could only pass 31 channels through. Leaving
            // the request flag off keeps the whole capacity available for the traffic and lets
            // AE2's controller stay the only authority on channel numbers.
            IManagedGridNode managed = GridHelper.createManagedNode(new Object(), NOOP_LISTENER)
                    .setInWorldNode(false)
                    .setTagName("me_channel_caller_port_" + index)
                    .setFlags(GridFlags.DENSE_CAPACITY)
                    // The synthetic node must never be discovered by neighbouring cables or
                    // machines; every edge it owns is created explicitly below.
                    .setExposedOnSides(EnumSet.noneOf(EnumFacing.class));
            managed.create(world, pos);
            IGridNode created = managed.getNode();
            if (created == null) {
                managed.destroy();
                return null;
            }
            IGridConnection attachment = connect(spec.target, created);
            if (attachment == null) {
                managed.destroy();
                return null;
            }
            Port port = new Port(spec, managed);
            port.attachment = attachment;
            return port;
        }

        @Nullable
        private IGridNode node() {
            return node.getNode();
        }

        @Nullable
        private IGridConnection linkTo(@Nullable IGridNode other) {
            if (other == null || node.getNode() == null) {
                return null;
            }
            return connect(node.getNode(), other);
        }

        /** Channels currently routed through this port's link to its touched block. */
        private int usedChannels() {
            try {
                if (attachment == null || node.getNode() == null) {
                    return 0;
                }
                return Math.max(0, attachment.getUsedChannels());
            } catch (Throwable ignored) {
                return 0;
            }
        }

        private boolean isAlive() {
            IGridNode live = node.getNode();
            if (live == null || live.grid() == null) {
                return false;
            }
            return attachment != null && isAttached(attachment, live, spec.target);
        }

        private void destroy() {
            if (attachment != null) {
                destroyConnection(attachment);
                attachment = null;
            }
            try {
                node.destroy();
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
            return GridHelper.createConnection(first, second);
        } catch (Throwable error) {
            AE2Utilix.LOGGER.debug("ME channel caller could not link synthetic nodes", error);
            return null;
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
}
