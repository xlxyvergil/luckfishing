package com.xlxyvergil.luckfishing;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.xlxyvergil.luckfishing.config.UpgradeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

@EventBusSubscriber(modid = Constants.MOD_ID)
public class FishingHandler {

    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {

        final Player fisher = event.getEntity();

        // 判定钓上实体时清空鱼获。
        if (trySpawn(fisher, fisher.getMainHandItem())) {

            event.getDrops().clear();
        }
    }

    /**
     * 为刚结束钓鱼的实体生成生物。原版事件只覆盖原版钓钩，其它实现（见 {@code compat.maid.MaidCompat}）直接调用本方法。
     * 返回本次是否判定为实体，生成失败也算，调用方据此取消鱼获。
     */
    public static boolean trySpawn(Entity fisher, ItemStack rod) {

        if (fisher.level().isClientSide()) {

            return false;
        }

        final UpgradeConfig config = LuckFishing.getUpgradeConfig();

        if (config == null) {

            return false;
        }

        if (!config.isFishingRod(rod)) {

            Constants.LOG.debug("[luckfishing] '{}' finished fishing but the item in hand ('{}') is not treated as a fishing rod; nothing will spawn.",
                    fisher.getName().getString(), BuiltInRegistries.ITEM.getKey(rod.getItem()));
            return false;
        }

        final ResourceLocation appliedUpgrade = UpgradeConfig.getAppliedUpgrade(rod);

        // 竿上写着某个强化，配置里却没有对应的池子：报错并跳过这次抽取。
        // 不退回未强化池——那会让「配置错了」表现得和「没强化」一模一样。
        if (appliedUpgrade != null && !config.hasUpgrade(appliedUpgrade)) {

            Constants.LOG.error("The rod held by '{}' carries upgrade '{}' but the config has no pool for it; skipping this catch. Fix config/{}.json.",
                    fisher.getName().getString(), appliedUpgrade, Constants.MOD_ID);
            return false;
        }

        final UpgradeConfig.Pick pick = config.pickMob(appliedUpgrade, luckOf(fisher), fisher.level().getRandom());

        if (pick == null) {

            return false;
        }

        // 逐次记录「服务器这边真正读到的鱼竿数据」，默认只进 debug.log；
        // 要在不钓鱼的情况下查看，用服务端命令 /luckfishing diagnose [玩家]。
        // 缺值一律打印成 <无>，避免 SLF4J 把 Java null 印成 "null"、看起来像有个叫 null 的强化。
        final String rawUpgrade = Constants.getRawUpgrade(rod);
        Constants.LOG.debug("[luckfishing] '{}' rod='{}' rawUpgrade='{}' applied='{}' tier={} chance={} picked='{}'",
                fisher.getName().getString(),
                BuiltInRegistries.ITEM.getKey(rod.getItem()),
                rawUpgrade == null ? "<无>" : rawUpgrade,
                appliedUpgrade == null ? "<无>" : appliedUpgrade.toString(),
                config.tierOf(appliedUpgrade),
                String.format(Locale.ROOT, "%.2f", config.chanceOf(appliedUpgrade)),
                pick.getId());

        final ResourceLocation mobId = pick.getId();

        if (mobId == null) {

            // 配置里写坏的 id：按「已判定为实体」处理，免得钓上的东西又变回鱼。
            Constants.LOG.warn("A configured mob id could not be parsed and will be skipped.");
            return true;
        }

        // ENTITY_TYPE 是带默认值（猪）的注册表，get() 对未知 id 不会返回 null，必须走 getOptional。
        final EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(mobId).orElse(null);

        if (entityType == null) {

            Constants.LOG.warn("Mob ID '{}' does not belong to a known entity type. It will be skipped.", mobId);
        } else {

            announce(fisher, entityType);
            spawnNear(fisher, entityType, pick.getNbt());
        }

        // 到这里就判定本次钓上的是实体，即使生成失败也取消鱼获，避免被拦截后变回鱼。
        return true;
    }

    /** 取实体幸运值，没有该属性时为 0。 */
    private static float luckOf(Entity fisher) {

        // 幸运值取自持有鱼竿的实体，所以带幸运修饰的女仆与玩家一致。
        return fisher instanceof LivingEntity living ? (float) living.getAttributeValue(Attributes.LUCK) : 0.0F;
    }

    /** 向所有玩家广播「谁钓上了什么」，名称走语言键本地化。 */
    private static void announce(Entity fisher, EntityType<?> entityType) {

        final MinecraftServer server = fisher.getServer();

        if (server != null) {

            server.getPlayerList().broadcastSystemMessage(Component.translatable("luckfishing.message.hooked", fisher.getDisplayName(), Component.translatable(entityType.getDescriptionId())), false);
        }
    }

    /** 在钓鱼实体附近随机位置生成生物，并按需加载配置的 NBT。 */
    private static void spawnNear(Entity fisher, EntityType<?> entityType, @Nullable String nbt) {

        final Level level = fisher.level();

        if (!(level instanceof ServerLevel serverLevel)) {

            return;
        }

        final CompoundTag userNbt = parseNbt(entityType, nbt);

        final RandomSource random = serverLevel.getRandom();
        final double angle = random.nextDouble() * Math.PI * 2.0D;
        final double distance = 2.0D + random.nextDouble() * 2.0D;
        final double x = fisher.getX() + Math.cos(angle) * distance;
        final double z = fisher.getZ() + Math.sin(angle) * distance;
        final double y = fisher.getY();
        final float yaw = random.nextFloat() * 360.0F;
        final BlockPos spawnPos = BlockPos.containing(x, y, z);

        // 1.21.1 的 spawn 只保留「回调」形态，配置的 NBT 改在回调里、实体入世界前应用。
        entityType.spawn(serverLevel, spawned -> {
            loadNbt(spawned, userNbt);
            spawned.moveTo(x, y, z, yaw, 0.0F);
        }, spawnPos, MobSpawnType.EVENT, false, false);
    }

    /** 把配置的 NBT 应用到刚生成的实体上，保留原 UUID。 */
    private static void loadNbt(Entity entity, @Nullable CompoundTag nbt) {

        if (nbt == null) {

            return;
        }

        final CompoundTag merged = entity.saveWithoutId(new CompoundTag());
        final UUID uuid = entity.getUUID();
        merged.merge(nbt);
        entity.setUUID(uuid);
        entity.load(merged);
    }

    /** 解析配置的 SNBT；为空或格式错误时忽略，避免打断生成。 */
    @Nullable
    private static CompoundTag parseNbt(EntityType<?> entityType, @Nullable String nbt) {

        if (nbt == null || nbt.isBlank()) {

            return null;
        }

        try {

            return TagParser.parseTag(nbt);
        } catch (CommandSyntaxException exception) {

            Constants.LOG.warn("Ignoring invalid NBT '{}' for '{}': {}", nbt, BuiltInRegistries.ENTITY_TYPE.getKey(entityType), exception.getMessage());
            return null;
        }
    }
}
