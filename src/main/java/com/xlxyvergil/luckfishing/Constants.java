package com.xlxyvergil.luckfishing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
}
