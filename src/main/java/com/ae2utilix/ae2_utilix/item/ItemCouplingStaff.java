package com.ae2utilix.ae2_utilix.item;

import ae2.api.config.Actionable;
import ae2.items.tools.powered.powersink.AEBasePoweredItem;
import com.ae2utilix.ae2_utilix.AE2Utilix;
import com.ae2utilix.ae2_utilix.ClientUtil;
import com.ae2utilix.ae2_utilix.block.IPhaseLinkHost;
import com.ae2utilix.ae2_utilix.network.NetworkHandler;
import com.ae2utilix.ae2_utilix.network.PacketHighlightBlock;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Records a block position, then links a phase ME interface to it.
 *
 * <p>Long range attacks and linking both drain the internal AE buffer, mirroring the Forge
 * version of this tool.</p>
 */
public class ItemCouplingStaff extends AEBasePoweredItem {

    private static final double POWER_PER_ATTACK = 3000.0;
    private static final float ATTACK_DAMAGE = 30.0f;
    private static final double POWER_PER_RECORD = 5000.0;

    private static final String NBT_DIM = "ae2utilix_dim";
    private static final String NBT_X = "ae2utilix_x";
    private static final String NBT_Y = "ae2utilix_y";
    private static final String NBT_Z = "ae2utilix_z";
    private static final String NBT_FACE = "ae2utilix_face";

    public ItemCouplingStaff() {
        super(80000.0);
        this.setTranslationKey(AE2Utilix.MODID + ".coupling_staff");
        this.setRegistryName(AE2Utilix.MODID, "coupling_staff");
        this.setCreativeTab(AE2Utilix.AE2_UTILIX_TAB);
    }

    @Override
    public boolean hitEntity(ItemStack item, EntityLivingBase target, EntityLivingBase hitter) {
        if (this.getAECurrentPower(item) >= POWER_PER_ATTACK) {
            this.extractAEPower(item, POWER_PER_ATTACK, Actionable.MODULATE);
            DamageSource src;
            if (hitter instanceof EntityPlayer) {
                src = DamageSource.causePlayerDamage((EntityPlayer) hitter);
            } else {
                src = DamageSource.causeMobDamage(hitter);
            }
            src.setDamageBypassesArmor().setDamageIsAbsolute();
            target.attackEntityFrom(src, ATTACK_DAMAGE);
            return true;
        }
        return false;
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
                                           float hitX, float hitY, float hitZ, EnumHand hand) {
        if (!player.isSneaking()) {
            return EnumActionResult.PASS;
        }

        ItemStack held = player.getHeldItem(hand);
        if (held.isEmpty() || held.getItem() != this) {
            return EnumActionResult.PASS;
        }

        if (world.getTileEntity(pos) instanceof IPhaseLinkHost) {
            if (world.isRemote) {
                return EnumActionResult.SUCCESS;
            }
            IPhaseLinkHost host = (IPhaseLinkHost) world.getTileEntity(pos);
            if (hasRecord(held)) {
                Integer dim = getDimension(held);
                BlockPos linkPos = getPos(held);
                EnumFacing linkFace = getFace(held);
                if (dim != null && linkPos != null && linkFace != null) {
                    host.setLinkData(dim, linkPos, linkFace);
                    if (!host.isLinkValid()) {
                        host.clearLinkData();
                        ClientUtil.sendActionBar((EntityPlayerMP) player,
                                "item.ae2_utilix.coupling_staff.link_invalid");
                    } else {
                        ClientUtil.sendActionBar((EntityPlayerMP) player,
                                "item.ae2_utilix.coupling_staff.link_success");
                    }
                }
            } else if (host.hasLinkData()) {
                BlockPos linkPos = host.getLinkPos();
                Integer linkDim = host.getLinkDimension();
                if (linkPos != null && linkDim != null) {
                    NetworkHandler.CHANNEL.sendTo(new PacketHighlightBlock(linkPos, linkDim),
                            (EntityPlayerMP) player);
                    ClientUtil.sendActionBar((EntityPlayerMP) player,
                            "item.ae2_utilix.coupling_staff.highlight");
                }
            } else {
                ClientUtil.sendActionBar((EntityPlayerMP) player, "item.ae2_utilix.coupling_staff.no_link");
            }
            return EnumActionResult.SUCCESS;
        }

        if (world.isRemote) {
            return EnumActionResult.SUCCESS;
        }
        if (this.getAECurrentPower(held) < POWER_PER_RECORD) {
            return EnumActionResult.FAIL;
        }

        this.extractAEPower(held, POWER_PER_RECORD, Actionable.MODULATE);
        recordPosition(held, world.provider.getDimension(), pos, side);

        EnumFacing face = getFace(held);
        ClientUtil.sendActionBar((EntityPlayerMP) player, "item.ae2_utilix.coupling_staff.recorded",
                world.provider.getDimension(), pos.getX(), pos.getY(), pos.getZ(),
                face != null ? face.getName() : "?");
        return EnumActionResult.SUCCESS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        if (player.isSneaking() && held.getItem() == this && hasRecord(held)) {
            if (!world.isRemote) {
                clearRecord(held);
                ClientUtil.sendActionBar((EntityPlayerMP) player, "item.ae2_utilix.coupling_staff.cleared");
            }
            return new ActionResult<>(EnumActionResult.SUCCESS, held);
        }
        return new ActionResult<>(EnumActionResult.PASS, held);
    }

