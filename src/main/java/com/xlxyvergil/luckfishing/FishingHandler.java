package com.xlxyvergil.luckfishing;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.xlxyvergil.luckfishing.config.UpgradeConfig;
import java.util.UUID;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = Constants.MOD_ID)
public class FishingHandler {

    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {

        // 判定钓上实体时清空鱼获。
        if (trySpawn(event.getEntity(), event.getEntity().getMainHandItem())) {

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

        // 重新读取配置，改配置无需重启游戏。
        config.reload();

        if (!config.isFishingRod(rod)) {

            return false;
        }

        final UpgradeConfig.Pick pick = config.pickMob(UpgradeConfig.getAppliedUpgrade(rod), luckOf(fisher), fisher.level().getRandom());

        if (pick == null) {

            return false;
        }

        final ResourceLocation mobId = pick.getId();
        final EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(mobId);

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
    private static void spawnNear(Entity fisher, EntityType<?> entityType, String nbt) {

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

        // EntityType#spawn 传入的 tag 到不了 ForgeCaps 等字段，改在回调里、实体入世界前应用。
        entityType.spawn(serverLevel, null, spawned -> {
            loadNbt(spawned, userNbt);
            spawned.moveTo(x, y, z, yaw, 0.0F);
        }, spawnPos, MobSpawnType.EVENT, false, false);
    }

    /** 把配置的 NBT 应用到刚生成的实体上，保留原 UUID。 */
    private static void loadNbt(Entity entity, CompoundTag nbt) {

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
    private static CompoundTag parseNbt(EntityType<?> entityType, String nbt) {

        if (nbt == null || nbt.isBlank()) {

            return null;
        }

        try {

            return TagParser.parseTag(nbt);
        } catch (CommandSyntaxException exception) {

            Constants.LOG.warn("Ignoring invalid NBT '{}' for '{}': {}", nbt, ForgeRegistries.ENTITY_TYPES.getKey(entityType), exception.getMessage());
            return null;
        }
    }
}
