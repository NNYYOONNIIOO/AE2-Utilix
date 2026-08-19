package com.ae2utilix.item;

import appeng.api.AEApi;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
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
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
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
        if (event.getWorld().isRemote) {
            return;
        }

        EntityPlayer player = event.getEntityPlayer();
        EnumHand hand = event.getHand();
        ItemStack packageStack = player.getHeldItem(hand);
        if (!isPartPackage(packageStack)
                || !player.canPlayerEdit(event.getPos(), event.getFace(), packageStack)) {
            return;
        }

        // Match ExtendedAE's useOn flow: handle the package before an existing
        // cable host can process the same click, then cancel the event after the
        // part has been placed so it cannot be placed twice.
        if (placePartPackage(player, event.getWorld(), event.getPos(), event.getFace(), hand, packageStack)
                == EnumActionResult.SUCCESS) {
            event.setCanceled(true);
        }
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
            float hitX, float hitY, float hitZ, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        if (!isPartPackage(held)) {
            return EnumActionResult.PASS;
        }
        if (!player.canPlayerEdit(pos, side, held)) {
            return EnumActionResult.FAIL;
        }
        // The client must return PASS so PlayerControllerMP sends the normal
        // right-click packet to the server. Returning SUCCESS here ends the
        // client interaction before the server can place and consume the package.
        if (world.isRemote) {
            return EnumActionResult.PASS;
        }
        return placePartPackage(player, world, pos, side, hand, held);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
            EnumFacing side, float hitX, float hitY, float hitZ) {
        ItemStack held = player.getHeldItem(hand);
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
            return placePartPackage(player, world, pos, side, hand, held);
        }
        return placeBlockPackage(player, world, pos, side, hitX, hitY, hitZ, hand, held);
    }

    private EnumActionResult placePartPackage(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
            EnumHand hand, ItemStack packageStack) {
        ItemStack partStack = getTargetStack(packageStack);
        if (!(partStack.getItem() instanceof appeng.api.parts.IPartItem)) {
            return EnumActionResult.FAIL;
        }

        PartPlacement.Placement placement = PartPlacement.getPartPlacement(player, world, partStack, pos, side);
        if (placement == null) {
            return EnumActionResult.FAIL;
        }

        // placePart only creates the part. Do not call PartPlacement.place here:
        // that method also consumes the supplied stack and manipulates the
        // player's hand, which is the source of the cable-host duplication path.
        IPart placed = PartPlacement.placePart(player, world, partStack,
                placement.pos(), placement.side(), hand);
        if (placed == null) {
            return EnumActionResult.FAIL;
        }

        IPartHost host = AEApi.instance().partHelper().getPartHost(world, placement.pos());
        placed.readFromNBT(getStoredData(packageStack));
        if (host != null) {
            host.markForSave();
            host.markForUpdate();
            host.notifyNeighbors();
        }
        consumePackage(player, hand, packageStack);
        return EnumActionResult.SUCCESS;
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
        if (!player.isCreative()) {
            // Match ExtendedAE's ItemPackedDevice: keep the package stack captured
            // before placement and shrink that exact stack after placePart returns.
            // AE2 may change the selected hand while adding a part to an existing
            // cable host, so reading the hand again here can miss the package.
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
