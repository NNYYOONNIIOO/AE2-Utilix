package com.ae2utilix.ae2_utilix.item;

import com.ae2utilix.ae2_utilix.AE2Utilix;
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
        setTranslationKey(AE2Utilix.MODID + ".me_channel_caller");
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

    /**
     * The unified item doubles as the listener item: metadata 3 or above shows the
     * listener name. Cleanroom maps {@code getItemStackDisplayName} to its SRG name, so
     * this overrides {@code func_77653_i} directly.
     */
    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        String key = stack != null && stack.getMetadata() >= 3
                ? "tile." + AE2Utilix.MODID + ".me_channel_listener"
                : "tile." + AE2Utilix.MODID + ".me_channel_caller";
        return net.minecraft.util.text.translation.I18n.translateToLocal(key + ".name");
    }

    @SideOnly(Side.CLIENT)
    @Override
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
