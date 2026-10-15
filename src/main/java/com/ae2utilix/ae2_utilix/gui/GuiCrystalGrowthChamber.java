package com.ae2utilix.ae2_utilix.gui;

import com.ae2utilix.ae2_utilix.AE2Utilix;
import com.ae2utilix.ae2_utilix.block.TileCrystalGrowthChamber;
import com.ae2utilix.ae2_utilix.network.PacketToggleCGCButton;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GuiCrystalGrowthChamber extends GuiContainer {

    private static final ResourceLocation TEXTURE_backpack_gui_background =
            new ResourceLocation(AE2Utilix.MODID, "textures/guis/backpack_gui_background.png");
    private static final ResourceLocation TEXTURE_button_off =
            new ResourceLocation(AE2Utilix.MODID, "textures/guis/18_18_button_off.png");
    private static final ResourceLocation TEXTURE_button_on =
            new ResourceLocation(AE2Utilix.MODID, "textures/guis/18_18_button_on.png");
    private static final ResourceLocation TEXTURE_button_selected =
            new ResourceLocation(AE2Utilix.MODID, "textures/guis/18_18_button_selected.png");
    private static final ResourceLocation TEXTURE_eject_off =
            new ResourceLocation(AE2Utilix.MODID, "textures/guis/eject_off.png");
    private static final ResourceLocation TEXTURE_eject_on =
            new ResourceLocation(AE2Utilix.MODID, "textures/guis/eject_on.png");
    private static final ResourceLocation TEXTURE_interface_eject_off =
            new ResourceLocation(AE2Utilix.MODID, "textures/guis/interface_eject_off.png");
    private static final ResourceLocation TEXTURE_interface_eject_on =
            new ResourceLocation(AE2Utilix.MODID, "textures/guis/interface_eject_on.png");
    private static final ResourceLocation TEXTURE_reservoir =
            new ResourceLocation(AE2Utilix.MODID, "textures/guis/reservoir.png");
    private static final ResourceLocation TEXTURE_scale_mark =
            new ResourceLocation(AE2Utilix.MODID, "textures/guis/scale_mark.png");
    private static final ResourceLocation TEXTURE_processing_indicator_bar =
            new ResourceLocation(AE2Utilix.MODID, "textures/guis/processing_indicator_bar.png");
    private static final ResourceLocation TEXTURE_processed_indicator_bar =
            new ResourceLocation(AE2Utilix.MODID, "textures/guis/processed_indicator_bar.png");
    private static final ResourceLocation TEXTURE_interface_configuration_outer_frame =
            new ResourceLocation(AE2Utilix.MODID, "textures/guis/interface_configuration_outer_frame.png");
    private static final ResourceLocation TEXTURE_interface_related_base_plate =
            new ResourceLocation(AE2Utilix.MODID, "textures/guis/interface_related_base_plate.png");
    private static final ResourceLocation TEXTURE_item_3_3_9 =
            new ResourceLocation(AE2Utilix.MODID, "textures/guis/item_3_3_9.png");
    private static final ResourceLocation TEXTURE_item_3_2_6 =
            new ResourceLocation(AE2Utilix.MODID, "textures/guis/item_3_2_6.png");
    private static final ResourceLocation TEXTURE_upgrade_slot =
            new ResourceLocation(AE2Utilix.MODID, "textures/guis/upgrade_slot.png");

    private final TileCrystalGrowthChamber cgc;

    public static final int GUI_X = 176;
    public static final int FULL_GUI_X = 210;
    public static final int GUI_Y = 166;

    public static final int reservoir_W = 18;
    public static final int reservoir_H = 52;
    public static final int reservoir_INPUT_X = 7;
    public static final int reservoir_INPUT_Y = 17;
    public static final int reservoir_OUTPUT_X = 147;
    public static final int reservoir_OUTPUT_Y = 17;

    public static final int crystal_growth_chamber_processing_identifier_X = 86;
    public static final int crystal_growth_chamber_processing_identifier_Y = 38;
    public static final int crystal_growth_chamber_processing_identifier_W = 17;
    public static final int crystal_growth_chamber_processing_identifier_H = 10;

    public static final int button_off_X = 186;
    public static final int button_off_Y = 111;
    public static final int button_W = 18;
    public static final int button_H = 18;
    public static final int eject_off_X = 188;
    public static final int eject_off_Y = 111;

    public static final int interface_configuration_outer_frame_X = 182;
    public static final int interface_configuration_outer_frame_Y = 131;
    public static final int interface_configuration_outer_frame_W = 26;
    public static final int interface_configuration_outer_frame_H = 26;
    public static final int interface_related_base_plate_X = 179;
    public static final int interface_related_base_plate_Y = 105;
    public static final int interface_related_base_plate_W = 32;
    public static final int interface_related_base_plate_H = 61;

    public static final int[] interface_eject_off_XS = {191, 183, 191, 199, 191, 199};
    public static final int[] interface_eject_off_YS = {132, 140, 140, 140, 148, 148};
    public static final int interface_eject_off_W = 8;
    public static final int interface_eject_off_H = 8;

    public static final int item_3_3_9_X = 29;
    public static final int item_3_3_9_Y = 16;
    public static final int item_3_3_9_W = 54;
    public static final int item_3_3_9_H = 54;
    public static final int item_3_2_6_X = 106;
    public static final int item_3_2_6_Y = 16;
    public static final int item_3_2_6_W = 36;
    public static final int item_3_2_6_H = 54;
    public static final int upgrade_slot_X = 179;
    public static final int upgrade_slot_Y = 0;
    public static final int upgrade_slot_W = 32;
    public static final int upgrade_slot_H = 104;

    public static final int FLUID_IN_RESERVOIR_X_OFF = 1;
    public static final int FLUID_IN_RESERVOIR_Y_OFF = 1;
    public static final int FLUID_IN_RESERVOIR_W = 16;
    public static final int FLUID_IN_RESERVOIR_H = 50;

    public static final int PLAYER_INV_X = 8;
    public static final int PLAYER_INV_Y = 84;
    public static final int PLAYER_HOTBAR_Y = 142;

    private static final int BTN_ID_POWER = 1;
    private static final int BTN_ID_EJECT = 2;
    private static final int BTN_ID_INTERFACE_EJECT_BASE = 10;

    public GuiCrystalGrowthChamber(InventoryPlayer ip, TileCrystalGrowthChamber cgc) {
        super(new ContainerCrystalGrowthChamber(ip, cgc));
        this.cgc = cgc;
        xSize = FULL_GUI_X;
        ySize = GUI_Y;
    }

    @Override
    public void initGui() {
        super.initGui();
        guiLeft = (width - GUI_X) / 2;
        GuiButton btnPower = new GuiButton(BTN_ID_POWER,
                guiLeft + button_off_X, guiTop + button_off_Y, button_W, button_H, "");
        btnPower.visible = false;
        addButton(btnPower);
        GuiButton btnEject = new GuiButton(BTN_ID_EJECT,
                guiLeft + eject_off_X, guiTop + eject_off_Y, button_W, button_H, "");
        btnEject.visible = false;
        addButton(btnEject);
        for (int i = 0; i < 6; i++) {
            GuiButton btnFace = new GuiButton(BTN_ID_INTERFACE_EJECT_BASE + i,
                    guiLeft + interface_eject_off_XS[i], guiTop + interface_eject_off_YS[i], interface_eject_off_W, interface_eject_off_H, "");
            btnFace.visible = false;
            addButton(btnFace);
        }
    }

    @Override
    protected void actionPerformed(GuiButton btn) {
        if (btn == null) return;
        if (btn.id == BTN_ID_POWER) {
            ae2utilix$send(
                    new PacketToggleCGCButton(cgc.getPos(), PacketToggleCGCButton.BUTTON_POWER));
        } else if (btn.id == BTN_ID_EJECT) {
            ae2utilix$send(
                    new PacketToggleCGCButton(cgc.getPos(), PacketToggleCGCButton.BUTTON_EJECT));
        } else if (btn.id >= BTN_ID_INTERFACE_EJECT_BASE && btn.id < BTN_ID_INTERFACE_EJECT_BASE + 6) {
            ae2utilix$send(
                    new PacketToggleCGCButton(cgc.getPos(),
                            PacketToggleCGCButton.BUTTON_FACE_EJECT_START + (btn.id - BTN_ID_INTERFACE_EJECT_BASE)));
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        mc.renderEngine.bindTexture(TEXTURE_backpack_gui_background);
        drawModalRectWithCustomSizedTexture(guiLeft, guiTop, 0, 0, GUI_X, ySize, GUI_X, ySize);

        mc.renderEngine.bindTexture(TEXTURE_item_3_3_9);
        drawModalRectWithCustomSizedTexture(guiLeft + item_3_3_9_X, guiTop + item_3_3_9_Y,
                0, 0, item_3_3_9_W, item_3_3_9_H, item_3_3_9_W, item_3_3_9_H);

        mc.renderEngine.bindTexture(TEXTURE_item_3_2_6);
        drawModalRectWithCustomSizedTexture(guiLeft + item_3_2_6_X, guiTop + item_3_2_6_Y,
                0, 0, item_3_2_6_W, item_3_2_6_H, item_3_2_6_W, item_3_2_6_H);

        mc.renderEngine.bindTexture(TEXTURE_upgrade_slot);
        drawModalRectWithCustomSizedTexture(guiLeft + upgrade_slot_X, guiTop + upgrade_slot_Y,
                0, 0, upgrade_slot_W, upgrade_slot_H, upgrade_slot_W, upgrade_slot_H);

        mc.renderEngine.bindTexture(TEXTURE_interface_related_base_plate);
        drawModalRectWithCustomSizedTexture(guiLeft + interface_related_base_plate_X, guiTop + interface_related_base_plate_Y,
                0, 0, interface_related_base_plate_W, interface_related_base_plate_H,
                interface_related_base_plate_W, interface_related_base_plate_H);

        mc.renderEngine.bindTexture(TEXTURE_interface_configuration_outer_frame);
        drawModalRectWithCustomSizedTexture(guiLeft + interface_configuration_outer_frame_X, guiTop + interface_configuration_outer_frame_Y,
                0, 0, interface_configuration_outer_frame_W, interface_configuration_outer_frame_H,
                interface_configuration_outer_frame_W, interface_configuration_outer_frame_H);

        drawReservoir(guiLeft + reservoir_INPUT_X, guiTop + reservoir_INPUT_Y, cgc.getInputFluid());
        drawReservoir(guiLeft + reservoir_OUTPUT_X, guiTop + reservoir_OUTPUT_Y, cgc.getOutputFluid());

        mc.renderEngine.bindTexture(TEXTURE_processing_indicator_bar);
        drawModalRectWithCustomSizedTexture(guiLeft + crystal_growth_chamber_processing_identifier_X, guiTop + crystal_growth_chamber_processing_identifier_Y,
                0, 0, crystal_growth_chamber_processing_identifier_W, crystal_growth_chamber_processing_identifier_H,
                crystal_growth_chamber_processing_identifier_W, crystal_growth_chamber_processing_identifier_H);

        if (cgc.getMaxProgress() > 0 && cgc.getProgress() > 0) {
            int barW = crystal_growth_chamber_processing_identifier_W;
            int filledW = (int) ((float) cgc.getProgress() / cgc.getMaxProgress() * barW);
            filledW = Math.max(0, Math.min(filledW, barW));
            mc.renderEngine.bindTexture(TEXTURE_processed_indicator_bar);
            drawModalRectWithCustomSizedTexture(guiLeft + crystal_growth_chamber_processing_identifier_X, guiTop + crystal_growth_chamber_processing_identifier_Y,
                    0, 0, filledW, crystal_growth_chamber_processing_identifier_H,
                    barW, crystal_growth_chamber_processing_identifier_H);
        }

        drawPowerButton(guiLeft + button_off_X, guiTop + button_off_Y, mouseX, mouseY);
        drawEjectButton(guiLeft + eject_off_X, guiTop + eject_off_Y);

        for (int i = 0; i < 6; i++) {
            drawFaceEjectButton(guiLeft + interface_eject_off_XS[i], guiTop + interface_eject_off_YS[i], i);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
    }

    private void drawReservoir(int x, int y, FluidStack fluid) {
        mc.renderEngine.bindTexture(TEXTURE_reservoir);
        drawModalRectWithCustomSizedTexture(x, y, 0, 0, reservoir_W, reservoir_H, reservoir_W, reservoir_H);

        if (fluid != null && fluid.amount > 0) {
            int fillH = (int) ((float) fluid.amount / cgc.getMaxFluidMB() * FLUID_IN_RESERVOIR_H);
            fillH = Math.max(0, Math.min(fillH, FLUID_IN_RESERVOIR_H));

            int ry = y + reservoir_H - FLUID_IN_RESERVOIR_Y_OFF - fillH;
            int rx = x + FLUID_IN_RESERVOIR_X_OFF;

            ResourceLocation still = fluid.getFluid().getStill(fluid);
            if (still != null) {
                GlStateManager.disableLighting();
                GlStateManager.enableBlend();
                int color = fluid.getFluid().getColor(fluid);
                float r = ((color >> 16) & 0xFF) / 255.0F;
                float g = ((color >> 8) & 0xFF) / 255.0F;
                float b = (color & 0xFF) / 255.0F;
                GlStateManager.color(r, g, b, 1.0F);
                mc.renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                TextureMap textureMap = (TextureMap) mc.renderEngine.getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                TextureAtlasSprite sprite = textureMap.getTextureExtry(still.toString());
                if (sprite == null) {
                    sprite = mc.getTextureMapBlocks().getAtlasSprite(still.toString());
                }
                drawTiledFluid(rx, ry, FLUID_IN_RESERVOIR_W, fillH, sprite);
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                GlStateManager.disableBlend();
            }
        }

        mc.renderEngine.bindTexture(TEXTURE_scale_mark);
        drawModalRectWithCustomSizedTexture(x, y, 0, 0, 16, 52, 16, 52);
    }

    private void drawTiledFluid(int x, int y, int width, int height, TextureAtlasSprite sprite) {
        float uMin = sprite.getMinU();
        float uMax = sprite.getMaxU();
        float vMin = sprite.getMinV();
        float vMax = sprite.getMaxV();
        float uDif = uMax - uMin;
        float vDif = vMax - vMin;

        Tessellator t = Tessellator.getInstance();
        BufferBuilder buf = t.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);

        int yTileCount = height / 16;
        int yRemainder = height - yTileCount * 16;
        int xTileCount = width / 16;
        int xRemainder = width - xTileCount * 16;

        for (int xTile = 0; xTile <= xTileCount; xTile++) {
            int tileW = (xTile == xTileCount) ? xRemainder : 16;
            if (tileW == 0) break;
            int xOff = x + xTile * 16;
            float uLocalMin = uMin;
            float uLocalMax = uMin + uDif * tileW / 16.0F;

            for (int yTile = 0; yTile <= yTileCount; yTile++) {
                int tileH = (yTile == yTileCount) ? yRemainder : 16;
                if (tileH == 0) break;
                int yOff = y + yTile * 16;
                float vLocalMin = vMin;
                float vLocalMax = vMin + vDif * tileH / 16.0F;

                buf.pos(xOff, yOff + tileH, zLevel).tex(uLocalMin, vLocalMax).endVertex();
                buf.pos(xOff + tileW, yOff + tileH, zLevel).tex(uLocalMax, vLocalMax).endVertex();
                buf.pos(xOff + tileW, yOff, zLevel).tex(uLocalMax, vLocalMin).endVertex();
                buf.pos(xOff, yOff, zLevel).tex(uLocalMin, vLocalMin).endVertex();
            }
        }

        t.draw();
    }

    private void drawPowerButton(int x, int y, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + button_W &&
                mouseY >= y && mouseY < y + button_H;
        ResourceLocation tex;
        if (cgc.isEjecting()) {
            tex = hover ? TEXTURE_button_selected : TEXTURE_button_on;
        } else {
            tex = hover ? TEXTURE_button_selected : TEXTURE_button_off;
        }
        mc.renderEngine.bindTexture(tex);
        drawModalRectWithCustomSizedTexture(x, y, 0, 0, button_W, button_H, 18, 18);
    }

    private void drawEjectButton(int x, int y) {
        mc.renderEngine.bindTexture(cgc.isEjecting() ? TEXTURE_eject_on : TEXTURE_eject_off);
        drawModalRectWithCustomSizedTexture(x, y, 0, 0, 16, 16, 16, 16);
    }

    private void drawFaceEjectButton(int x, int y, int faceIdx) {
        mc.renderEngine.bindTexture(cgc.isFaceEjecting(faceIdx) ? TEXTURE_interface_eject_on : TEXTURE_interface_eject_off);
        drawModalRectWithCustomSizedTexture(x, y, 0, 0, interface_eject_off_W, interface_eject_off_H, interface_eject_off_W, interface_eject_off_H);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {

        int rx = mouseX - guiLeft;
        int ry = mouseY - guiTop;

        if (isInBounds(rx, ry, button_off_X, button_off_Y, button_W, button_H)) {
            mc.player.playSound(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 0.5F, 1.0F);
            actionPerformed(findBtn(BTN_ID_EJECT));
            return;
        }
        if (isInBounds(rx, ry, eject_off_X, eject_off_Y, button_W, button_H)) {
            mc.player.playSound(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 0.5F, 1.0F);
            actionPerformed(findBtn(BTN_ID_EJECT));
            return;
        }
        for (int i = 0; i < 6; i++) {
            if (isInBounds(rx, ry, interface_eject_off_XS[i], interface_eject_off_YS[i], interface_eject_off_W, interface_eject_off_H)) {
                mc.player.playSound(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 0.5F, 1.0F);
                actionPerformed(findBtn(BTN_ID_INTERFACE_EJECT_BASE + i));
                return;
            }
        }

        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) && mouseButton == 0) {
            if (isInBounds(rx, ry, reservoir_INPUT_X, reservoir_INPUT_Y, reservoir_W, reservoir_H)) {
                ae2utilix$send(
                        new PacketToggleCGCButton(cgc.getPos(), PacketToggleCGCButton.BUTTON_CLEAR_INPUT_FLUID));
                return;
            }
            if (isInBounds(rx, ry, reservoir_OUTPUT_X, reservoir_OUTPUT_Y, reservoir_W, reservoir_H)) {
                ae2utilix$send(
                        new PacketToggleCGCButton(cgc.getPos(), PacketToggleCGCButton.BUTTON_CLEAR_OUTPUT_FLUID));
                return;
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private static void ae2utilix$send(PacketToggleCGCButton packet) {
        com.ae2utilix.ae2_utilix.network.NetworkHandler.CHANNEL.sendToServer(packet);
    }

    private boolean isInBounds(int rx, int ry, int bx, int by, int bw, int bh) {
        return rx >= bx && rx < bx + bw && ry >= by && ry < by + bh;
    }

    private GuiButton findBtn(int id) {
        for (GuiButton b : buttonList) {
            if (b.id == id) return b;
        }
        return null;
    }

    /**
     * Draws the vanilla slot tooltips and this screen's own tooltips.
     *
     * <p>Vanilla calls {@link #renderHoveredToolTip(int, int)} from inside
     * {@code drawScreen}, which is too early for tooltips that have to sit above the
     * slot item highlights. The Forge build solved this with a post-render event; here
     * the whole tooltip pass simply runs after {@code super.drawScreen} returns.</p>
     */
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawTooltips(mouseX, mouseY);
    }

    /**
     * Suppressed here: the item tooltip is drawn from {@link #drawScreen} instead, after
     * vanilla finished its item highlight pass, so it is never painted over.
     */
    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
    }

    private static final String[] FACE_KEYS = {
            "ae2_utilix.gui.face.up",
            "ae2_utilix.gui.face.left",
            "ae2_utilix.gui.face.front",
            "ae2_utilix.gui.face.right",
            "ae2_utilix.gui.face.down",
            "ae2_utilix.gui.face.back"
    };

    private void drawTooltips(int mouseX, int mouseY) {
        int rx = mouseX - guiLeft;
        int ry = mouseY - guiTop;

        // Slot item tooltips come from vanilla, drawn now that the screen is complete.
        super.renderHoveredToolTip(mouseX, mouseY);

        if (isInBounds(rx, ry, button_off_X, button_off_Y, button_W, button_H)) {
            List<String> lines = new ArrayList<>();
            lines.add(cgc.isEjecting() ? I18n.format("ae2_utilix.gui.eject.on") : I18n.format("ae2_utilix.gui.eject.off"));
            drawHoveringText(lines, mouseX, mouseY);
            return;
        }

        for (int i = 0; i < 6; i++) {
            if (isInBounds(rx, ry, interface_eject_off_XS[i], interface_eject_off_YS[i], interface_eject_off_W, interface_eject_off_H)) {
                List<String> lines = new ArrayList<>();
                lines.add(I18n.format(FACE_KEYS[i]));
                drawHoveringText(lines, mouseX, mouseY);
                return;
            }
        }

        if (isInBounds(rx, ry, reservoir_INPUT_X, reservoir_INPUT_Y, reservoir_W, reservoir_H)) {
            List<String> lines = new ArrayList<>();
            FluidStack fluid = cgc.getInputFluid();
            if (fluid != null && fluid.amount > 0) {
                lines.add(fluid.getLocalizedName());
                lines.add(fluid.amount + " / " + cgc.getMaxFluidMB() + " mB");
                lines.add("\u00a77" + I18n.format("ae2_utilix.gui.shift_left_click_clear"));
            } else {
                lines.add(I18n.format("ae2_utilix.gui.empty"));
            }
            drawHoveringText(lines, mouseX, mouseY);
            return;
        }

        if (isInBounds(rx, ry, reservoir_OUTPUT_X, reservoir_OUTPUT_Y, reservoir_W, reservoir_H)) {
            List<String> lines = new ArrayList<>();
            FluidStack fluid = cgc.getOutputFluid();
            if (fluid != null && fluid.amount > 0) {
                lines.add(fluid.getLocalizedName());
                lines.add(fluid.amount + " / " + cgc.getMaxFluidMB() + " mB");
                lines.add("\u00a77" + I18n.format("ae2_utilix.gui.shift_left_click_clear"));
            } else {
                lines.add(I18n.format("ae2_utilix.gui.empty"));
            }
            drawHoveringText(lines, mouseX, mouseY);
            return;
        }
    }
}
