package com.xlxyvergil.luckfishing;

import com.xlxyvergil.luckfishing.config.UpgradeConfig;
import com.xlxyvergil.luckfishing.network.SyncedConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** 显示鱼竿当前的强化阶段与出怪概率，仅在客户端绘制。 */
@Mod.EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
public class TooltipHandler {

    /** 阶段行的语言键，参数为强化道具名称。 */
    private static final String STAGE_KEY = "tooltip.luckfishing.stage";

    /** 未强化时替代强化道具名称的语言键。 */
    private static final String STAGE_NONE_KEY = "tooltip.luckfishing.stage.none";

    /** 概率行的语言键，参数为出怪概率。 */
    private static final String CHANCE_KEY = "tooltip.luckfishing.chance";

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {

        final UpgradeConfig config = clientConfig();

        if (config == null || !config.isFishingRod(event.getItemStack())) {

            return;
        }

        final ResourceLocation appliedUpgrade = UpgradeConfig.getAppliedUpgrade(event.getItemStack());

        event.getToolTip().add(Component.translatable(STAGE_KEY, stageName(appliedUpgrade, config)).withStyle(ChatFormatting.GRAY));
        event.getToolTip().add(Component.translatable(CHANCE_KEY, config.chanceOf(appliedUpgrade)).withStyle(ChatFormatting.GRAY));
    }

    /**
     * 优先用服务端同步来的配置，保证 tooltip 与服务端实际判定一致；
     * 还没收到同步时退回本地配置（单机里两者本就是同一份）。
     */
    private static UpgradeConfig clientConfig() {

        final UpgradeConfig synced = SyncedConfig.get();

        return synced != null ? synced : LuckFishing.getUpgradeConfig();
    }

    /**
     * 强化以物品 id 存储，这里用物品自身的语言键取名；未强化时返回未强化提示。
     */
    private static Component stageName(ResourceLocation appliedUpgrade, UpgradeConfig config) {

        if (!config.hasUpgrade(appliedUpgrade)) {

            return Component.translatable(STAGE_NONE_KEY);
        }

        final Item upgradeItem = ForgeRegistries.ITEMS.getValue(appliedUpgrade);

        // 手写配置可能指向未注册的物品，此时只能显示 id。
        return upgradeItem == null
                ? Component.literal(appliedUpgrade.toString())
                : Component.translatable(upgradeItem.getDescriptionId());
    }
}
