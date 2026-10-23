package com.xlxyvergil.luckfishing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Constants {

    public static final String MOD_ID = "luckfishing";
    public static final String MOD_NAME = "Luck Fishing";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    /**
     * 记录鱼竿所带强化道具 id 的自定义数据键。
     * <p>1.21 起物品 NBT 改由数据组件承载，这里存进 {@link DataComponents#CUSTOM_DATA}，与 {@code /summon} 时代的
     * 「物品自定义数据」语义一致：不参与原版比较，也不影响其它模组。
     */
    public static final String UPGRADE_TAG = MOD_ID + ":upgrade";

    /** 读写强化配置共用的 GSON，只序列化带 @Expose 的字段。 */
    public static final Gson GSON = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().setPrettyPrinting().create();

    /** 网络同步使用的 GSON，字段与配置文件一致但不做缩进，减小包体。 */
    public static final Gson COMPACT_GSON = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

    private Constants() {
    }

    /** 把强化道具 id 写进物品副本的自定义数据，覆盖原有值。 */
    public static void applyUpgrade(ItemStack stack, ResourceLocation upgradeId) {

        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(UPGRADE_TAG, upgradeId.toString()));
    }

    /** 读取物品上记录的强化道具 id，没有或格式无效时为 null。 */
    @Nullable
    public static ResourceLocation getAppliedUpgrade(@Nullable ItemStack stack) {

        if (stack == null || stack.isEmpty()) {

            return null;
        }

        final CustomData data = stack.get(DataComponents.CUSTOM_DATA);

        if (data == null || !data.contains(UPGRADE_TAG)) {

            return null;
        }

        return ResourceLocation.tryParse(data.copyTag().getString(UPGRADE_TAG));
    }
}
