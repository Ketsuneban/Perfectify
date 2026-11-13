package dev.ketsuneban.yeta;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

@EventBusSubscriber(modid = Yeta.MOD_ID)
public final class BedrockCombatFeatures {
    private static final String SKELETON_WAVE = "yetaBedrockSkeletonWave";
    private static final String HEALTH_INITIALIZED = "yetaBedrockHealthInitialized";

    private BedrockCombatFeatures() {}

    @SubscribeEvent
    public static void onBossJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof WitherBoss wither)) return;
        double health = switch (level.getDifficulty()) {
            case HARD -> 600.0;
            case NORMAL -> 450.0;
            default -> 300.0;
        };
        float currentHealth = wither.getHealth();
        var attribute = wither.getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null) attribute.setBaseValue(health);
        if (wither.getPersistentData().getBoolean(HEALTH_INITIALIZED)) {
            wither.setHealth(Math.min(currentHealth, (float)health));
        } else {
            wither.getPersistentData().putBoolean(HEALTH_INITIALIZED, true);
            wither.setHealth((float)health);
        }
    }

    @SubscribeEvent
    public static void onBossDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof WitherBoss wither
                && wither.level() instanceof ServerLevel level
                && level.getDifficulty() != Difficulty.EASY
                && !wither.getPersistentData().getBoolean(SKELETON_WAVE)
                && wither.getHealth() - event.getAmount() <= wither.getMaxHealth() * 0.5F) {
            wither.getPersistentData().putBoolean(SKELETON_WAVE, true);
            int count = level.getDifficulty() == Difficulty.HARD ? 4 : 3;
            for (int i = 0; i < count; i++) {
                WitherSkeleton skeleton = EntityType.WITHER_SKELETON.create(level);
                if (skeleton == null) continue;
                double angle = Math.PI * 2.0 * i / count;
                skeleton.moveTo(wither.getX() + Math.cos(angle) * 3.0,
                    wither.getY(), wither.getZ() + Math.sin(angle) * 3.0,
                    wither.getYRot(), 0.0F);
                level.addFreshEntity(skeleton);
            }
        }

        var attacker = event.getSource().getEntity();
        if (!(attacker instanceof WitherBoss) && !(attacker instanceof EnderDragon)) return;
        Difficulty difficulty = event.getEntity().level().getDifficulty();
        float multiplier = difficulty == Difficulty.HARD ? 1.75F
            : difficulty == Difficulty.NORMAL ? 1.35F : 1.0F;
        event.setAmount(event.getAmount() * multiplier);
    }

    @SubscribeEvent
    public static void onPiglinKilled(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ZombifiedPiglin piglin)
                || !(event.getSource().getEntity() instanceof Player player)
                || !(piglin.level() instanceof ServerLevel level)) return;
        AABB area = piglin.getBoundingBox().inflate(32.0, 16.0, 32.0);
        for (ZombifiedPiglin nearby : level.getEntitiesOfClass(ZombifiedPiglin.class, area,
                other -> other.isAlive() && other != piglin)) {
            nearby.setTarget(player);
            nearby.setPersistentAngerTarget(player.getUUID());
            nearby.startPersistentAngerTimer();
        }
    }
}
