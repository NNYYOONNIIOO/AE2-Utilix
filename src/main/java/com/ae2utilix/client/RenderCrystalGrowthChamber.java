package com.ae2utilix.client;

import com.ae2utilix.AE2UtilixConfig;
import com.ae2utilix.block.TileCrystalGrowthChamber;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;

public class RenderCrystalGrowthChamber extends TileEntitySpecialRenderer<TileCrystalGrowthChamber> {

    @Override
    public void render(TileCrystalGrowthChamber te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        if (!AE2UtilixConfig.renderFloatingItem) return;
        ItemStack displayStack = getDisplayStack(te);
        if (displayStack.isEmpty()) return;

        boolean hasProgress = te.getProgress() > 0 && te.getMaxProgress() > 0;
        if (!hasProgress && te.getClientDisplayStack().isEmpty()) return;

        if (AE2UtilixConfig.renderFloatingItemModel) {
            float progress = hasProgress ? (float) te.getProgress() / te.getMaxProgress() : 0.0F;

            GlStateManager.pushMatrix();
            GlStateManager.translate(x + 0.5, y + 0.5 - 0.0125, z + 0.5);

            float scale = 0.35F + progress * 0.2F;
            GlStateManager.scale(scale, scale, scale);

            float rotation = (te.getWorld().getTotalWorldTime() + partialTicks) * 2.0F;
            GlStateManager.rotate(rotation, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(25.0F, 1.0F, 0.0F, 0.0F);

            if (!(displayStack.getItem() instanceof ItemBlock)) {
                GlStateManager.translate(0.0F, 0.15F, 0.0F);
            }

            Minecraft.getMinecraft().getRenderItem().renderItem(displayStack, ItemCameraTransforms.TransformType.GROUND);

            GlStateManager.popMatrix();
        }

        if (!Minecraft.getMinecraft().isGamePaused()) {
            spawnEffects(te, partialTicks);
        }
    }

    private ItemStack getDisplayStack(TileCrystalGrowthChamber te) {
        ItemStack display = te.getClientDisplayStack();
        if (!display.isEmpty()) return display;
        for (int i = 0; i < te.getOutputInv().getSlots(); i++) {
            ItemStack stack = te.getOutputInv().getStackInSlot(i);
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private void spawnEffects(TileCrystalGrowthChamber te, float partialTicks) {
        if (!te.getWorld().isRemote) return;

        BlockPos pos = te.getPos();
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;

        float density = Math.max(0.0F, Math.min(1.0F, AE2UtilixConfig.floatingParticleDensity));
        if (density <= 0.0F || te.getWorld().rand.nextFloat() >= density) return;

        double ox = (te.getWorld().rand.nextDouble() - 0.5) * 0.6;
        double oy = (te.getWorld().rand.nextDouble() - 0.5) * 0.6;
        double oz = (te.getWorld().rand.nextDouble() - 0.5) * 0.6;

        te.getWorld().spawnParticle(EnumParticleTypes.REDSTONE,
                cx + ox, cy + oy, cz + oz,
                0.0, 0.1, 0.2);

        if (te.getWorld().rand.nextFloat() < 0.3F * density) {
            te.getWorld().spawnParticle(EnumParticleTypes.END_ROD,
                    cx + ox * 1.5, cy + oy * 1.5, cz + oz * 1.5,
                    0.0, 0.05, 0.0);
        }
    }

    @Override
    public boolean isGlobalRenderer(TileCrystalGrowthChamber te) {
        return false;
    }
}
