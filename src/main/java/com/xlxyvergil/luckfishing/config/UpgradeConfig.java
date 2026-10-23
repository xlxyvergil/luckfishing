package com.xlxyvergil.luckfishing.config;

import com.google.gson.annotations.Expose;
import com.xlxyvergil.luckfishing.Constants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nullable;

/**
 * 配置鱼竿可获得的铁砧强化，以及各强化解锁的实体池。
 * 每个强化道具有一个等级、一次钓获的出怪概率，以及一张「实体 → 权重」的池。
 *
 * <p>池按等级累加：强化到等级 N 的鱼竿可取所有等级 ≤ N 的池，最高等级可覆盖全部池；
 * 未强化的鱼竿也能取所有池，只是用 {@link #UNUPGRADED_CHANCE}。
 *
 * <p>权重只表示零幸运下的占比。幸运越高权重指数越低，稀有项越容易出现，见 {@link #luckExponent(float)}：
 * 指数为 0 时全池等概率，再低则最稀有的变成最可能的。
 */
public class UpgradeConfig {

    /** 新发现实体的默认权重。 */
    private static final double DEFAULT_WEIGHT = 1.0D;

    /** 配置文件未提供时的默认出怪概率。 */
    private static final double DEFAULT_CHANCE = 0.2D;

    /** 未强化鱼竿使用的概率；默认 1.0，保持原有的必出怪行为，除非手动调低。 */
    private static final double UNUPGRADED_CHANCE = 1.0D;

    /** 默认强化的等级，也是未强化鱼竿报告的等级。 */
    private static final int UNUPGRADED_TIER = 0;

    /** 零幸运时的权重指数，此时权重就是它在池中的占比。 */
    private static final double ALPHA_AT_ZERO_LUCK = 1.0D;

    /** 幸运趋于无穷时权重指数的极限；取 -1 即对权重取倒数，最稀有的变成最可能的。 */
    private static final double ALPHA_MIN = -1.0D;

    /** 使权重指数与 {@link #ALPHA_MIN} 的距离减半所需的幸运值。 */
    private static final double LUCK_HALF_LIFE = 1.0D;

    /** 扫描实体池时一并写入配置的默认强化道具。 */
    private static final String DEFAULT_UPGRADE = "minecraft:iron_ingot";

    /** 默认视为鱼竿的物品标签。 */
    private static final String DEFAULT_ROD_TAG = "forge:fishing_rods";

    /** 本配置的加载来源文件，用于运行中重新读取。 */
    private File configFile;

    /** 成员被视为鱼竿的物品标签。 */
    @Expose
    private String rodTag = DEFAULT_ROD_TAG;

    /** 除 {@link #rodTag} 外额外视为鱼竿的物品 id。 */
    @Expose
    private List<String> rods = new ArrayList<>();

    /** 以强化道具 id 为键的强化配置。 */
    @Expose
    private Map<String, Upgrade> upgrades = new TreeMap<>();

    /** 该物品是否是可强化的鱼竿。 */
    public boolean isFishingRod(ItemStack stack) {

        if (stack.isEmpty()) {

            return false;
        }

        if (stack.getItem() instanceof FishingRodItem || stack.is(Items.FISHING_ROD)) {

            return true;
        }

        final ResourceLocation tagId = ResourceLocation.tryParse(this.rodTag);

        if (tagId != null && stack.is(TagKey.create(ForgeRegistries.ITEMS.getRegistryKey(), tagId))) {

            return true;
        }

        final ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());

