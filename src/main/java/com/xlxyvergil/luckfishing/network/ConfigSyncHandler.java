package com.xlxyvergil.luckfishing.network;

import com.xlxyvergil.luckfishing.Constants;
import com.xlxyvergil.luckfishing.LuckFishing;
import com.xlxyvergil.luckfishing.config.UpgradeConfig;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 玩家登录时把服务端的强化配置推给它，客户端据此绘制 tooltip。
 * <p>事件只在服务端触发（客户端进服走 {@code ClientPlayerNetworkEvent.LoggingIn}），因此这里读到的一定是权威配置。
 */
@EventBusSubscriber(modid = Constants.MOD_ID)
public final class ConfigSyncHandler {

    private ConfigSyncHandler() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {

        final UpgradeConfig config = LuckFishing.getUpgradeConfig();

        if (config == null || !(event.getEntity() instanceof ServerPlayer player)) {

            return;
        }

        // 原版客户端（displayTest 允许其进服）与旧版模组没有本通道，跳过即可。
        if (!NetworkHandler.isRemotePresent(player)) {

            return;
        }

        NetworkHandler.sendTo(player, config.toJson());
    }
}
