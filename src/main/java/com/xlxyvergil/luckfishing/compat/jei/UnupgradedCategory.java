package com.xlxyvergil.luckfishing.compat.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** 一页静态说明：未强化的鱼竿也能钓到所有配置的实体。单独分类，避免混进实体索引。 */
@OnlyIn(Dist.CLIENT)
public class UnupgradedCategory implements IRecipeCategory<UnupgradedCategory.Recipe> {

    private static final int WIDTH = 176;
    private static final int HEIGHT = 32;

    private final RecipeType<Recipe> type;
    private final IDrawable icon;
    private final Component title;

    public UnupgradedCategory(IGuiHelper guiHelper, RecipeType<Recipe> type, ItemStack icon, Component title) {
        this.type = type;
        this.icon = guiHelper.createDrawableItemStack(icon);
        this.title = title;
    }

    @Override
    public RecipeType<Recipe> getRecipeType() {
        return this.type;
    }

    @Override
    public Component getTitle() {
        return this.title;
    }

    @Override
    public IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Recipe recipe, IFocusGroup focuses) {
        // 静态文本页，没有槽位。
    }

    @Override
    public void draw(
        Recipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY
    ) {
        final Font font = Minecraft.getInstance().font;
        guiGraphics.drawWordWrap(
            font,
            Component.translatable("jei.luckfishing.unupgraded_hint"),
            4,
            4,
            WIDTH - 8,
            0xFF_FFFF
        );
    }

    /** 信息页的占位配方。 */
    public record Recipe() {}
}