    public static void recordPosition(ItemStack stack, int dimension, BlockPos pos, EnumFacing face) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        NBTTagCompound tag = stack.getTagCompound();
        tag.setInteger(NBT_DIM, dimension);
        tag.setInteger(NBT_X, pos.getX());
        tag.setInteger(NBT_Y, pos.getY());
        tag.setInteger(NBT_Z, pos.getZ());
        tag.setInteger(NBT_FACE, face.ordinal());
    }

    public static void clearRecord(ItemStack stack) {
        if (stack.hasTagCompound()) {
            NBTTagCompound tag = stack.getTagCompound();
            tag.removeTag(NBT_DIM);
            tag.removeTag(NBT_X);
            tag.removeTag(NBT_Y);
            tag.removeTag(NBT_Z);
            tag.removeTag(NBT_FACE);
            if (tag.isEmpty()) {
                stack.setTagCompound(null);
            }
        }
    }

    public static boolean hasRecord(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTagCompound()) {
            return false;
        }
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey(NBT_DIM) && tag.hasKey(NBT_X) && tag.hasKey(NBT_Y)
                && tag.hasKey(NBT_Z) && tag.hasKey(NBT_FACE);
    }

    @Nullable
    public static Integer getDimension(ItemStack stack) {
        if (!hasRecord(stack)) {
            return null;
        }
        return stack.getTagCompound().getInteger(NBT_DIM);
    }

    @Nullable
    public static BlockPos getPos(ItemStack stack) {
        if (!hasRecord(stack)) {
            return null;
        }
        NBTTagCompound tag = stack.getTagCompound();
        return new BlockPos(tag.getInteger(NBT_X), tag.getInteger(NBT_Y), tag.getInteger(NBT_Z));
    }

    @Nullable
    public static EnumFacing getFace(ItemStack stack) {
        if (!hasRecord(stack)) {
            return null;
        }
        int ordinal = stack.getTagCompound().getInteger(NBT_FACE);
        if (ordinal >= 0 && ordinal < EnumFacing.values().length) {
            return EnumFacing.values()[ordinal];
        }
        return null;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addCheckedInformation(ItemStack stack, World world, List<String> lines,
                                      ITooltipFlag advancedTooltips) {
        super.addCheckedInformation(stack, world, lines, advancedTooltips);
        lines.add("");
        if (hasRecord(stack)) {
            NBTTagCompound tag = stack.getTagCompound();
            lines.add(I18n.format("item.ae2_utilix.coupling_staff.tooltip.dim", tag.getInteger(NBT_DIM)));
            lines.add(I18n.format("item.ae2_utilix.coupling_staff.tooltip.pos",
                    tag.getInteger(NBT_X), tag.getInteger(NBT_Y), tag.getInteger(NBT_Z)));
            EnumFacing face = getFace(stack);
            if (face != null) {
                lines.add(I18n.format("item.ae2_utilix.coupling_staff.tooltip.face",
                        I18n.format("enumfacing." + face.getName())));
            }
        } else {
            lines.add(I18n.format("item.ae2_utilix.coupling_staff.tooltip.empty"));
        }
    }
}