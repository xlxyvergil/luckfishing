package com.xlxyvergil.luckfishing;

import com.xlxyvergil.luckfishing.compat.maid.MaidCompat;
import com.xlxyvergil.luckfishing.config.UpgradeConfig;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;

@Mod(Constants.MOD_ID)
@Mod.EventBusSubscriber(modid = Constants.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class LuckFishing {

    private static UpgradeConfig upgradeConfig;

    /** 所有模组加载完成后触发，此时注册表已含全部实体，在这里生成并更新强化配置。 */
    @SubscribeEvent
    public static void onLoadComplete(FMLLoadCompleteEvent event) {

        final File configFile = FMLPaths.CONFIGDIR.get().resolve(Constants.MOD_ID + ".json").toFile();
        upgradeConfig = UpgradeConfig.load(configFile);

        // 女仆钓鱼走独立事件，单独注册监听；是否有该模组的判断封装在 MaidCompat 里。
        MaidCompat.registerEvents();
    }

    public static UpgradeConfig getUpgradeConfig() {

        return upgradeConfig;
    }
}
