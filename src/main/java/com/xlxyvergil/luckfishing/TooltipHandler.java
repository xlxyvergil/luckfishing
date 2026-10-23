package com.xlxyvergil.luckfishing;

import com.xlxyvergil.luckfishing.config.UpgradeConfig;
import com.xlxyvergil.luckfishing.network.SyncedConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

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

        final ItemStack stack = event.getItemStack();
        final UpgradeConfig config = clientConfig();

        if (config == null || !config.isFishingRod(stack)) {

            return;
        }

        // 只负责「读 + 显示」：竿上记了什么就显示什么，配置算出来的概率就显示什么。
        final ResourceLocation appliedUpgrade = UpgradeConfig.getAppliedUpgrade(stack);

        event.getToolTip().add(Component.translatable(STAGE_KEY, stageName(appliedUpgrade)).withStyle(ChatFormatting.GRAY));
        event.getToolTip().add(Component.translatable(CHANCE_KEY, config.chanceOf(appliedUpgrade)).withStyle(ChatFormatting.GRAY));
    }

    /** 服务端同步来的配置优先，保证 tooltip 与服务端实际判定一致；还没有同步时退回本地配置。 */
    private static UpgradeConfig clientConfig() {

        final UpgradeConfig synced = SyncedConfig.get();

        return synced != null ? synced : LuckFishing.getUpgradeConfig();
    }

    /**
     * 强化以物品 id 存储：有标记就显示该道具的名字（未注册的物品退回显示 id），没有标记显示未强化。
     */
    private static Component stageName(@Nullable ResourceLocation appliedUpgrade) {

        if (appliedUpgrade == null) {

            return Component.translatable(STAGE_NONE_KEY);
        }

        final Item upgradeItem = ForgeRegistries.ITEMS.getValue(appliedUpgrade);

        return upgradeItem == null
                ? Component.literal(appliedUpgrade.toString())
                : Component.translatable(upgradeItem.getDescriptionId());
    }
}
