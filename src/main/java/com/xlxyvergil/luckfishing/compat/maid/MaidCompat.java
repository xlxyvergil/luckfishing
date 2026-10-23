package com.xlxyvergil.luckfishing.compat.maid;

import net.neoforged.fml.ModList;

/**
 * 车万女仆（TLM）兼容入口。
 *
 * <p>女仆用的是自己抛出的钓钩，收回时发的是自己的事件而非 {@code ItemFishedEvent}。本类只引用原版和
 * {@link ModList}，任何情况下都能加载；涉及女仆类型的代码都在包内可见的 {@link MaidCompatInternal}，
 * 经 {@link #isLoaded()} 短路后才触达。未装女仆模组时该类不会被加载，类型也就不会解析。
 */
public final class MaidCompat {

    private static final String TLM_MODID = "touhou_little_maid";

    private MaidCompat() {}

    /** 仅在装有女仆模组时注册其钓鱼监听。 */
    public static void registerEvents() {
        if (isLoaded()) {
            MaidCompatInternal.registerEvents();
        }
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(TLM_MODID);
    }
}
