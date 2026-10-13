package com.ae2utilix.ae2_utilix.item;

import ae2.api.config.Actionable;
import ae2.items.tools.powered.powersink.AEBasePoweredItem;
import com.ae2utilix.ae2_utilix.AE2Utilix;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ItemMatterDecomposer extends AEBasePoweredItem {

    private static final double MAX_POWER = 10000000.0;

    private static final double POWER_ANNIHILATION = 80000.0;
    private static final double POWER_BRITTLE = 5000.0;
    private static final double POWER_REGULAR = 10000.0;

    private static final Set<String> TOOL_TYPES = new HashSet<>(Arrays.asList("pickaxe", "axe", "shovel"));

    private static final float EFFICIENCY_DIAMOND = 8.0f;
    private static final float EFFICIENCY_IRON = 6.0f;
    private static final float EFFICIENCY_INSTANT = 9999.0f;

    private static final String NBT_MODE = "ae2utilix_mode";

    public enum DecomposerMode {
        ANNIHILATION(0),
        BRITTLE(1),
        REGULAR(2);

        public final int index;

        DecomposerMode(int index) {
            this.index = index;
        }

        public DecomposerMode next() {
            return VALUES[(this.index + 1) % VALUES.length];
        }

        public DecomposerMode previous() {
            return VALUES[(this.index - 1 + VALUES.length) % VALUES.length];
        }

        private static final DecomposerMode[] VALUES = values();

        public static DecomposerMode fromIndex(int index) {
            if (index >= 0 && index < VALUES.length) return VALUES[index];
            return REGULAR;
        }
    }

    public ItemMatterDecomposer() {
        super(MAX_POWER);
        this.setTranslationKey(AE2Utilix.MODID + ".matter_decomposer");
        this.setRegistryName(AE2Utilix.MODID, "matter_decomposer");
        this.setCreativeTab(AE2Utilix.AE2_UTILIX_TAB);
        this.setHarvestLevel("pickaxe", 3);
        this.setHarvestLevel("axe", 3);
        this.setHarvestLevel("shovel", 3);
        MinecraftForge.EVENT_BUS.register(DecomposerEventHandler.class);
    }

    public static DecomposerMode getMode(ItemStack stack) {
        if (stack.getItem() instanceof ItemMatterDecomposer) {
            NBTTagCompound tag = stack.getTagCompound();
            if (tag != null && tag.hasKey(NBT_MODE)) {
                return DecomposerMode.fromIndex(tag.getInteger(NBT_MODE));
            }
        }
        return DecomposerMode.REGULAR;
    }

    public static void setMode(ItemStack stack, DecomposerMode mode) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setInteger(NBT_MODE, mode.index);
    }

    private static double getPowerCost(ItemStack stack) {
        switch (getMode(stack)) {
            case ANNIHILATION: return POWER_ANNIHILATION;
            case BRITTLE: return POWER_BRITTLE;
            case REGULAR: return POWER_REGULAR;
            default: return POWER_REGULAR;
        }
    }

    static boolean hasPowerForMode(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemMatterDecomposer)) return false;
        return ((ItemMatterDecomposer) stack.getItem()).getAECurrentPower(stack) >= getPowerCost(stack);
    }

    @Override
    public boolean isDamaged(ItemStack stack) {
        return false;
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return true;
    }

    @Override
    public boolean canHarvestBlock(IBlockState state) {
        return true;
    }

    @Override
    public int getHarvestLevel(ItemStack stack, String toolClass, EntityPlayer player, IBlockState blockState) {
        if (!TOOL_TYPES.contains(toolClass)) return -1;
        DecomposerMode mode = getMode(stack);
        if (mode == DecomposerMode.ANNIHILATION) return 100;
        return hasPowerForMode(stack) ? 3 : 2;
    }

    @Override
    public Set<String> getToolClasses(ItemStack stack) {
        return TOOL_TYPES;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, IBlockState state) {
        DecomposerMode mode = getMode(stack);

        if (mode == DecomposerMode.ANNIHILATION) {
            return hasPowerForMode(stack) ? EFFICIENCY_INSTANT : EFFICIENCY_IRON;
        }

        Material material = state.getMaterial();

        if (mode == DecomposerMode.BRITTLE) {
            if (material == Material.GLASS) {
                return hasPowerForMode(stack) ? EFFICIENCY_INSTANT : EFFICIENCY_IRON;
            }
            return 1.0f;
        }

        boolean pickaxeEffective = material == Material.IRON || material == Material.ANVIL || material == Material.ROCK;
        boolean axeEffective = material == Material.WOOD || material == Material.PLANTS || material == Material.VINE;
        boolean shovelEffective = material == Material.GROUND || material == Material.GRASS || material == Material.SAND
                || material == Material.SNOW || material == Material.CRAFTED_SNOW || material == Material.CLAY;
        if (pickaxeEffective || axeEffective || shovelEffective) {
            return hasPowerForMode(stack) ? EFFICIENCY_DIAMOND : EFFICIENCY_IRON;
        }
        return 1.0f;
    }

    @Override
    public boolean onBlockDestroyed(ItemStack stack, World worldIn, IBlockState state, BlockPos pos, EntityLivingBase entityLiving) {
        if (entityLiving instanceof EntityPlayer && !worldIn.isRemote) {
            extractAEPower(stack, getPowerCost(stack), Actionable.MODULATE);
        }
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addCheckedInformation(ItemStack stack, World world, List<String> lines, ITooltipFlag advancedTooltips) {
        super.addCheckedInformation(stack, world, lines, advancedTooltips);

        DecomposerMode currentMode = getMode(stack);
        lines.add(net.minecraft.client.resources.I18n.format("item.ae2_utilix.matter_decomposer.mode_label").trim());
        for (DecomposerMode mode : DecomposerMode.VALUES) {
            String modeName = net.minecraft.client.resources.I18n.format("item.ae2_utilix.matter_decomposer.mode." + mode.name().toLowerCase());
            TextFormatting color = mode == currentMode ? TextFormatting.WHITE : TextFormatting.GRAY;
            lines.add(color + " \u00b7 " + modeName);
        }
    }

    public static class DecomposerEventHandler {
        @SubscribeEvent
        public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
            EntityPlayer player = event.getEntityPlayer();
            ItemStack heldItem = player.getHeldItemMainhand();

            if (heldItem.getItem() != AE2Utilix.MATTER_DECOMPOSER) return;
            if (!hasPowerForMode(heldItem)) return;

            DecomposerMode mode = getMode(heldItem);

            if (mode == DecomposerMode.ANNIHILATION) {
                if (!player.onGround) {
                    event.setNewSpeed(event.getNewSpeed() * 5);
                }
                if (player.isInsideOfMaterial(Material.WATER) &&
                        !EnchantmentHelper.getAquaAffinityModifier(player)) {
                    event.setNewSpeed(event.getNewSpeed() * 5);
                }
                return;
            }

            if (mode == DecomposerMode.BRITTLE) {
                IBlockState state = event.getState();
                if (state.getMaterial() == Material.GLASS) {
                    event.setNewSpeed(EFFICIENCY_INSTANT);
                }
                return;
            }
        }

        @SubscribeEvent
        public static void onHarvestDrops(BlockEvent.HarvestDropsEvent event) {
            EntityPlayer harvester = event.getHarvester();
            if (harvester == null) return;

            ItemStack heldItem = harvester.getHeldItemMainhand();
            if (heldItem.getItem() != AE2Utilix.MATTER_DECOMPOSER) return;
            if (!hasPowerForMode(heldItem)) return;

            DecomposerMode mode = getMode(heldItem);
            if (mode != DecomposerMode.BRITTLE && mode != DecomposerMode.ANNIHILATION) return;

            IBlockState state = event.getState();
            if (state.getMaterial() != Material.GLASS) return;

            if (event.getDrops().isEmpty()) {
                Block block = state.getBlock();
                net.minecraft.item.Item item = net.minecraft.item.Item.getItemFromBlock(block);
                if (item != Items.AIR) {
                    event.getDrops().add(new ItemStack(item, 1, block.getMetaFromState(state)));
                }
            }
        }
    }
}
