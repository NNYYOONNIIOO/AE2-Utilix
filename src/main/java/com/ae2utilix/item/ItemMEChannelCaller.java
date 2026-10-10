package com.ae2utilix.item;

import com.ae2utilix.AE2Utilix;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/** The placeable item used to bind two ME Channel Caller blocks together. */
public class ItemMEChannelCaller extends ItemBlock {
    public static final String CHANNEL_ID_TAG = "ae2utilix_channel_id";

    public ItemMEChannelCaller(net.minecraft.block.Block block) {
        super(block);
        setUnlocalizedName(AE2Utilix.MODID + ".me_channel_caller");
        setCreativeTab(AE2Utilix.AE2_UTILIX_TAB);
        setMaxStackSize(64);
    }

    @Nullable
    public static UUID getChannelId(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTagCompound()) {
            return null;
        }
        NBTTagCompound tag = stack.getTagCompound();
        return tag.hasUniqueId(CHANNEL_ID_TAG) ? tag.getUniqueId(CHANNEL_ID_TAG) : null;
    }

    public static void setChannelId(ItemStack stack, UUID id) {
        if (stack == null || stack.isEmpty() || id == null) {
            return;
        }
        NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        tag.setUniqueId(CHANNEL_ID_TAG, id);
        stack.setTagCompound(tag);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip,
                               ITooltipFlag flag) {
        UUID id = getChannelId(stack);
        if (id == null) {
            tooltip.add(I18n.format("item.ae2_utilix.me_channel_caller.tooltip.unbound"));
        } else {
            tooltip.add(I18n.format("item.ae2_utilix.me_channel_caller.tooltip.id", id));
        }
    }
}
