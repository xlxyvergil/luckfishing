package com.xlxyvergil.luckfishing.client;

import com.xlxyvergil.luckfishing.Constants;
import com.xlxyvergil.luckfishing.compat.jei.EntitySizeCache;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 客户端命令 {@code /luckf reload}：重读强化配置、测量未缓存的模型尺寸，并触发资源重载让 JEI 重建配方列表，无需重启。 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Constants.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientCommands {

    private ClientCommands() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
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

        final int added = EntitySizeCache.refresh();
        source.sendSuccess(() -> Component.translatable("command.luckfishing.reload.done", added), false);

        // 资源重载会让 JEI 重建配方，从而经 LuckFishingJEIPlugin.registerRecipes 重读配置。
        mc.reloadResourcePacks();
        return added;
    }
}