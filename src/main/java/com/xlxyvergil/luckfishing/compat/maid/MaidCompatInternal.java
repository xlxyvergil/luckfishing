package com.xlxyvergil.luckfishing.compat.maid;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidFishedEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.xlxyvergil.luckfishing.FishingHandler;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;

/** 女仆兼容的类型隔离层；直接引用女仆类型，只能在装有该模组时加载（由 {@link MaidCompat} 保证）。 */
final class MaidCompatInternal {

    /** 事件总线按实例注册，用单例即可；静态方法也可以，但实例更直观地避免类被反复扫描。 */
    private static final MaidCompatInternal INSTANCE = new MaidCompatInternal();

    private MaidCompatInternal() {}

    static void registerEvents() {
        NeoForge.EVENT_BUS.register(INSTANCE);
    }

    /**
     * 女仆钓上实体时与玩家一致地生成生物。
     *
     * <p>女仆的战利品用的是事件之前构建的列表，所以直接取消事件来用实体替换鱼；耐久仍按钓钩传入的伤害扣除。
     * 1.21.1 的车万女仆把该事件标注为可取消（{@code ICancellableEvent}），与这里的写法一致。
     */
    @SubscribeEvent
    public void onMaidFished(MaidFishedEvent event) {

        final EntityMaid maid = event.getMaid();

        if (FishingHandler.trySpawn(maid, maid.getMainHandItem())) {

            event.setCanceled(true);
        }
    }
}
