package com.xlxyvergil.luckfishing;

import com.xlxyvergil.luckfishing.config.UpgradeConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = Constants.MOD_ID)
public class AnvilHandler {

    /** 把「鱼竿 + 配置的强化道具」合成为带强化的鱼竿，结果为鱼竿副本，耐久与附魔等数据保留。 */
    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {

        final ItemStack left = event.getLeft();
        final ItemStack right = event.getRight();

        final UpgradeConfig config = LuckFishing.getUpgradeConfig();

        if (config == null) {

            return;
        }

        if (!config.isFishingRod(left)) {

            return;
        }

        final UpgradeConfig.Upgrade upgrade = config.getUpgrade(right);

        if (upgrade == null) {

            return;
        }

        // 低等级材料不能覆盖已有的更高强化。
        if (upgrade.getTier() < config.tierOf(UpgradeConfig.getAppliedUpgrade(left))) {

            return;
        }

        final ResourceLocation upgradeId = ForgeRegistries.ITEMS.getKey(right.getItem());

        if (upgradeId == null) {

            return;
        }

        final ItemStack result = left.copy();
        result.setCount(1);
        result.getOrCreateTag().putString(Constants.UPGRADE_TAG, upgradeId.toString());

        event.setOutput(result);
        // 费用为 0 时 AnvilMenu.mayPickup 不会给出结果，所以至少要 1 级。
        event.setCost(1);
        event.setMaterialCost(1);
    }
}