        return itemId != null && this.rods.contains(itemId.toString());
    }

    /** 该物品对应的强化配置，不是强化道具时为 null。 */
    public Upgrade getUpgrade(ItemStack stack) {

        final ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());

        return id == null ? null : this.upgrades.get(id.toString());
    }

    /** 全部强化配置，键为强化道具 id。 */
    public Map<String, Upgrade> getUpgrades() {

        return this.upgrades;
    }

    /** 视为鱼竿的物品标签。 */
    public String getRodTag() {

        return this.rodTag;
    }

    /** 鱼竿上已应用的强化，没有时为 null。 */
    public static ResourceLocation getAppliedUpgrade(ItemStack stack) {

        final CompoundTag tag = stack.getTag();

        if (tag == null || !tag.contains(Constants.UPGRADE_TAG, Tag.TAG_STRING)) {

            return null;
        }

        return ResourceLocation.tryParse(tag.getString(Constants.UPGRADE_TAG));
    }

    /** 鱼竿所取的等级。 */
    public int tierOf(ResourceLocation appliedUpgrade) {

        if (appliedUpgrade == null) {

            return UNUPGRADED_TIER;
        }

        final Upgrade upgrade = this.upgrades.get(appliedUpgrade.toString());

        return upgrade == null ? UNUPGRADED_TIER : upgrade.getTier();
    }

    /** 配置是否认识该强化。 */
    public boolean hasUpgrade(ResourceLocation appliedUpgrade) {

        return appliedUpgrade != null && this.upgrades.containsKey(appliedUpgrade.toString());
    }

    /** 该鱼竿一次钓获的出怪概率。 */
    public double chanceOf(ResourceLocation appliedUpgrade) {

        if (!hasUpgrade(appliedUpgrade)) {

            return UNUPGRADED_CHANCE;
        }

        return chanceFor(tierOf(appliedUpgrade));
    }

    /**
     * 抽取本次出怪：未强化鱼竿取所有等级的池并用 {@link #UNUPGRADED_CHANCE}，已强化鱼竿取等级 ≤ 自身的池并用该等级概率。
     * 返回抽中的实体及其 NBT，不出怪时为 null。
     */
    public Pick pickMob(ResourceLocation appliedUpgrade, float luck, RandomSource random) {

        // 配置里没有的强化按未强化处理，避免强化道具被移除后鱼竿没有池可用。
        final Upgrade own = appliedUpgrade == null ? null : this.upgrades.get(appliedUpgrade.toString());
        final int rodTier = own == null ? Integer.MAX_VALUE : own.getTier();
        final double chance = own == null ? UNUPGRADED_CHANCE : chanceFor(rodTier);

        if (chance <= 0.0D || random.nextDouble() >= chance) {

            return null;
        }

        final double alpha = luckExponent(luck);
        double total = 0.0D;

        for (Upgrade upgrade : this.upgrades.values()) {

            for (Map.Entry<String, Entry> entry : upgrade.getMobs().entrySet()) {

                if (isSelectable(rodTier, upgrade, entry.getKey(), entry.getValue())) {

                    total += weightFor(entry.getValue(), alpha);
                }
            }
        }

        if (total <= 0.0D) {

            return null;
        }

        double roll = random.nextDouble() * total;

        for (Upgrade upgrade : this.upgrades.values()) {

            for (Map.Entry<String, Entry> entry : upgrade.getMobs().entrySet()) {

                if (!isSelectable(rodTier, upgrade, entry.getKey(), entry.getValue())) {

                    continue;
                }

                roll -= weightFor(entry.getValue(), alpha);

                if (roll < 0.0D) {

                    return new Pick(ResourceLocation.tryParse(entry.getKey()), entry.getValue().getNbt());
                }
            }
        }

        return null;
    }

    /**
     * 把幸运映射为权重指数：从 {@link #ALPHA_AT_ZERO_LUCK} 开始，每 {@link #LUCK_HALF_LIFE} 的幸运让与
     * {@link #ALPHA_MIN} 的距离减半，池从配置占比滑向等概率再滑向完全反转。
     */
    private static double luckExponent(float luck) {

        if (luck <= 0.0F) {

            return ALPHA_AT_ZERO_LUCK;
        }

        final double decay = Math.pow(0.5D, luck / LUCK_HALF_LIFE);

        return ALPHA_MIN + (ALPHA_AT_ZERO_LUCK - ALPHA_MIN) * decay;
    }

    /** 参与抽取的权重份额。 */
    private static double weightFor(Entry entry, double alpha) {

        return Math.pow(entry.getWeight(), alpha);
    }

    /** 该鱼竿可取的最高等级所配置的出怪概率。 */
    private double chanceFor(int rodTier) {

        double chance = 0.0D;
        int bestTier = Integer.MIN_VALUE;

        for (Upgrade upgrade : this.upgrades.values()) {

            if (upgrade.getTier() > rodTier) {

                continue;
            }

            if (upgrade.getTier() > bestTier) {

                bestTier = upgrade.getTier();
                chance = upgrade.getChance();
            }

            else if (upgrade.getTier() == bestTier) {

                chance = Math.max(chance, upgrade.getChance());
            }
        }

        return chance;
    }

    /** 只有等级可达、权重为正、且 id 仍指向已注册实体的条目才能被抽中，避免手写错误或失效条目占用份额。 */
    private static boolean isSelectable(int rodTier, Upgrade upgrade, String id, Entry entry) {

        if (upgrade.getTier() > rodTier || entry == null || entry.getWeight() <= 0.0D) {

            return false;
        }

        final ResourceLocation location = ResourceLocation.tryParse(id);

        return location != null && ForgeRegistries.ENTITY_TYPES.containsKey(location);
    }

    /**
     * 扫描全部已注册实体，与现有配置文件合并后写回：文件里已有的强化保留等级、概率和池，
     * 新实体按默认权重加入默认强化，手写条目即使不是敌对生物也保留。
     */
    public static UpgradeConfig load(File configFile) {

        final UpgradeConfig config = new UpgradeConfig();
        config.configFile = configFile;

        // 收集当前所有已注册敌对生物的默认条目。
        final Map<String, Entry> scanned = new TreeMap<>();

        for (EntityType<?> entityType : ForgeRegistries.ENTITY_TYPES.getValues()) {

            if (entityType.getCategory() != MobCategory.MONSTER) {

                continue;
            }

            final ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entityType);

            if (id != null) {

                scanned.put(id.toString(), new Entry(DEFAULT_WEIGHT));
            }
        }

        config.upgrades.put(DEFAULT_UPGRADE, new Upgrade(UNUPGRADED_TIER, DEFAULT_CHANCE, scanned));

        config.readFromDisk();
        config.writeToDisk();

        return config;
    }

    /**
     * 重新读取配置文件，使道具和池的改动下次钓获即生效。
     * 只读文件，不重扫注册表也不回写；文件无法解析时视为空池。
     */
    public void reload() {

        if (this.configFile == null) {

            return;
        }

        final UpgradeConfig loaded = new UpgradeConfig();
        loaded.configFile = this.configFile;
        loaded.readFromDisk();

        this.rodTag = loaded.rodTag;
        this.rods = loaded.rods;
        this.upgrades = loaded.upgrades;
    }

    /** 序列化为与配置文件同结构的 JSON，供服务端把配置同步给客户端。 */
    public String toJson() {

        return Constants.COMPACT_GSON.toJson(this);
    }

    /** 从 {@link #toJson()} 产出的 JSON 还原一份配置；内容为空或无法解析时返回 null。 */
    @Nullable
    public static UpgradeConfig fromJson(String json) {

        if (json == null || json.isEmpty()) {

            return null;
        }

        try {

            return Constants.COMPACT_GSON.fromJson(json, UpgradeConfig.class);
        } catch (Exception exception) {

            Constants.LOG.warn("Received an unreadable upgrade config from the server; keeping the local one.", exception);
            return null;
        }
    }

    /** 把配置文件里的值覆盖到默认值上，文件里没有的强化保留，避免手写条目被丢弃。 */
    private void readFromDisk() {

        if (this.configFile == null || !this.configFile.exists()) {

            return;
        }

        try (FileReader reader = new FileReader(this.configFile)) {

            final UpgradeConfig loaded = Constants.GSON.fromJson(reader, UpgradeConfig.class);

            if (loaded == null) {

                return;
            }

            if (loaded.rodTag != null) {

                this.rodTag = loaded.rodTag;
            }

            if (loaded.rods != null) {

                this.rods = loaded.rods;
            }

            if (loaded.upgrades == null) {

                return;
            }

            for (Map.Entry<String, Upgrade> configEntry : loaded.upgrades.entrySet()) {

                if (configEntry.getValue() == null || ResourceLocation.tryParse(configEntry.getKey()) == null) {

                    continue;
                }

                this.upgrades.put(configEntry.getKey(), configEntry.getValue());
            }
        }

        catch (Exception ignored) {

            // 文件缺失、读不到或格式错误时按空池处理。
        }
    }

    /** 把当前配置写回磁盘，配置目录不存在时自动创建。 */
    private void writeToDisk() {

        if (this.configFile == null) {

            return;
        }

        this.configFile.getParentFile().mkdirs();

        try (FileWriter writer = new FileWriter(this.configFile)) {

            Constants.GSON.toJson(this, writer);
        }

        catch (Exception ignored) {

            // 文件写不进去时无能为力。
        }
    }

    /** 单个强化道具：所属等级、一次钓获的出怪概率，以及它解锁的实体池。 */
    public static class Upgrade {

        @Expose
        private int tier;

        @Expose
        private double chance;

        @Expose
        private Map<String, Entry> mobs = new TreeMap<>();

        private Upgrade() {

        }

        public Upgrade(int tier, double chance, Map<String, Entry> mobs) {

            this.tier = tier;
            this.chance = chance;
            this.mobs = mobs;
        }

        public int getTier() {

            return this.tier;
        }

        public double getChance() {

            return this.chance;
        }

        public Map<String, Entry> getMobs() {

            return this.mobs;
        }
    }

    /** 池中的单个实体，权重即它的抽中概率。 */
    public static class Entry {

        @Expose
        private double weight;

        /** 生成时加载到实体上的 SNBT，可省略，写法同 {@code /summon} 的 NBT。 */
        @Expose
        private String nbt;

        public Entry(double weight) {

            this.weight = weight;
        }

        public double getWeight() {

            return this.weight;
        }

        public String getNbt() {

            return this.nbt;
        }
    }

    /** 本次抽中的实体：它的 id 和要加载的 NBT。 */
    public static class Pick {

        private final ResourceLocation id;
        private final String nbt;

        public Pick(ResourceLocation id, String nbt) {

            this.id = id;
            this.nbt = nbt;
        }

        public ResourceLocation getId() {

            return this.id;
        }

        public String getNbt() {

            return this.nbt;
        }
    }
}
