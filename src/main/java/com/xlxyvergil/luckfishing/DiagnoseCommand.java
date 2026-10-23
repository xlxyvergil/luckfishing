package com.xlxyvergil.luckfishing;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.xlxyvergil.luckfishing.config.UpgradeConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;

/**
 * 服务端诊断命令 {@code /luckfishing diagnose [玩家]}：把「服务器这边真正读到的鱼竿数据」逐项打给玩家看。
 *
 * <p>存在的理由：钓怪判定完全在服务端进行，读的是服务端那份物品栈；客户端显示什么服务端根本不看。
 * 所以出现「主机正常、客机走默认池」这类现象时，唯一有意义的证据是服务器读到了什么——这个命令就是把它
 * 摊开来：物品 id、强化标记原文、解析结果、配置是否认识它、等级与出怪概率，以及配置里一共几个强化条目。
 *
 * <p>用它可以在不钓鱼、不开第二台机器的情况下确认：是那根竿在服务端没有标记，还是标记在但配置里查不到。
 */
@Mod.EventBusSubscriber(modid = Constants.MOD_ID)
public final class DiagnoseCommand {

    private DiagnoseCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {

        event.getDispatcher().register(
            Commands.literal(Constants.MOD_ID)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("diagnose")
                    .executes(context -> diagnose(context.getSource(), context.getSource().getPlayerOrException()))
                    .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> diagnose(context.getSource(), EntityArgument.getPlayer(context, "target")))))
        );
    }

    /** 把该玩家两只手的鱼竿数据与配置状态逐行输出。 */
    private static int diagnose(CommandSourceStack source, ServerPlayer player) throws CommandSyntaxException {

        final UpgradeConfig config = LuckFishing.getUpgradeConfig();

        line(source, "玩家: " + player.getGameProfile().getName() + (player.level().isClientSide() ? " (客户端)" : " (服务端)"));
        describeRod(source, config, "主手", player.getMainHandItem());
        describeRod(source, config, "副手", player.getOffhandItem());

        if (config == null) {

            line(source, "配置: 尚未加载（upgradeConfig == null），此时不会生成任何生物");
            return 1;
        }

        line(source, "配置: " + config.getUpgrades().size() + " 个强化条目, rodTag = " + config.getRodTag());
        line(source, "配置内的强化道具: " + config.getUpgrades().keySet());
        return 1;
    }

    private static void describeRod(CommandSourceStack source, UpgradeConfig config, String hand, ItemStack stack) {

        final String raw = Constants.getRawUpgrade(stack);
        final ResourceLocation applied = UpgradeConfig.getAppliedUpgrade(stack);
        final boolean isRod = config != null && config.isFishingRod(stack);

        line(source, hand + ": " + ForgeRegistries.ITEMS.getKey(stack.getItem()) + " 判定为鱼竿 = " + isRod);
        line(source, hand + ": 强化标记原文 = " + (raw == null ? "<无>" : "'" + raw + "'"));
        line(source, hand + ": 解析出的强化 = " + applied + " 配置已知 = " + (config != null && config.hasUpgrade(applied)));

        if (config != null) {

            line(source, hand + ": 等级 = " + config.tierOf(applied)
                    + " 出怪概率 = " + String.format(Locale.ROOT, "%.2f", config.chanceOf(applied)));
        }
    }

    private static void line(CommandSourceStack source, String text) {

        source.sendSuccess(() -> Component.literal("[luckfishing] " + text), false);
    }
}
