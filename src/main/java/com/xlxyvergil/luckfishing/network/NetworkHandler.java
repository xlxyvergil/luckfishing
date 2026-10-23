package com.xlxyvergil.luckfishing.network;

import com.xlxyvergil.luckfishing.Constants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 模组的网络通道，目前只承载一件事：把服务端的强化配置下发给客户端。
 * <p>判定钓获仍完全在服务端进行，客户端拿到配置只为显示 tooltip，避免远端客户端读到自己的本地
 * {@code config/luckfishing.json}（其内容通常与主机不一致，未强化池会显示成 100%）。
 */
public final class NetworkHandler {

    /** 协议版本，两端必须一致才会放行连接。 */
    private static final String PROTOCOL_VERSION = "1";

    /** 同步消息的注册 id。 */
    private static final int SYNC_CONFIG_ID = 0;

    /**
     * 同步通道。
     * <p>这里刻意沿用 {@code new ResourceLocation(ns, path)}，而不是 47.4 才引入的
     * {@code ResourceLocation.fromNamespaceAndPath}：后者在 47.1–47.3 上并不存在，会让本模组在
     * {@code forge_version_range=[47,)} 所允许的老版本上抛 {@code NoSuchMethodError}。
     * 该构造器自 47.4 起被标为“待删除”，因此在此定向抑制这条纯编译期告警。
     */
    @SuppressWarnings("removal")
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Constants.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION),
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION));

    private static boolean registered;

    private NetworkHandler() {
    }

    /** 建立通道并注册消息，在模组构造时调用一次；注册表锁定后再建通道会抛异常。 */
    public static void register() {

        if (registered) {

            return;
        }

        registered = true;

        CHANNEL.messageBuilder(SyncConfigMessage.class, SYNC_CONFIG_ID, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncConfigMessage::encode)
                .decoder(SyncConfigMessage::decode)
                .consumerMainThread(SyncConfigMessage::handle)
                .add();
    }

    /** 远端是否也建立了本通道；没有（原版客户端或旧版模组）时不下发，省得发无用包。 */
    public static boolean isRemotePresent(ServerPlayer player) {

        return CHANNEL.isRemotePresent(player.connection.connection);
    }

    /** 把序列化后的服务端配置推送给指定玩家。 */
    public static void sendTo(ServerPlayer player, String configJson) {

        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncConfigMessage(configJson));
    }
}
