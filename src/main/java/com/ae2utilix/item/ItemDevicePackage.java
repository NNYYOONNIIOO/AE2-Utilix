package com.ae2utilix.item;

import appeng.api.AEApi;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import appeng.parts.PartPlacement;
import com.ae2utilix.AE2Utilix;
import net.minecraft.block.Block;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * An item that contains an AE device together with its complete persistent state.
 * The tag deliberately stores the original ItemStack and TileEntity/part data
 * separately, mirroring how AE saves parts in a cable bus.
 */
public class ItemDevicePackage extends Item {

    public static final String KIND = "Kind";
    public static final String STACK = "Stack";
    public static final String DATA = "Data";
    public static final String NAME = "Name";
    public static final String SIDE = "Side";

    public static final String KIND_BLOCK = "block";
    public static final String KIND_PART = "part";

    private static void debugLog(String format, Object... args) {
        if (AE2Utilix.LOGGER != null) {
            AE2Utilix.LOGGER.info("[DevicePackageDebug] " + String.format(format, args));
        }
    }

    private static String describeStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "EMPTY";
        }

        String itemName = stack.getItem() == null ? "null" : String.valueOf(stack.getItem().getRegistryName());
        NBTTagCompound tag = stack.getTagCompound();
        String kind = tag == null ? "-" : tag.getString(KIND);
        String target = "-";
        int targetDamage = -1;
        if (tag != null && tag.hasKey(STACK, 10)) {
            NBTTagCompound stored = tag.getCompoundTag(STACK);
            target = stored.getString("id");
            targetDamage = stored.getShort("Damage");
        }
        return String.format("item=%s,count=%d,damage=%d,kind=%s,target=%s,targetDamage=%d",
                itemName, stack.getCount(), stack.getItemDamage(), kind, target, targetDamage);
    }

    public ItemDevicePackage() {
        this.setUnlocalizedName(AE2Utilix.MODID + ".device_package");
        this.setRegistryName(AE2Utilix.MODID, "device_package");
        this.setMaxStackSize(1);
        this.setCreativeTab(AE2Utilix.AE2_UTILIX_TAB);
    }

    public static ItemStack createBlockPackage(ItemStack blockStack, NBTTagCompound tileData) {
        return createPackage(KIND_BLOCK, blockStack, tileData, null);
    }

    public static ItemStack createPartPackage(ItemStack partStack, NBTTagCompound partData, int side) {
        return createPackage(KIND_PART, partStack, partData, side);
    }

    private static ItemStack createPackage(String kind, ItemStack targetStack,
            NBTTagCompound data, @Nullable Integer side) {
        ItemStack result = new ItemStack(AE2Utilix.DEVICE_PACKAGE);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(KIND, kind);

        ItemStack storedStack = targetStack.copy();
        storedStack.setCount(1);
        NBTTagCompound stackTag = new NBTTagCompound();
        storedStack.writeToNBT(stackTag);
        tag.setTag(STACK, stackTag);
        tag.setTag(DATA, data.copy());
        tag.setString(NAME, storedStack.getDisplayName());
        if (side != null) {
            tag.setInteger(SIDE, side);
        }
        result.setTagCompound(tag);
        return result;
    }

    public static boolean isValidPackage(ItemStack packageStack) {
        if (packageStack == null || packageStack.isEmpty()
                || packageStack.getItem() != AE2Utilix.DEVICE_PACKAGE
                || !packageStack.hasTagCompound()) {
            return false;
        }

        NBTTagCompound tag = packageStack.getTagCompound();
        if (tag == null || (!KIND_BLOCK.equals(tag.getString(KIND)) && !KIND_PART.equals(tag.getString(KIND)))) {
            return false;
        }
        if (!tag.hasKey(STACK, 10) || !tag.hasKey(DATA, 10)) {
            return false;
        }

        ItemStack target = new ItemStack(tag.getCompoundTag(STACK));
        return !target.isEmpty() && target.getItem() != AE2Utilix.DEVICE_PACKAGE;
    }

    public static boolean isPartPackage(ItemStack packageStack) {
        return isValidPackage(packageStack) && KIND_PART.equals(packageStack.getTagCompound().getString(KIND));
    }

    public static boolean isBlockPackage(ItemStack packageStack) {
        return isValidPackage(packageStack) && KIND_BLOCK.equals(packageStack.getTagCompound().getString(KIND));
    }

    public static ItemStack getTargetStack(ItemStack packageStack) {
        if (!isValidPackage(packageStack)) {
            return ItemStack.EMPTY;
        }
        ItemStack target = new ItemStack(packageStack.getTagCompound().getCompoundTag(STACK));
        if (target.isEmpty() || target.getItem() == AE2Utilix.DEVICE_PACKAGE) {
            return ItemStack.EMPTY;
        }
        target.setCount(1);
        return target;
    }

    public static NBTTagCompound getStoredData(ItemStack packageStack) {
        if (!isValidPackage(packageStack)) {
            return new NBTTagCompound();
        }
        return packageStack.getTagCompound().getCompoundTag(DATA).copy();
    }

    private static NBTTagCompound getPlacementData(ItemStack packageStack) {
        NBTTagCompound data = getStoredData(packageStack);
        // This is the old part's runtime grid-node state. A newly placed part
        // already belongs to its new cable grid, so loading it after addPart()
        // throws "Loading data after part of a grid, this is invalid".
        data.removeTag("part");
        return data;
    }

    public static int getPartSide(ItemStack packageStack) {
        if (!isPartPackage(packageStack)) {
            return -1;
        }
        return packageStack.getTagCompound().getInteger(SIDE);
    }

    @Nullable
    public static String getPackedName(ItemStack packageStack) {
        if (!isValidPackage(packageStack)) {
            return null;
        }
        NBTTagCompound tag = packageStack.getTagCompound();
        String name = tag.getString(NAME);
        if (!name.isEmpty()) {
            return name;
        }
        ItemStack target = getTargetStack(packageStack);
        return target.isEmpty() ? null : target.getDisplayName();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        EntityPlayer player = event.getEntityPlayer();
        EnumHand hand = event.getHand();
        ItemStack packageStack = player.getHeldItem(hand);
        if (packageStack.isEmpty() || packageStack.getItem() != AE2Utilix.DEVICE_PACKAGE) {
            return;
        }

        debugLog("RightClickBlock remote=%s player=%s hand=%s pos=%s face=%s canceled=%s package=%s",
                event.getWorld().isRemote, player.getName(), hand, event.getPos(), event.getFace(),
                event.isCanceled(), describeStack(packageStack));
        if (!isPartPackage(packageStack)
                || !player.canPlayerEdit(event.getPos(), event.getFace(), packageStack)) {
            debugLog("RightClickBlock ignored validPart=%s canEdit=%s",
                    isPartPackage(packageStack), player.canPlayerEdit(event.getPos(), event.getFace(), packageStack));
            return;
        }

        // A normal right-click gives the cable block the first chance to handle
        // the interaction. Claim both uses before it reaches that block. The
        // client only suppresses its local cable activation; the server below
        // performs the authoritative placement and consumption.
        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);
        debugLog("RightClickBlock intercepted remote=%s useBlock/useItem=DENY handBefore=%s",
                event.getWorld().isRemote, describeStack(player.getHeldItem(hand)));
        if (event.getWorld().isRemote) {
            return;
        }

        // Stop lower-priority cable handlers before placing the part. If the
        // placement cannot be completed, restore the normal interaction below.
        event.setCanceled(true);
        EnumActionResult result = placePartPackage(player, event.getWorld(), event.getPos(), event.getFace(), hand,
                packageStack);
        debugLog("RightClickBlock placementResult=%s canceledBefore=%s handAfter=%s",
                result, event.isCanceled(), describeStack(player.getHeldItem(hand)));
        if (result != EnumActionResult.SUCCESS) {
            event.setCanceled(false);
            // No valid placement: preserve the normal cable/block interaction.
            event.setUseBlock(Event.Result.DEFAULT);
            event.setUseItem(Event.Result.DEFAULT);
        }
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
            float hitX, float hitY, float hitZ, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        if (!held.isEmpty() && held.getItem() == AE2Utilix.DEVICE_PACKAGE) {
            debugLog("onItemUseFirst remote=%s player=%s hand=%s pos=%s face=%s package=%s",
                    world.isRemote, player.getName(), hand, pos, side, describeStack(held));
        }
        if (!isPartPackage(held)) {
            return EnumActionResult.PASS;
        }
        if (!player.canPlayerEdit(pos, side, held)) {
            return EnumActionResult.FAIL;
        }
        // Follow ItemPacker's 1.12.2 interaction path: claim the cable click on
        // the client, then perform the actual placement on the server. This
        // prevents the cable block from handling the same click first.
        if (world.isRemote) {
            debugLog("onItemUseFirst clientResult=SUCCESS package=%s", describeStack(held));
            return EnumActionResult.SUCCESS;
        }
        EnumActionResult result = placePartPackage(player, world, pos, side, hand, held);
        debugLog("onItemUseFirst serverResult=%s handAfter=%s", result, describeStack(player.getHeldItem(hand)));
        return result;
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
            EnumFacing side, float hitX, float hitY, float hitZ) {
        ItemStack held = player.getHeldItem(hand);
        if (!held.isEmpty() && held.getItem() == AE2Utilix.DEVICE_PACKAGE) {
            debugLog("onItemUse remote=%s player=%s hand=%s pos=%s face=%s package=%s",
                    world.isRemote, player.getName(), hand, pos, side, describeStack(held));
        }
        if (!isValidPackage(held)) {
            return EnumActionResult.FAIL;
        }
        if (!player.canPlayerEdit(pos, side, held)) {
            return EnumActionResult.FAIL;
        }
        if (world.isRemote) {
            return EnumActionResult.SUCCESS;
        }

        if (isPartPackage(held)) {
            EnumActionResult result = placePartPackage(player, world, pos, side, hand, held);
            debugLog("onItemUse partResult=%s handAfter=%s", result, describeStack(player.getHeldItem(hand)));
            return result;
        }
        return placeBlockPackage(player, world, pos, side, hitX, hitY, hitZ, hand, held);
    }

    private EnumActionResult placePartPackage(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
            EnumHand hand, ItemStack packageStack) {
        debugLog("placePartPackage START remote=%s player=%s hand=%s pos=%s side=%s package=%s",
                world.isRemote, player.getName(), hand, pos, side, describeStack(packageStack));
        ItemStack partStack = getTargetStack(packageStack);
        if (!(partStack.getItem() instanceof appeng.api.parts.IPartItem)) {
            debugLog("placePartPackage FAIL targetNotPart target=%s", describeStack(partStack));
            return EnumActionResult.FAIL;
        }

        NBTTagCompound placementData = getPlacementData(packageStack);
        debugLog("placePartPackage sanitizedData hasPart=%s keys=%s",
                placementData.hasKey("part", 10), placementData.getKeySet());

        debugLog("placePartPackage target=%s", describeStack(partStack));

        PartPlacement.Placement placement = PartPlacement.getPartPlacement(player, world, partStack, pos, side);
        if (placement == null) {
            debugLog("placePartPackage FAIL noPlacement pos=%s side=%s", pos, side);
            return EnumActionResult.FAIL;
        }

        debugLog("placePartPackage placement=%s/%s packageBefore=%s handBefore=%s",
                placement.pos(), placement.side(), describeStack(packageStack), describeStack(player.getHeldItem(hand)));

        // Reserve one package before touching an existing cable host. This is
        // the important difference from consuming after placement: if AE2 or
        // Forge restores the original hand reference, it already has the
        // decremented count and cannot duplicate the package.
        int originalCount = packageStack.getCount();
        packageStack.shrink(1);
        player.setHeldItem(hand, packageStack.isEmpty() ? ItemStack.EMPTY : packageStack);
        debugLog("placePartPackage preConsumed originalCount=%d packageRef=%s handAfterPreConsume=%s",
                originalCount, describeStack(packageStack), describeStack(player.getHeldItem(hand)));
        boolean placedSuccessfully = false;
        IPart placed = null;
        IPartHost host = null;
        try {
            // placePart only creates the part. As in ExtendedAE, pass no hand so
            // AE2 cannot apply normal IPartItem consumption to our package slot.
            placed = PartPlacement.placePart(player, world, partStack,
                    placement.pos(), placement.side(), null);
            if (placed == null) {
                debugLog("placePartPackage FAIL placePartReturnedNull package=%s hand=%s",
                        describeStack(packageStack), describeStack(player.getHeldItem(hand)));
                return EnumActionResult.FAIL;
            }

            host = AEApi.instance().partHelper().getPartHost(world, placement.pos());
            debugLog("placePartPackage placePartSuccess part=%s host=%s package=%s hand=%s",
                    placed.getClass().getName(), host == null ? "null" : host.getClass().getName(),
                    describeStack(packageStack), describeStack(player.getHeldItem(hand)));
            restorePartData(placed, placementData);
            if (host != null) {
                host.markForSave();
                host.markForUpdate();
            }
            placedSuccessfully = true;
            debugLog("placePartPackage SUCCESS package=%s hand=%s", describeStack(packageStack),
                    describeStack(player.getHeldItem(hand)));
            return EnumActionResult.SUCCESS;
        } catch (RuntimeException ex) {
            debugLog("placePartPackage FAIL exception=%s package=%s hand=%s",
                    ex.toString(), describeStack(packageStack), describeStack(player.getHeldItem(hand)));
            if (host != null && placed != null) {
                AEPartLocation location = AEPartLocation.fromFacing(placement.side());
                if (host.getPart(location) == placed) {
                    host.removePart(location, false);
                    host.markForSave();
                    host.markForUpdate();
                    debugLog("placePartPackage rolledBackPart location=%s", location);
                }
            }
            return EnumActionResult.FAIL;
        } finally {
            if (!placedSuccessfully) {
                packageStack.setCount(originalCount);
            }
            player.setHeldItem(hand, packageStack.isEmpty() ? ItemStack.EMPTY : packageStack);
            player.inventory.markDirty();
            debugLog("placePartPackage END success=%s restored=%s handFinal=%s",
                    placedSuccessfully, describeStack(packageStack), describeStack(player.getHeldItem(hand)));
        }
    }

    /**
     * A 1.12.2 AE2 part is attached to the cable bus, and consequently to the
     * new grid, inside PartPlacement.placePart(). A normal readFromNBT call at
     * this point makes GridNode reject the load with "Loading data after part
     * of a grid". The package contains the old part's settings, not its old
     * network membership, so temporarily detach the new node's grid reference
     * while AE2 reads the settings and restore the live grid immediately after.
     */
    private static void restorePartData(IPart part, NBTTagCompound data) {
        final java.util.List<GridReference> detached = new java.util.ArrayList<>();
        final java.util.Set<Object> visited = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<Object, Boolean>());
        final java.util.ArrayDeque<Object> pending = new java.util.ArrayDeque<>();
        pending.add(part);

        try {
            int scanned = 0;
            while (!pending.isEmpty() && scanned++ < 256) {
                Object current = pending.removeFirst();
                if (current == null || !visited.add(current) || !isAppEngObject(current)) {
                    continue;
                }

                Class<?> type = current.getClass();
                while (type != null && type != Object.class) {
                    for (Field field : type.getDeclaredFields()) {
                        final int modifiers = field.getModifiers();
                        if (Modifier.isStatic(modifiers) || field.getType().isPrimitive()) {
                            continue;
                        }

                        try {
                            field.setAccessible(true);
                            Object value = field.get(current);
                            if ("myGrid".equals(field.getName())
                                    && "appeng.me.Grid".equals(field.getType().getName())) {
                                if (value != null) {
                                    field.set(current, null);
                                    detached.add(new GridReference(current, field, value));
                                    debugLog("restorePartData detachedGrid owner=%s",
                                            current.getClass().getName());
                                }
                                continue;
                            }

                            if (isAppEngObject(value)) {
                                pending.addLast(value);
                            }
                        } catch (Exception ex) {
                            debugLog("restorePartData fieldSkipped owner=%s field=%s error=%s",
                                    current.getClass().getName(), field.getName(), ex.toString());
                        }
                    }
                    type = type.getSuperclass();
                }
            }

            if (detached.isEmpty()) {
                debugLog("restorePartData noGridReferenceFound part=%s", part.getClass().getName());
            }
            part.readFromNBT(data);
        } finally {
            for (int index = detached.size() - 1; index >= 0; index--) {
                GridReference reference = detached.get(index);
                try {
                    reference.field.setAccessible(true);
                    reference.field.set(reference.owner, reference.originalGrid);
                } catch (Exception ex) {
                    debugLog("restorePartData restoreGridFailed owner=%s error=%s",
                            reference.owner.getClass().getName(), ex.toString());
                }
            }
        }
    }

    private static boolean isAppEngObject(Object value) {
        if (value == null) {
            return false;
        }
        String className = value.getClass().getName();
        return className.startsWith("appeng.") || className.startsWith("com.ae2utilix.");
    }

    private static final class GridReference {
        private final Object owner;
        private final Field field;
        private final Object originalGrid;

        private GridReference(Object owner, Field field, Object originalGrid) {
            this.owner = owner;
            this.field = field;
            this.originalGrid = originalGrid;
        }
    }

    private EnumActionResult placeBlockPackage(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
            float hitX, float hitY, float hitZ, EnumHand hand, ItemStack packageStack) {
        ItemStack blockStack = getTargetStack(packageStack);
        if (!(blockStack.getItem() instanceof ItemBlock)) {
            return EnumActionResult.FAIL;
        }

        BlockPos placementPos = world.getBlockState(pos).getBlock().isReplaceable(world, pos)
                ? pos : pos.offset(side);
        Item targetItem = blockStack.getItem();
        ItemStack originalHeld = player.getHeldItem(hand);
        EnumActionResult result;
        // ItemBlock's 1.12.2 placement API reads the stack from the player's
        // hand, so temporarily expose the stored block stack to its original
        // placement code and always restore the package afterwards.
        player.setHeldItem(hand, blockStack);
        try {
            result = targetItem.onItemUse(player, world, pos, hand, side, hitX, hitY, hitZ);
        } finally {
            player.setHeldItem(hand, originalHeld);
        }
        if (result != EnumActionResult.SUCCESS) {
            return result;
        }

        TileEntity tile = world.getTileEntity(placementPos);
        if (tile == null && !placementPos.equals(pos)) {
            tile = world.getTileEntity(pos);
            if (tile != null) {
                placementPos = pos;
            }
        }
        if (tile == null) {
            return EnumActionResult.FAIL;
        }

        NBTTagCompound data = getStoredData(packageStack);
        data.setInteger("x", placementPos.getX());
        data.setInteger("y", placementPos.getY());
        data.setInteger("z", placementPos.getZ());
        tile.readFromNBT(data);
        tile.markDirty();
        world.notifyBlockUpdate(placementPos, world.getBlockState(placementPos),
                world.getBlockState(placementPos), 3);
        consumePackage(player, hand, packageStack);
        return EnumActionResult.SUCCESS;
    }

    private static void consumePackage(EntityPlayer player, EnumHand hand, ItemStack packageStack) {
        // Match ExtendedAE's ItemPackedDevice: keep the package stack captured
        // before placement and shrink that exact stack after placePart returns.
        // This is intentionally not guarded by isCreative(); EAE consumes a
        // successfully placed package in creative mode as well.
        if (packageStack.isEmpty() || packageStack.getItem() != AE2Utilix.DEVICE_PACKAGE) {
            return;
        }
        packageStack.shrink(1);
        if (packageStack.isEmpty()) {
            player.setHeldItem(hand, ItemStack.EMPTY);
        } else {
            player.setHeldItem(hand, packageStack);
        }
        player.inventory.markDirty();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        String name = getPackedName(stack);
        if (name == null) {
            tooltip.add(I18n.format("item.ae2_utilix.device_package.empty"));
        } else {
            tooltip.add(I18n.format("item.ae2_utilix.device_package.tooltip", name));
        }
    }
}
