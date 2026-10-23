package com.xlxyvergil.luckfishing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Constants {

    public static final String MOD_ID = "luckfishing";
    public static final String MOD_NAME = "Luck Fishing";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    /** 记录鱼竿所带强化道具 id 的 NBT 键。 */
    public static final String UPGRADE_TAG = MOD_ID + ":upgrade";

    /** 读写强化配置共用的 GSON，只序列化带 @Expose 的字段。 */
    public static final Gson GSON = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().setPrettyPrinting().create();

    /** 网络同步使用的 GSON，字段与配置文件一致但不做缩进，减小包体。 */
    public static final Gson COMPACT_GSON = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

    /** 物品上记录的强化 id 原文（不解析），没有时为 null；排障时用来区分「没数据」和「数据解析不出来」。 */
    @Nullable
    public static String getRawUpgrade(ItemStack stack) {

        final CompoundTag tag = stack.getTag();

        if (tag == null || !tag.contains(UPGRADE_TAG)) {

            return null;
        }

        return tag.getString(UPGRADE_TAG);
    }
}

