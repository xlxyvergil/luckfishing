package com.xlxyvergil.luckfishing.network;

import com.xlxyvergil.luckfishing.config.UpgradeConfig;
import org.jetbrains.annotations.Nullable;

/**
 * 客户端保存的服务端配置副本，登录时由 {@link NetworkHandler} 下发并覆盖。
 * <p>副本不需要在断线时清除：换服后新服务端会立刻下发它自己的配置把副本覆盖掉。
 * <p>故意不引用任何客户端专属类，服务端加载本类时不会出错（服务端只是注册消息，不会真正处理）。
 */
public final class SyncedConfig {

    private static volatile UpgradeConfig config;

    private SyncedConfig() {
    }

    /** 用服务端下发的 JSON 覆盖副本；解析失败时保留原值，宁可显示旧配置也不要清空。 */
    public static void accept(String json) {

        final UpgradeConfig loaded = UpgradeConfig.fromJson(json);

        if (loaded != null) {

            config = loaded;
        }
    }

    /** 服务端配置副本；尚未收到同步时为 null。 */
    @Nullable
    public static UpgradeConfig get() {

        return config;
    }
}
