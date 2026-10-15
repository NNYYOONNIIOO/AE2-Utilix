package com.ae2utilix.ae2_utilix.integration.jei;

import com.ae2utilix.ae2_utilix.AE2Utilix;
import com.ae2utilix.ae2_utilix.Reference;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IDrawableAnimated;
import mezz.jei.api.gui.IDrawableStatic;
import mezz.jei.api.gui.IGuiFluidStackGroup;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.config.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** JEI category for the crystal growth chamber's nine input and six output slots. */
public class CrystalGrowthRecipeCategory implements IRecipeCategory<CrystalGrowthRecipeWrapper> {

    public static final String UID = AE2Utilix.MODID + ".crystal_growth";

    private static final int INPUT_GRID_X = 0;
    private static final int INPUT_GRID_Y = 0;
    private static final int INPUT_GRID_W = 54;
    private static final int INPUT_GRID_H = 54;

    private static final int OUTPUT_GRID_X = 84;
    private static final int OUTPUT_GRID_Y = 0;
    private static final int OUTPUT_GRID_W = 36;
    private static final int OUTPUT_GRID_H = 54;

    private static final int FLUID_INPUT_SLOT_X = 0;
    private static final int FLUID_INPUT_SLOT_Y = 56;
    private static final int FLUID_OUTPUT_SLOT_X = 84;
    private static final int FLUID_OUTPUT_SLOT_Y = 56;

    private static final int FLUID_W = 18;
    private static final int FLUID_H = 18;

    private static final int ARROW_X = 58;
    private static final int ARROW_Y = 18;
    private static final int ARROW_W = 24;
    private static final int ARROW_H = 17;

    private static final int WIDTH = 122;
    private static final int HEIGHT = 76;
    private static final int SLOT_SIZE = 18;

    private final IGuiHelper guiHelper;
    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable inputGridDrawable;
    private final IDrawable outputGridDrawable;
    private final IDrawable slotDrawable;
    private final IDrawableStatic arrowStatic;
    private final IDrawableStatic arrowEmpty;
    private final BlockTextureFluidRenderer fluidRenderer;
    private final String localizedName;

    private CrystalGrowthRecipeWrapper currentWrapper;
    private IDrawableAnimated currentArrow;

    public CrystalGrowthRecipeCategory(IGuiHelper guiHelper) {
        this.guiHelper = guiHelper;
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableIngredient(
                new ItemStack(AE2Utilix.BLOCK_CRYSTAL_GROWTH_CHAMBER));

        ResourceLocation inputGridTex = new ResourceLocation(AE2Utilix.MODID, "textures/guis/item_3_3_9.png");
        this.inputGridDrawable = guiHelper.drawableBuilder(inputGridTex, 0, 0, INPUT_GRID_W, INPUT_GRID_H)
                .setTextureSize(INPUT_GRID_W, INPUT_GRID_H).build();

        ResourceLocation outputGridTex = new ResourceLocation(AE2Utilix.MODID, "textures/guis/item_3_2_6.png");
        this.outputGridDrawable = guiHelper.drawableBuilder(outputGridTex, 0, 0, OUTPUT_GRID_W, OUTPUT_GRID_H)
                .setTextureSize(OUTPUT_GRID_W, OUTPUT_GRID_H).build();

        this.slotDrawable = guiHelper.getSlotDrawable();
        this.arrowStatic = guiHelper.drawableBuilder(Constants.RECIPE_GUI_VANILLA, 82, 128, ARROW_W, ARROW_H).build();
        this.arrowEmpty = guiHelper.drawableBuilder(Constants.RECIPE_GUI_VANILLA, 24, 132, ARROW_W, ARROW_H).build();
        this.currentArrow = guiHelper.createAnimatedDrawable(this.arrowStatic, 100,
                IDrawableAnimated.StartDirection.LEFT, false);

        this.fluidRenderer = new BlockTextureFluidRenderer(FLUID_W, FLUID_H);
        this.localizedName = I18n.format("ae2_utilix.jei.crystal_growth.title");
    }

    @Override
    public String getUid() {
        return UID;
    }

    @Override
    public String getTitle() {
        return this.localizedName;
    }

    @Override
    public String getModName() {
        return Reference.MOD_NAME;
    }

    @Override
    public IDrawable getBackground() {
        return this.background;
    }

    @Override
    public IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public void drawExtras(Minecraft minecraft) {
        this.inputGridDrawable.draw(minecraft, INPUT_GRID_X, INPUT_GRID_Y);
        this.outputGridDrawable.draw(minecraft, OUTPUT_GRID_X, OUTPUT_GRID_Y);
        this.arrowEmpty.draw(minecraft, ARROW_X, ARROW_Y);
        this.currentArrow.draw(minecraft, ARROW_X, ARROW_Y);
    }

    @Override
    public List<String> getTooltipStrings(int mouseX, int mouseY) {
        if (mouseX >= ARROW_X && mouseX < ARROW_X + ARROW_W
                && mouseY >= ARROW_Y && mouseY < ARROW_Y + ARROW_H) {
            CrystalGrowthRecipeWrapper wrapper = this.currentWrapper;
            if (wrapper != null) {
                List<String> tooltip = new ArrayList<>();
                tooltip.add(wrapper.getProcessingTime() + " tick");
                tooltip.add(String.format("%.0f AE", wrapper.getEnergyCost()));
                return tooltip;
            }
        }
        return Collections.emptyList();
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, CrystalGrowthRecipeWrapper recipeWrapper,
                          IIngredients ingredients) {
        this.currentWrapper = recipeWrapper;
        this.currentArrow = this.guiHelper.createAnimatedDrawable(this.arrowStatic,
                Math.max(1, recipeWrapper.getProcessingTime()), IDrawableAnimated.StartDirection.LEFT, false);

        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        IGuiFluidStackGroup fluidStacks = recipeLayout.getFluidStacks();

        int slotIndex = 0;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                itemStacks.init(slotIndex, true,
                        INPUT_GRID_X + col * SLOT_SIZE, INPUT_GRID_Y + row * SLOT_SIZE);
                itemStacks.setBackground(slotIndex, this.slotDrawable);
                slotIndex++;
            }
        }

        fluidStacks.init(0, true, this.fluidRenderer, FLUID_INPUT_SLOT_X, FLUID_INPUT_SLOT_Y,
                FLUID_W, FLUID_H, 1, 1);
        fluidStacks.setBackground(0, this.slotDrawable);

        fluidStacks.init(1, false, this.fluidRenderer, FLUID_OUTPUT_SLOT_X, FLUID_OUTPUT_SLOT_Y,
                FLUID_W, FLUID_H, 1, 1);
        fluidStacks.setBackground(1, this.slotDrawable);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 2; col++) {
                itemStacks.init(slotIndex, false,
                        OUTPUT_GRID_X + col * SLOT_SIZE, OUTPUT_GRID_Y + row * SLOT_SIZE);
                itemStacks.setBackground(slotIndex, this.slotDrawable);
                slotIndex++;
            }
        }

        itemStacks.set(ingredients);
        fluidStacks.set(ingredients);
    }
}