package com.xlxyvergil.luckfishing.compat.maid;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidFishedEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.xlxyvergil.luckfishing.FishingHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** 女仆兼容的类型隔离层；直接引用女仆类型，只能在装有该模组时加载（由 {@link MaidCompat} 保证）。 */
final class MaidCompatInternal {

    private MaidCompatInternal() {}

    static void registerEvents() {
        MinecraftForge.EVENT_BUS.register(MaidCompatInternal.class);
    }

    /**
     * 女仆钓上实体时与玩家一致地生成生物。
     *
     * <p>女仆的战利品用的是事件之前构建的列表，所以直接取消事件来用实体替换鱼；耐久仍按钓钩传入的伤害扣除。
     * 方法为 public 是因为事件总线只认 public 方法，其代理类也生成在同一包内，便于访问本包私有类。
     */
    @SubscribeEvent
    public static void onMaidFished(MaidFishedEvent event) {

        final EntityMaid maid = event.getMaid();

        if (FishingHandler.trySpawn(maid, maid.getMainHandItem())) {

            event.setCanceled(true);
        }
    }
}
