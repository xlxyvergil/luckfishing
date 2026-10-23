package com.xlxyvergil.luckfishing.compat.jei;

import com.google.gson.annotations.Expose;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xlxyvergil.luckfishing.Constants;
import com.xlxyvergil.luckfishing.client.ActiveConfig;
import com.xlxyvergil.luckfishing.config.UpgradeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.TreeMap;

/**
 * 按实体 id 缓存钓鱼池中每个实体的真实渲染尺寸，供 JEI 页面把模型统一缩放到同一框内
 * （末影龙这类碰撞箱与模型差距很大的实体尤其明显）。
 *
 * <p>测量方式：走真实渲染管线，但把 {@link MultiBufferSource} 换成只记录顶点的
 * {@link VertexConsumer}，不做实际绘制，因此原版与 GeckoLib 等自定义模型都能测到，也不需要 GL 上下文。
 * 结果与碰撞箱逐轴取大，存入 {@code config/luckfishing/entity_sizes.json}，该文件也可手动覆盖。
 * 首次展示时惰性测量，{@link #refresh()} 只补测配置新增的实体。
 */
@OnlyIn(Dist.CLIENT)
public final class EntitySizeCache {

    /** 缓存文件格式版本；版本不符则丢弃，全部重新测量。 */
    private static final int FORMAT_VERSION = 3;

    private static final Type DATA_TYPE = new TypeToken<Data>() {}.getType();

    private static final Map<String, Size> SIZES = new TreeMap<>();

    private static boolean loaded;
    private static File file;

    private EntitySizeCache() {
    }

    private static File file() {
        if (file == null) {
            file = FMLPaths.CONFIGDIR.get().resolve(Constants.MOD_ID).resolve("entity_sizes.json").toFile();
        }
        return file;
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;

        final File configFile = file();
        if (!configFile.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(configFile)) {
            final Data read = Constants.GSON.fromJson(reader, DATA_TYPE);
            if (read != null && read.version == FORMAT_VERSION && read.sizes != null) {
                SIZES.putAll(read.sizes);
            }
        }

        catch (Exception ignored) {
            // 文件缺失或格式错误时视为尚无缓存。
        }
    }

    private static void save() {
        final File configFile = file();
        try {
            final File parent = configFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            final Data data = new Data();
            data.sizes = new TreeMap<>(SIZES);
            try (FileWriter writer = new FileWriter(configFile)) {
                Constants.GSON.toJson(data, DATA_TYPE, writer);
            }
        }

        catch (Exception ignored) {
            // 文件写不进去时无法处理。
        }
    }

    /** 缓存的尺寸，尚未测量时为 null。 */
    @Nullable
    public static Size get(EntityType<?> type) {
        ensureLoaded();
        final ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return id == null ? null : SIZES.get(id.toString());
    }

    /** 返回缓存尺寸，首次使用时测量并写入文件；无法测量时回退到碰撞箱。 */
    public static Size getOrMeasure(EntityType<?> type) {
        ensureLoaded();

        final ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (id != null) {
            final Size cached = SIZES.get(id.toString());
            if (cached != null) {
                return cached;
            }
        }

        Size measured = measure(type);
        if (measured == null) {
            measured = fallback(type);
        }

        if (id != null) {
            SIZES.put(id.toString(), measured);
            save();
        }
        return measured;
    }

    /** 测量尚未缓存的实体尺寸，用于运行中改配置；配置由调用方（{@code /luckf reload}）先行重读，返回新测量的实体数量。 */
    public static int refresh() {
        final UpgradeConfig config = ActiveConfig.get();
        if (config == null) {
            return 0;
        }

        ensureLoaded();

        int added = 0;
        for (UpgradeConfig.Upgrade upgrade : config.getUpgrades().values()) {
            if (upgrade == null) {
                continue;
            }
            for (String id : upgrade.getMobs().keySet()) {
                if (SIZES.containsKey(id)) {
                    continue;
                }
                final ResourceLocation location = ResourceLocation.tryParse(id);
                if (location == null) {
                    continue;
                }
                // ENTITY_TYPE 是带默认值（猪）的注册表，get() 对未知 id 不会返回 null，必须走 getOptional。
                final EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(location).orElse(null);
                if (type == null) {
                    continue;
                }
                Size measured = measure(type);
                if (measured == null) {
                    measured = fallback(type);
                }
                SIZES.put(id, measured);
                added++;
            }
        }

        if (added > 0) {
            save();
        }
        return added;
    }

