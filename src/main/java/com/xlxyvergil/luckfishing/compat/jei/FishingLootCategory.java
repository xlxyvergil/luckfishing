package com.xlxyvergil.luckfishing.compat.jei;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;

/** 钓鱼池分类，每个实体一页，展示名称、解锁它的强化等级与道具、该等级的出怪概率和权重。 */
@OnlyIn(Dist.CLIENT)
public class FishingLootCategory implements IRecipeCategory<FishingLootRecipe> {

    static final int WIDTH = 176;
    static final int HEIGHT = 128;

    /** 模型最长边缩放到的像素边长。 */
    private static final int MOB_SIZE = 64;

    private final RecipeType<FishingLootRecipe> type;
    private final IDrawable icon;
    private final Component title;

    public FishingLootCategory(IGuiHelper guiHelper, RecipeType<FishingLootRecipe> type, ItemStack icon, Component title) {
        this.type = type;
        this.icon = guiHelper.createDrawableItemStack(icon);
        this.title = title;
    }

    @Override
    public RecipeType<FishingLootRecipe> getRecipeType() {
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
    public void setRecipe(IRecipeLayoutBuilder builder, FishingLootRecipe recipe, IFocusGroup focuses) {
        // 全部在 draw() 里渲染，没有槽位。
    }

    @Override
    public void draw(
        FishingLootRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY
    ) {
        final Font font = Minecraft.getInstance().font;
        final LivingEntity entity = recipe.getEntity();

        final Component name = entity == null
            ? Component.literal(recipe.getEntityId().toString())
            : entity.getDisplayName();

        guiGraphics.drawString(font, name.copy().withStyle(ChatFormatting.BOLD), 4, 4, 0xFF_FFFF);

        int y = 16;
        y = drawStat(font, guiGraphics, Component.translatable("jei.luckfishing.tier"), Component.literal(String.valueOf(recipe.getTier() + 1)), y);
        y = drawStat(font, guiGraphics, Component.translatable("jei.luckfishing.upgrade_item"), upgradeName(recipe), y);
        y = drawStat(font, guiGraphics, Component.translatable("jei.luckfishing.tier_chance"), Component.literal(percent(recipe.getChance())), y);
        drawStat(font, guiGraphics, Component.translatable("jei.luckfishing.weight"), Component.literal(String.format(Locale.ROOT, "%.2f", recipe.getWeight())), y);

        if (entity != null) {
            renderMob(guiGraphics, recipe, entity);
        }
    }

    private static int drawStat(Font font, GuiGraphics guiGraphics, Component label, Component value, int y) {
        guiGraphics.drawString(font, label, 4, y, 0xFF_A0A0A0);
        guiGraphics.drawString(font, value, 4, y + 10, 0xFF_FFFF);
        return y + 22;
    }

    private static Component upgradeName(FishingLootRecipe recipe) {
        final Item item = ForgeRegistries.ITEMS.getValue(recipe.getUpgradeItemId());
        return item == null
            ? Component.literal(recipe.getUpgradeItemId().toString())
            : new ItemStack(item).getHoverName();
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value * 100.0D);
    }

    private void renderMob(GuiGraphics guiGraphics, FishingLootRecipe recipe, LivingEntity entity) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        entity.tickCount = mc.player.tickCount;

        // 尺寸取自缓存（模型框与碰撞箱逐轴取大）。渲染像素约等于「方块尺寸 × scale」，
        // 把最长边缩到 MOB_SIZE 即可，模型远大于碰撞箱的实体也能统一填满目标框。
        final EntitySizeCache.Size size = EntitySizeCache.getOrMeasure(recipe.getEntityType());
        final float entitySize = Math.max(Math.max(size.getWidth(), size.getHeight()), size.getLength());
        final int scale = Math.max(1, (int) (MOB_SIZE / Math.max(entitySize, 0.05F)));

        final PoseStack stack = guiGraphics.pose();
        stack.pushPose();
        // 右半边水平居中，垂直放在下方 1/3 处。
        stack.translate(WIDTH / 2.0F + WIDTH / 4.0F, HEIGHT - HEIGHT / 3.0F, 0.0F);
        renderEntity(guiGraphics, entity, scale);
        stack.popPose();
    }

    private void renderEntity(GuiGraphics guiGraphics, LivingEntity entity, int scale) {
        final PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.mulPoseMatrix(guiGraphics.pose().last().pose());
        InventoryScreen.renderEntityInInventoryFollowsMouse(guiGraphics, 0, 0, scale, 75, -20, entity);
        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
    }
}