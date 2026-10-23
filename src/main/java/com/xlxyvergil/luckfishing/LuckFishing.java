package com.xlxyvergil.luckfishing;

import com.xlxyvergil.luckfishing.compat.maid.MaidCompat;
import com.xlxyvergil.luckfishing.config.UpgradeConfig;
import com.xlxyvergil.luckfishing.network.NetworkHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.loading.FMLPaths;

import java.io.File;

@Mod(Constants.MOD_ID)
public class LuckFishing {

    private static UpgradeConfig upgradeConfig;

    /**
     * 网络通道必须在注册表锁定前建立，所以挂在模组总线（mod bus）上最早执行；
     * 配置要等全部实体注册完毕才能扫描，所以另挂加载完成事件。
     */
    public LuckFishing(IEventBus modBus) {

        modBus.addListener(NetworkHandler::register);
        modBus.addListener(LuckFishing::onLoadComplete);
    }

    /** 所有模组加载完成后触发，此时注册表已含全部实体，在这里生成并更新强化配置。 */
    private static void onLoadComplete(FMLLoadCompleteEvent event) {

        final File configFile = FMLPaths.CONFIGDIR.get().resolve(Constants.MOD_ID + ".json").toFile();
        upgradeConfig = UpgradeConfig.load(configFile);

        // 女仆钓鱼走独立事件，单独注册监听；是否有该模组的判断封装在 MaidCompat 里。
        MaidCompat.registerEvents();
    }

    public static UpgradeConfig getUpgradeConfig() {

        return upgradeConfig;
    }
}
