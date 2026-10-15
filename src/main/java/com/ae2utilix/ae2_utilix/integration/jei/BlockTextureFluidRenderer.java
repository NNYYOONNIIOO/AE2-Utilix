package com.ae2utilix.ae2_utilix.integration.jei;

import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fluids.FluidStack;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a fluid stack as a still texture inside JEI's fluid slot.
 *
 * <p>JEI's own renderer needs a block model bake, which is unnecessary for a slot this
 * small; sampling the fluid's still sprite directly keeps the recipe screen free of
 * missing-texture warnings and works for every fluid, including ones without a bucket.</p>
 */
public class BlockTextureFluidRenderer implements IIngredientRenderer<FluidStack> {

    private final int width;
    private final int height;
    /** Nudges the sprite from JEI's slot origin, so it lines up with the slot frame. */
    private final int offsetX;
    private final int offsetY;

    public BlockTextureFluidRenderer(int width, int height) {
        this(width, height, 0, 0);
    }

    public BlockTextureFluidRenderer(int width, int height, int offsetX, int offsetY) {
        this.width = width;
        this.height = height;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    @Override
    public void render(Minecraft minecraft, int xPosition, int yPosition, FluidStack ingredient) {
        if (ingredient == null || ingredient.getFluid() == null) {
            return;
        }
        xPosition += this.offsetX;
        yPosition += this.offsetY;
        TextureAtlasSprite sprite = minecraft.getTextureMapBlocks()
                .getAtlasSprite(ingredient.getFluid().getStill(ingredient).toString());
        if (sprite == null) {
            return;
        }

        minecraft.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        int color = ingredient.getFluid().getColor(ingredient);
        float alpha = ((color >> 24) & 0xFF) / 255.0F;
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        GlStateManager.color(red, green, blue, alpha == 0.0F ? 1.0F : alpha);
        GlStateManager.enableBlend();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(xPosition, yPosition + this.height, 0.0D)
                .tex(sprite.getMinU(), sprite.getMaxV()).endVertex();
        buffer.pos(xPosition + this.width, yPosition + this.height, 0.0D)
                .tex(sprite.getMaxU(), sprite.getMaxV()).endVertex();
        buffer.pos(xPosition + this.width, yPosition, 0.0D)
                .tex(sprite.getMaxU(), sprite.getMinV()).endVertex();
        buffer.pos(xPosition, yPosition, 0.0D)
                .tex(sprite.getMinU(), sprite.getMinV()).endVertex();
        tessellator.draw();

        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public List<String> getTooltip(Minecraft minecraft, FluidStack ingredient, ITooltipFlag tooltipFlag) {
        List<String> tooltip = new ArrayList<>();
        if (ingredient == null || ingredient.getFluid() == null || ingredient.amount <= 0) {
            return tooltip;
        }
        FontRenderer font = minecraft.fontRenderer;
        tooltip.add(ingredient.getFluid().getLocalizedName(ingredient));
        tooltip.add(TextFormatting.GRAY + String.valueOf(ingredient.amount) + " mB");
        return tooltip;
    }
}