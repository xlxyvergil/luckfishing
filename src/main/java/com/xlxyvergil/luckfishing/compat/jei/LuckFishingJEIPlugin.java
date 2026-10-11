package com.xlxyvergil.luckfishing.compat.jei;

import com.xlxyvergil.luckfishing.Constants;
import com.xlxyvergil.luckfishing.LuckFishing;
import com.xlxyvergil.luckfishing.config.UpgradeConfig;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe;
import mezz.jei.api.recipe.vanilla.IVanillaRecipeFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** 把钓鱼池以鱼竿为入口暴露给 JEI，并在 JEI 铁砧分类下注册「鱼竿 + 强化道具」配方。 */
@OnlyIn(Dist.CLIENT)
@JeiPlugin
public class LuckFishingJEIPlugin implements IModPlugin {

    private static final ResourceLocation PLUGIN_UID =
        ResourceLocation.tryParse(Constants.MOD_ID + ":jei_plugin");

    private static final RecipeType<FishingLootRecipe> FISHING_LOOT_TYPE =
        new RecipeType<>(
            ResourceLocation.tryParse(Constants.MOD_ID + ":fishing_loot"),
            FishingLootRecipe.class
        );

    private static final RecipeType<UnupgradedCategory.Recipe> UNUPGRADED_TYPE =
        new RecipeType<>(
            ResourceLocation.tryParse(Constants.MOD_ID + ":unupgraded"),
            UnupgradedCategory.Recipe.class
        );

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new FishingLootCategory(
            registration.getJeiHelpers().getGuiHelper(),
            FISHING_LOOT_TYPE,
            new ItemStack(Items.FISHING_ROD),
            Component.translatable("jei.luckfishing.category")
        ));
        registration.addRecipeCategories(new UnupgradedCategory(
            registration.getJeiHelpers().getGuiHelper(),
            UNUPGRADED_TYPE,
            new ItemStack(Items.FISHING_ROD),
            Component.translatable("jei.luckfishing.unupgraded_category")
        ));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        final UpgradeConfig config = LuckFishing.getUpgradeConfig();
        if (config == null) {
            return;
        }

        // JEI 重建配方列表（进入世界、/reload）时都会走到这里，重读文件即可让运行中的改动生效。
        config.reload();

        final List<FishingLootRecipe> recipes = new ArrayList<>();
        for (Map.Entry<String, UpgradeConfig.Upgrade> entry : config.getUpgrades().entrySet()) {
            final ResourceLocation upgradeId = ResourceLocation.tryParse(entry.getKey());
            final UpgradeConfig.Upgrade upgrade = entry.getValue();
            if (upgradeId == null || upgrade == null) {
                continue;
            }

            for (Map.Entry<String, UpgradeConfig.Entry> mob : upgrade.getMobs().entrySet()) {
                final ResourceLocation mobId = ResourceLocation.tryParse(mob.getKey());
                if (mobId == null || !ForgeRegistries.ENTITY_TYPES.containsKey(mobId)) {
                    continue;
                }
                final EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(mobId);
                if (entityType == null || mob.getValue() == null || mob.getValue().getWeight() <= 0.0D) {
                    continue;
                }
                recipes.add(new FishingLootRecipe(
                    mobId,
                    entityType,
                    mob.getValue().getWeight(),
                    upgrade.getTier(),
                    upgrade.getChance(),
                    upgradeId
                ));
            }
        }

        recipes.sort(Comparator.comparingInt(FishingLootRecipe::getTier));

        registration.addRecipes(FISHING_LOOT_TYPE, recipes);
        registration.addRecipes(UNUPGRADED_TYPE, List.of(new UnupgradedCategory.Recipe()));
        registerAnvilRecipes(registration, config);
    }

    /** 注册「鱼竿 + 强化道具」的铁砧配方，与真实铁砧机制一致。 */
    private void registerAnvilRecipes(IRecipeRegistration registration, UpgradeConfig config) {
        final IVanillaRecipeFactory vanilla = registration.getVanillaRecipeFactory();
        final List<IJeiAnvilRecipe> recipes = new ArrayList<>();

        for (String itemId : config.getUpgrades().keySet()) {
            final ResourceLocation upgradeId = ResourceLocation.tryParse(itemId);
            if (upgradeId == null) {
                continue;
            }
            final Item upgradeItem = ForgeRegistries.ITEMS.getValue(upgradeId);
            if (upgradeItem == null) {
                continue;
            }

            final ItemStack left = new ItemStack(Items.FISHING_ROD);
            final ItemStack right = new ItemStack(upgradeItem);
            final ItemStack output = new ItemStack(Items.FISHING_ROD);
            output.getOrCreateTag().putString(Constants.UPGRADE_TAG, upgradeId.toString());

            final ResourceLocation uid = ResourceLocation.tryParse(
                Constants.MOD_ID + ":upgrade/" + upgradeId.getNamespace() + "/" + upgradeId.getPath()
            );
            recipes.add(vanilla.createAnvilRecipe(left, List.of(right), List.of(output), uid));
        }

        registration.addRecipes(RecipeTypes.ANVIL, recipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(Items.FISHING_ROD), FISHING_LOOT_TYPE);
        registration.addRecipeCatalyst(new ItemStack(Items.FISHING_ROD), UNUPGRADED_TYPE);

        final UpgradeConfig config = LuckFishing.getUpgradeConfig();
        if (config == null) {
            return;
        }
        final ResourceLocation tagId = ResourceLocation.tryParse(config.getRodTag());
        if (tagId == null) {
            return;
        }
        final TagKey<Item> key = TagKey.create(ForgeRegistries.ITEMS.getRegistryKey(), tagId);
        for (Item item : ForgeRegistries.ITEMS.tags().getTag(key)) {
            registration.addRecipeCatalyst(new ItemStack(item), FISHING_LOOT_TYPE);
            registration.addRecipeCatalyst(new ItemStack(item), UNUPGRADED_TYPE);
        }
    }
}