package com.xlxyvergil.luckfishing.client;

import com.xlxyvergil.luckfishing.LuckFishing;
import com.xlxyvergil.luckfishing.config.UpgradeConfig;
import com.xlxyvergil.luckfishing.network.SyncedConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/**
 * 客户端「当前生效」的强化配置，所有只读数（tooltip、JEI 页面、模型尺寸补测）都必须走这里。
 *
 * <p>优先用服务端登录时同步下来的那份：服务器判定用的是什么池、什么概率，客户端展示就应该是同一份，
 * 否则多人游戏里会出现「客户端读到自己的 {@code config/luckfishing.json}，未强化池显示成 100%」这类
 * 客户端与主机不一致的问题。还没收到同步时（进服第一帧、单机首次加载）退回本地文件里的配置。
 */
@OnlyIn(Dist.CLIENT)
public final class ActiveConfig {

    private ActiveConfig() {
    }

    /** 服务端下发的副本优先，没有时退回本地配置；两者都没有时返回 null。 */
    @Nullable
    public static UpgradeConfig get() {

        final UpgradeConfig synced = SyncedConfig.get();

        return synced != null ? synced : LuckFishing.getUpgradeConfig();
    }
}
