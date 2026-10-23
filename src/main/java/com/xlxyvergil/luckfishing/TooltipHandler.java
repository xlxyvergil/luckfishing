package com.xlxyvergil.luckfishing;

import com.xlxyvergil.luckfishing.client.ActiveConfig;
import com.xlxyvergil.luckfishing.config.UpgradeConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.jetbrains.annotations.Nullable;

/** 显示鱼竿当前的强化阶段与出怪概率，仅在客户端绘制。 */
@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
public class TooltipHandler {

    /** 阶段行的语言键，参数为强化道具名称。 */
    private static final String STAGE_KEY = "tooltip.luckfishing.stage";

    /** 未强化时替代强化道具名称的语言键。 */
    private static final String STAGE_NONE_KEY = "tooltip.luckfishing.stage.none";

    /** 概率行的语言键，参数为出怪概率。 */
    private static final String CHANCE_KEY = "tooltip.luckfishing.chance";

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {

        final UpgradeConfig config = ActiveConfig.get();

        if (config == null || !config.isFishingRod(event.getItemStack())) {

            return;
        }

        final ResourceLocation appliedUpgrade = UpgradeConfig.getAppliedUpgrade(event.getItemStack());

        // 只负责「读 + 显示」：竿上记了什么就显示什么，配置算出来的概率就显示什么。
        event.getToolTip().add(Component.translatable(STAGE_KEY, stageName(appliedUpgrade)).withStyle(ChatFormatting.GRAY));
        event.getToolTip().add(Component.translatable(CHANCE_KEY, config.chanceOf(appliedUpgrade)).withStyle(ChatFormatting.GRAY));
    }

    /**
     * 强化以物品 id 存储：有标记就显示该道具的名字（未注册的物品退回显示 id），没有标记显示未强化。
     */
    private static Component stageName(@Nullable ResourceLocation appliedUpgrade) {

        if (appliedUpgrade == null) {

            return Component.translatable(STAGE_NONE_KEY);
        }

        // ITEM 是带默认值（空气）的注册表，未注册的 id 会取到空气而不是 null。
        final Item upgradeItem = BuiltInRegistries.ITEM.get(appliedUpgrade);

        return upgradeItem == null || upgradeItem == Items.AIR
                ? Component.literal(appliedUpgrade.toString())
                : Component.translatable(upgradeItem.getDescriptionId());
    }
}