    @Nullable
    private static Size measure(EntityType<?> type) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return null;
        }

        final Entity created = type.create(mc.level);
        if (!(created instanceof LivingEntity living)) {
            return null;
        }

        try {
            // 走真实渲染器，但把顶点交给只记包围盒的 consumer，原版与 GeckoLib 等模型都走同一路径。
            final BoundsVertexConsumer consumer = new BoundsVertexConsumer();
            final MultiBufferSource buffers = renderType -> consumer;

            living.tickCount = 0;
            mc.getEntityRenderDispatcher().render(
                living,
                0.0D, 0.0D, 0.0D,
                0.0F, 1.0F,
                new PoseStack(),
                buffers,
                LightTexture.FULL_BRIGHT
            );

            return combine(consumer.toSize(), living);
        }

        catch (Exception exception) {
            Constants.LOG.debug("Failed to measure the model size of {}", type, exception);
            return combine(null, living);
        }
    }

    /** 模型盒与碰撞箱逐轴取大：模型更大就保留模型，模型不可用时回退到碰撞箱。 */
    private static Size combine(@Nullable Size modelSize, Entity entity) {
        final float hitboxWidth = Math.max(entity.getBbWidth(), 0.05F);
        final float hitboxHeight = Math.max(entity.getBbHeight(), 0.05F);

        if (modelSize == null) {
            return new Size(hitboxWidth, hitboxHeight, hitboxWidth);
        }

        return new Size(
            Math.max(modelSize.getWidth(), hitboxWidth),
            Math.max(modelSize.getHeight(), hitboxHeight),
            Math.max(modelSize.getLength(), hitboxWidth)
        );
    }

    private static Size fallback(EntityType<?> type) {
        final EntityDimensions dimensions = type.getDimensions();
        final float width = Math.max(dimensions.width(), 0.05F);
        final float height = Math.max(dimensions.height(), 0.05F);
        return new Size(width, height, width);
    }

    /** 单个实体的渲染包围盒，单位为方块。 */
    public static class Size {

        @Expose
        private float width;

        @Expose
        private float height;

        @Expose
        private float length;

        public Size() {
        }

        public Size(float width, float height, float length) {
            this.width = width;
            this.height = height;
            this.length = length;
        }

        public float getWidth() {
            return this.width;
        }

        public float getHeight() {
            return this.height;
        }

        public float getLength() {
            return this.length;
        }
    }

    /**
     * 只记录顶点包围盒的 {@link VertexConsumer}，用于在不触碰 GL 的情况下测量模型。
     * <p>1.21 起顶点写入改为一串 setter（addVertex/setColor/setUv/...），不再有 endVertex，
     * 因此这里只需把 {@code addVertex} 收下的坐标累进包围盒，其余写入口全部空实现。
     */
    private static final class BoundsVertexConsumer implements VertexConsumer {

        private float minX = Float.MAX_VALUE;
        private float minY = Float.MAX_VALUE;
        private float minZ = Float.MAX_VALUE;
        private float maxX = -Float.MAX_VALUE;
        private float maxY = -Float.MAX_VALUE;
        private float maxZ = -Float.MAX_VALUE;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (x < this.minX) {
                this.minX = x;
            }
            if (x > this.maxX) {
                this.maxX = x;
            }
            if (y < this.minY) {
                this.minY = y;
            }
            if (y > this.maxY) {
                this.maxY = y;
            }
            if (z < this.minZ) {
                this.minZ = z;
            }
            if (z > this.maxZ) {
                this.maxZ = z;
            }
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            return this;
        }

        @Nullable
        Size toSize() {
            if (this.minX > this.maxX) {
                return null;
            }
            // 顶点写出时已换算成方块，这里不能再除 16。
            return new Size(
                Math.max(this.maxX - this.minX, 0.05F),
                Math.max(this.maxY - this.minY, 0.05F),
                Math.max(this.maxZ - this.minZ, 0.05F)
            );
        }
    }

    /** 缓存文件的磁盘结构，带版本以便丢弃旧格式。 */
    private static final class Data {

        @Expose
        private int version = FORMAT_VERSION;

        @Expose
        private Map<String, Size> sizes = new TreeMap<>();
    }
}
