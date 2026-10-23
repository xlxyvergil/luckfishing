package com.xlxyvergil.luckfishing.compat.jei;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/** JEI 页面上的单个可钓实体，属于某个强化等级；weight 是该池内的权重，chance 是该等级的出怪概率。 */
@OnlyIn(Dist.CLIENT)
public class FishingLootRecipe {

    private final ResourceLocation entityId;
    private final EntityType<?> entityType;
    private final double weight;
    private final int tier;
    private final double chance;
    private final ResourceLocation upgradeItemId;

    @Nullable
    private LivingEntity entity;

    public FishingLootRecipe(
        ResourceLocation entityId,
        EntityType<?> entityType,
        double weight,
        int tier,
        double chance,
        ResourceLocation upgradeItemId
    ) {
        this.entityId = entityId;
        this.entityType = entityType;
        this.weight = weight;
        this.tier = tier;
        this.chance = chance;
        this.upgradeItemId = upgradeItemId;
    }

    public ResourceLocation getEntityId() {
        return this.entityId;
    }

    public EntityType<?> getEntityType() {
        return this.entityType;
    }

    public double getWeight() {
        return this.weight;
    }

    public int getTier() {
        return this.tier;
    }

    public double getChance() {
        return this.chance;
    }

    public ResourceLocation getUpgradeItemId() {
        return this.upgradeItemId;
    }

    /** 用于渲染的实体，尚未加载世界时为 null。 */
    @Nullable
    public LivingEntity getEntity() {
        if (this.entity == null) {
            final Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return null;
            }
            final Entity created = this.entityType.create(mc.level);
            if (created instanceof LivingEntity living) {
                this.entity = living;
            }
        }
        return this.entity;
    }
}
