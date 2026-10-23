package com.xlxyvergil.luckfishing.network;

import com.xlxyvergil.luckfishing.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;

/**
 * 服务端 -> 客户端：携带序列化后的强化配置，客户端解析后供 tooltip 使用。
 * <p>1.21 起 NeoForge 的自定义包统一走 {@link CustomPacketPayload}：类型 id 由框架写在包体之前，
 * 编解码器只负责自己的负载，不再需要协议版本号与手工分配的 message id。
 */
public record SyncConfigPayload(String configJson) implements CustomPacketPayload {

    /** 本包的类型 id，同时用于判断对端是否装了本模组（{@code hasChannel}）。 */
    public static final CustomPacketPayload.Type<SyncConfigPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "sync_config"));

    /**
     * 配置 JSON 直接走字节数组传输，避开 {@code writeUtf} 的 32767 字节上限。
     */
    public static final StreamCodec<FriendlyByteBuf, SyncConfigPayload> STREAM_CODEC =
            CustomPacketPayload.codec(SyncConfigPayload::write, SyncConfigPayload::read);

    private static SyncConfigPayload read(FriendlyByteBuf buffer) {

        return new SyncConfigPayload(new String(buffer.readByteArray(), StandardCharsets.UTF_8));
    }

    private void write(FriendlyByteBuf buffer) {

        buffer.writeByteArray(this.configJson.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {

        return TYPE;
    }
}
