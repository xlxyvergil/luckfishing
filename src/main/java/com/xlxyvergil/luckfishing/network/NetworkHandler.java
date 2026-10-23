package com.xlxyvergil.luckfishing.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 模组的网络通道，目前只承载一件事：把服务端的强化配置下发给客户端。
 * <p>判定钓获仍完全在服务端进行，客户端拿到配置只为显示 tooltip，避免远端客户端读到自己的本地
 * {@code config/luckfishing.json}（其内容通常与主机不一致，未强化池会显示成 100%）。
 */
public final class NetworkHandler {

    /** 协议版本，两端不一致时 NeoForge 会拒绝建立连接。 */
    private static final String PROTOCOL_VERSION = "1";

    private NetworkHandler() {
    }

    /** 建立通道并注册消息；由模组总线上的 {@link RegisterPayloadHandlersEvent} 在通信建立前调用。 */
    public static void register(RegisterPayloadHandlersEvent event) {

        final PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        // 处理器默认在主线执行，无需再手工 enqueueWork。
        registrar.playToClient(
                SyncConfigPayload.TYPE,
                SyncConfigPayload.STREAM_CODEC,
                (payload, context) -> SyncedConfig.accept(payload.configJson()));
    }

    /** 远端是否也注册了本通道；没有（原版客户端或旧版模组）时不下发，省得发无用包。 */
    public static boolean isRemotePresent(ServerPlayer player) {

        return player.connection.hasChannel(SyncConfigPayload.TYPE);
    }

    /** 把序列化后的服务端配置推送给指定玩家。 */
    public static void sendTo(ServerPlayer player, String configJson) {

        PacketDistributor.sendToPlayer(player, new SyncConfigPayload(configJson));
    }
}
