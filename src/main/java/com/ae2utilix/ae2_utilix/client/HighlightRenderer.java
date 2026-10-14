package com.ae2utilix.ae2_utilix.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Draws a short lived outline for blocks linked by the coupling staff. */
@SideOnly(Side.CLIENT)
public final class HighlightRenderer {

    private static final List<HighlightEntry> HIGHLIGHTS = new ArrayList<>();
    private static final long DURATION_MS = 4000;

    private HighlightRenderer() {
    }

    public static void addHighlight(BlockPos pos, int dimension) {
        for (Iterator<HighlightEntry> it = HIGHLIGHTS.iterator(); it.hasNext(); ) {
            HighlightEntry entry = it.next();
            if (entry.pos.equals(pos) && entry.dimension == dimension) {
                it.remove();
            }
        }
        HIGHLIGHTS.add(new HighlightEntry(pos, dimension, System.currentTimeMillis()));
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        if (HIGHLIGHTS.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null) {
            return;
        }

        long now = System.currentTimeMillis();
        float partialTicks = event.getPartialTicks();

        double dX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double dY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double dZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.disableTexture2D();
        GlStateManager.depthMask(false);

        for (Iterator<HighlightEntry> it = HIGHLIGHTS.iterator(); it.hasNext(); ) {
            HighlightEntry entry = it.next();
            long elapsed = now - entry.startTime;
            if (elapsed > DURATION_MS) {
                it.remove();
                continue;
            }

            if (entry.dimension != player.dimension) {
                continue;
            }

            float alpha = 1.0f - (float) elapsed / DURATION_MS;
            alpha = alpha * 0.6f;

            GlStateManager.glLineWidth(4.0F);
            GL11.glDisable(GL11.GL_DEPTH_TEST);

            AxisAlignedBB box = new AxisAlignedBB(entry.pos).offset(-dX, -dY, -dZ).grow(0.002D);
            RenderGlobal.drawSelectionBoundingBox(box, 0.0f, 0.8f, 1.0f, alpha);

            GL11.glEnable(GL11.GL_DEPTH_TEST);

            GlStateManager.glLineWidth(2.0F);
            box = new AxisAlignedBB(entry.pos).offset(-dX, -dY, -dZ).grow(0.002D);
            RenderGlobal.drawSelectionBoundingBox(box, 0.0f, 0.8f, 1.0f, alpha * 0.5f);
        }

        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    private static final class HighlightEntry {
        private final BlockPos pos;
        private final int dimension;
        private final long startTime;

        private HighlightEntry(BlockPos pos, int dimension, long startTime) {
            this.pos = pos;
            this.dimension = dimension;
            this.startTime = startTime;
        }
    }
}