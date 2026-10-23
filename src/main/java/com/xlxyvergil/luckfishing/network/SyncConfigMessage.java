package com.xlxyvergil.luckfishing.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/** 服务端 -> 客户端：携带序列化后的强化配置，客户端解析后供 tooltip 使用。 */
public class SyncConfigMessage {

    /** 配置 JSON，直接走字节数组传输，避开 {@code writeUtf} 的 32767 字节上限。 */
    private final String configJson;

    public SyncConfigMessage(String configJson) {

        this.configJson = configJson;
    }

    public void encode(FriendlyByteBuf buffer) {

        buffer.writeByteArray(this.configJson.getBytes(StandardCharsets.UTF_8));
    }

    public static SyncConfigMessage decode(FriendlyByteBuf buffer) {

        return new SyncConfigMessage(new String(buffer.readByteArray(), StandardCharsets.UTF_8));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {

        SyncedConfig.accept(this.configJson);
    }
}
