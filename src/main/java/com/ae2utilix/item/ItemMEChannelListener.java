package com.ae2utilix.item;

import com.ae2utilix.AE2Utilix;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class ItemMEChannelListener extends ItemMEChannelCaller {
    public ItemMEChannelListener(net.minecraft.block.Block block) {
        super(block);
        setUnlocalizedName(AE2Utilix.MODID + ".me_channel_listener");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip,
                               ITooltipFlag flag) {
        UUID id = getChannelId(stack);
        if (id == null) {
            tooltip.add(I18n.format("item.ae2_utilix.me_channel_listener.tooltip.unbound"));
        } else {
            tooltip.add(I18n.format("item.ae2_utilix.me_channel_listener.tooltip.id", id));
        }
    }
}
