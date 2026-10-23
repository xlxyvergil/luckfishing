package com.xlxyvergil.luckfishing.client;

import com.xlxyvergil.luckfishing.Constants;
import com.xlxyvergil.luckfishing.LuckFishing;
import com.xlxyvergil.luckfishing.compat.jei.EntitySizeCache;
import com.xlxyvergil.luckfishing.config.UpgradeConfig;
import com.xlxyvergil.luckfishing.network.SyncedConfig;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** 客户端命令 {@code /luckf reload}：重读强化配置、测量未缓存的模型尺寸，并触发资源重载让 JEI 重建配方列表，无需重启。 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
public final class ClientCommands {

    private ClientCommands() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        final CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
            Commands.literal("luckf")
                .then(Commands.literal("reload")
                    .executes(context -> reload(context.getSource())))
        );
    }

    private static int reload(CommandSourceStack source) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            source.sendFailure(Component.translatable("command.luckfishing.reload.no_level"));
            return 0;
        }

        // 配置只在加载与手动重载时读盘，这里是唯一的手动重载入口。
        final UpgradeConfig config = LuckFishing.getUpgradeConfig();
        if (config != null) {
            config.reload();

            // 单机时客户端与主机共用同一份配置，重读后必须把展示用的副本一起刷新，
            // 否则 tooltip 与 JEI 会一直显示重载前的旧池子。
            // 多人游戏里副本是服务端下发的权威内容，不能用本地文件覆盖。
            if (mc.getSingleplayerServer() != null) {
                SyncedConfig.accept(config.toJson());
            }
        }

        final int added = EntitySizeCache.refresh();
        source.sendSuccess(() -> Component.translatable("command.luckfishing.reload.done", added), false);

        // 资源重载会让 JEI 重建配方，从而读到上面刚重读的配置。
        mc.reloadResourcePacks();
        return added;
    }
}
