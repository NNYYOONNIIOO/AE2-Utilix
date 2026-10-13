package com.ae2utilix.ae2_utilix.item;

import ae2.api.parts.IPartHost;
import ae2.api.parts.SelectedPart;
import com.ae2utilix.ae2_utilix.AE2Utilix;
import com.ae2utilix.ae2_utilix.upgrade.ModUpgradeRegistry;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import ae2.api.upgrades.IUpgradeInventory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ItemProductReturnCard extends Item implements com.ae2utilix.ae2_utilix.upgrade.IAE2UtilixUpgradeModule {

    public ItemProductReturnCard() {
        this.setTranslationKey(AE2Utilix.MODID + ".product_return_card");
        
        this.setMaxStackSize(64);
        this.setCreativeTab(AE2Utilix.AE2_UTILIX_TAB);
    }

    @Override
    public String getUpgradeTypeId() {
        return AE2Utilix.UPGRADE_PRODUCT_RETURN;
    }

    @Override
    public int getMaxInstalled() {
        return 1;
    }

    @Override
    public boolean matchesType(ItemStack stack) {
        return stack.getItem() instanceof ItemProductReturnCard;
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, EnumHand hand) {
        if (!player.isSneaking()) {
            return EnumActionResult.PASS;
        }

        ItemStack held = player.getHeldItem(hand);
        if (held.isEmpty() || held.getItem() != this) {
            return EnumActionResult.PASS;
        }

        IUpgradeInventory upgrades = null;
        TileEntity te = world.getTileEntity(pos);

        if (te instanceof IPartHost) {
            SelectedPart sp = ((IPartHost) te).selectPartWorld(new Vec3d(hitX, hitY, hitZ));
            if (sp.part instanceof ae2.api.upgrades.IUpgradeableObject) {
                upgrades = ((ae2.api.upgrades.IUpgradeableObject) sp.part).getUpgrades();
            }
        } else if (te instanceof ae2.api.upgrades.IUpgradeableObject) {
            upgrades = ((ae2.api.upgrades.IUpgradeableObject) te).getUpgrades();
        }

        if (upgrades != null) {
            if (world.isRemote) {
                return EnumActionResult.SUCCESS;
            }

            ItemStack oneCard = new ItemStack(this, 1);
            for (int i = 0; i < upgrades.size(); i++) {
                if (upgrades.isItemValid(i, oneCard) && upgrades.getStackInSlot(i).isEmpty()) {
                    upgrades.insertItem(i, oneCard, false);
                    held.shrink(1);
                    return EnumActionResult.SUCCESS;
                }
            }
        }

        return EnumActionResult.PASS;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        tooltip.add(I18n.format("item.ae2_utilix.product_return_card.tooltip.1"));
        addSupportedTooltip(getUpgradeTypeId(), tooltip);
    }

    @SideOnly(Side.CLIENT)
    static void addSupportedTooltip(String upgradeTypeId, List<String> tooltip) {
        Map<ItemStack, Integer> supported = ModUpgradeRegistry.getSupported(upgradeTypeId);
        if (supported.isEmpty()) return;
        tooltip.add("");
        List<String> names = new ArrayList<>();
        for (Map.Entry<ItemStack, Integer> entry : supported.entrySet()) {
            String name = entry.getKey().getDisplayName();
            int limit = entry.getValue();
            if (limit > 1) {
                name = name + " (" + limit + ')';
            }
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        tooltip.addAll(names);
    }
}
