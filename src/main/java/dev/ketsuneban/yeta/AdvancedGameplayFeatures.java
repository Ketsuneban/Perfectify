package dev.ketsuneban.yeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.GlowSquid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.animal.Dolphin;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.animal.TropicalFish;
import net.minecraft.world.entity.animal.Cod;
import net.minecraft.world.entity.animal.Salmon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WitherSkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Yeta.MOD_ID)
public final class AdvancedGameplayFeatures {
    private static final String WATER_BABY = "yetaWaterBaby";
    private static final String WATER_BABY_AGE = "yetaWaterBabyAge";
    private static final String MONUMENT_LOOT = "yetaMonumentLoot";
    private static final String WITHER_DEATH_BLAST = "yetaWitherDeathBlast";
    private static final Map<UUID, ServerBossEvent> ELDER_BARS = new HashMap<>();
    private static int ticks;

    private AdvancedGameplayFeatures() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void ensureWitherSpawn(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || (!event.getPlacedBlock().is(Blocks.WITHER_SKELETON_SKULL)
                && !event.getPlacedBlock().is(Blocks.WITHER_SKELETON_WALL_SKULL))) return;

        WitherSkullBlock.checkSpawn(level, event.getPos());
        if (level.getBlockState(event.getPos()).is(Blocks.WITHER_SKELETON_SKULL)
                || level.getBlockState(event.getPos()).is(Blocks.WITHER_SKELETON_WALL_SKULL)) {
            trySpawnWitherFromSoulBlocks(level, event.getPos());
        }
    }

    private static boolean trySpawnWitherFromSoulBlocks(ServerLevel level, BlockPos placedHead) {
        if (level.getDifficulty() == Difficulty.PEACEFUL
                || placedHead.getY() < level.getMinBuildHeight() + 2) return false;

        for (Direction along : new Direction[]{Direction.EAST, Direction.SOUTH}) {
            for (int placedOffset = -1; placedOffset <= 1; placedOffset++) {
                BlockPos center = placedHead.below().relative(along, -placedOffset);
                if (!matchesWitherStructure(level, center, along)) continue;
                spawnWither(level, center, along);
                return true;
            }
        }
        return false;
    }

    private static boolean matchesWitherStructure(ServerLevel level, BlockPos center, Direction along) {
        if (!isSoulBase(level.getBlockState(center.below()))
                || !level.getBlockState(center.below().relative(along)).isAir()
                || !level.getBlockState(center.below().relative(along.getOpposite())).isAir()) return false;

        for (int offset = -1; offset <= 1; offset++) {
            if (!isSoulBase(level.getBlockState(center.relative(along, offset)))
                    || !isWitherHead(level.getBlockState(center.above().relative(along, offset)))) return false;
        }
        return true;
    }

    private static boolean isSoulBase(BlockState state) {
        return state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL);
    }

    private static boolean isWitherHead(BlockState state) {
        return state.is(Blocks.WITHER_SKELETON_SKULL) || state.is(Blocks.WITHER_SKELETON_WALL_SKULL);
    }

    private static void spawnWither(ServerLevel level, BlockPos center, Direction along) {
        WitherBoss wither = EntityType.WITHER.create(level);
        if (wither == null) return;

        ArrayList<BlockPos> structure = new ArrayList<>();
        structure.add(center.below());
        for (int offset = -1; offset <= 1; offset++) {
            structure.add(center.relative(along, offset));
            structure.add(center.above().relative(along, offset));
        }
        for (BlockPos pos : structure) {
            BlockState state = level.getBlockState(pos);
            level.levelEvent(2001, pos, Block.getId(state));
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }

        BlockPos foot = center.below();
        float yaw = along.getAxis() == Direction.Axis.X ? 0.0F : 90.0F;
        wither.moveTo(foot.getX() + 0.5, foot.getY() + 0.55, foot.getZ() + 0.5, yaw, 0.0F);
        wither.yBodyRot = yaw;
        wither.makeInvulnerable();
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                wither.getBoundingBox().inflate(50.0))) {
            CriteriaTriggers.SUMMONED_ENTITY.trigger(player, wither);
        }
        level.addFreshEntity(wither);
        for (BlockPos pos : structure) level.updateNeighborsAt(pos, Blocks.AIR);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof WitherBoss wither
                && wither.level() instanceof ServerLevel level
                && !wither.getPersistentData().getBoolean(WITHER_DEATH_BLAST)) {
            wither.getPersistentData().putBoolean(WITHER_DEATH_BLAST, true);
            level.explode(wither, wither.getX(), wither.getY() + 1.0, wither.getZ(),
                10.0F, false, net.minecraft.world.level.Level.ExplosionInteraction.MOB);
        }
        if (event.getEntity() instanceof ElderGuardian guardian) {
            ServerBossEvent bar = ELDER_BARS.remove(guardian.getUUID());
            if (bar != null) bar.removeAllPlayers();
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.animal.Sheep sheep)
                || !sheep.hasCustomName() || !"jeb_".equals(sheep.getName().getString())) return;
        for (ItemEntity drop : event.getDrops()) {
            if (drop.getItem().is(ItemTags.WOOL)) {
                drop.setItem(new ItemStack(Items.WHITE_WOOL, drop.getItem().getCount()));
            }
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity entity = event.getEntity();
        if (entity instanceof AbstractArrow arrow && arrow.getOwner() instanceof AbstractSkeleton skeleton) {
            LivingEntity target = skeleton.getTarget();
            if (target != null) {
                double distance = skeleton.distanceTo(target);
                Vec3 predicted = target.position().add(target.getDeltaMovement().scale(Math.min(12.0, distance / 1.6)));
                Vec3 aim = predicted.add(0.0, target.getBbHeight() * 0.34, 0.0).subtract(arrow.position());
                arrow.shoot(aim.x, aim.y + Math.sqrt(aim.x * aim.x + aim.z * aim.z) * 0.18,
                    aim.z, 1.75F, Math.max(1.0F, 8.0F - level.getDifficulty().getId() * 2.0F));
            }
        }
        if (entity instanceof ElderGuardian guardian) {
            var damage = guardian.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damage != null) damage.setBaseValue(14.0);
            ELDER_BARS.computeIfAbsent(guardian.getUUID(), id -> new ServerBossEvent(
                Component.translatable("entity.minecraft.elder_guardian"),
                BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS));
        }
        if (isWaterBabyCandidate(entity) && !entity.getPersistentData().contains(WATER_BABY)) {
            boolean baby = level.random.nextInt(7) == 0;
            entity.getPersistentData().putBoolean(WATER_BABY, baby);
            entity.getPersistentData().putInt(WATER_BABY_AGE, 0);
            setWaterBabyScale((LivingEntity)entity, baby);
        } else if (isWaterBabyCandidate(entity)) {
            setWaterBabyScale((LivingEntity)entity, entity.getPersistentData().getBoolean(WATER_BABY));
        }
    }

    private static boolean isWaterBabyCandidate(Entity entity) {
        return entity instanceof Dolphin || entity instanceof Squid || entity instanceof GlowSquid
            || entity instanceof Salmon || entity instanceof Cod || entity instanceof TropicalFish;
    }

    private static void setWaterBabyScale(LivingEntity entity, boolean baby) {
        var scale = entity.getAttribute(Attributes.SCALE);
        if (scale != null) scale.setBaseValue(baby ? 0.52 : 1.0);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void dyeShulker(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getTarget() instanceof Shulker shulker)
                || !(event.getItemStack().getItem() instanceof DyeItem dye)) return;
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        event.setCanceled(true);
        if (!event.getLevel().isClientSide()) {
            DyeColor color = dye.getDyeColor();
            shulker.setVariant(Optional.of(color));
            if (!event.getEntity().getAbilities().instabuild) event.getItemStack().shrink(1);
        }
    }

    @SubscribeEvent
    public static void impalingForWetTargets(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof ThrownTrident trident)) return;
        LivingEntity target = event.getEntity();
        BlockPos pos = target.blockPosition();
        boolean wet = target.isInWaterOrRain() || target.level().getBlockState(pos).is(Blocks.WATER_CAULDRON);
        if (!wet || !(target.level() instanceof ServerLevel level)) return;
        var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int impaling = EnchantmentHelper.getItemEnchantmentLevel(
            enchantments.getOrThrow(Enchantments.IMPALING), trident.getWeaponItem());
        if (impaling > 0) event.setAmount(event.getAmount() + 2.5F * impaling);
    }

    @SubscribeEvent
    public static void entityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (isWaterBabyCandidate(entity) && entity.getPersistentData().getBoolean(WATER_BABY)) {
            int age = entity.getPersistentData().getInt(WATER_BABY_AGE) + 1;
            entity.getPersistentData().putInt(WATER_BABY_AGE, age);
            if (age >= 24000) {
                entity.getPersistentData().putBoolean(WATER_BABY, false);
                setWaterBabyScale((LivingEntity)entity, false);
            }
        }
        if (entity instanceof Projectile) {
            bounceAtWorldBorder(entity);
        }
    }

    private static void bounceAtWorldBorder(Entity entity) {
        var border = entity.level().getWorldBorder();
        Vec3 movement = entity.getDeltaMovement();
        double nextX = entity.getX() + movement.x;
        double nextZ = entity.getZ() + movement.z;
        double margin = Math.max(0.1, entity.getBbWidth() * 0.5);
        boolean bounceX = movement.x > 0.0 && nextX >= border.getMaxX() - margin
            || movement.x < 0.0 && nextX <= border.getMinX() + margin;
        boolean bounceZ = movement.z > 0.0 && nextZ >= border.getMaxZ() - margin
            || movement.z < 0.0 && nextZ <= border.getMinZ() + margin;
        if (bounceX || bounceZ) {
            entity.setPos(
                Math.max(border.getMinX() + margin, Math.min(border.getMaxX() - margin, entity.getX())),
                entity.getY(),
                Math.max(border.getMinZ() + margin, Math.min(border.getMaxZ() - margin, entity.getZ())));
            entity.setDeltaMovement(bounceX ? -movement.x * 0.8 : movement.x,
                movement.y * 0.9, bounceZ ? -movement.z * 0.8 : movement.z);
            entity.hurtMarked = true;
        }
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        ticks++;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                AABB area = player.getBoundingBox().inflate(64.0);
                for (ElderGuardian guardian : level.getEntitiesOfClass(ElderGuardian.class, area)) {
                    updateElderGuardian(level, guardian);
                }
                if (ticks % 10 == 0) {
                    for (AbstractMinecart minecart : level.getEntitiesOfClass(AbstractMinecart.class,
                            player.getBoundingBox().inflate(24.0), cart -> !cart.isVehicle())) {
                        for (IronGolem golem : level.getEntitiesOfClass(IronGolem.class,
                                minecart.getBoundingBox().inflate(0.35), g -> !g.isPassenger())) {
                            golem.startRiding(minecart);
                            break;
                        }
                    }
                }
            }
            if (ticks % 20 == 0) cleanBossBars(level);
            if (ticks % 10 == 0) updatePoweredWithers(level);
        }
    }

    private static void updateElderGuardian(ServerLevel level, ElderGuardian guardian) {
        ServerBossEvent bar = ELDER_BARS.computeIfAbsent(guardian.getUUID(), id -> new ServerBossEvent(
            guardian.getDisplayName(), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS));
        bar.setProgress(guardian.getHealth() / guardian.getMaxHealth());
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(guardian) <= 64.0 * 64.0) bar.addPlayer(player);
            else bar.removePlayer(player);
            if (player.distanceToSqr(guardian) <= 50.0 * 50.0 && ticks % 20 == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 60, 2, true, true));
            }
        }
        LivingEntity target = guardian.getActiveAttackTarget();
        if (target != null && target.isAlive()) {
            Vec3 pull = guardian.position().subtract(target.position());
            if (pull.lengthSqr() > 1.0) target.push(pull.x * 0.012, pull.y * 0.006, pull.z * 0.012);
            if (guardian.distanceToSqr(target) < 9.0 && ticks % 20 == 0) guardian.doHurtTarget(target);
        }
        if (!guardian.getPersistentData().getBoolean(MONUMENT_LOOT)) {
            guardian.getPersistentData().putBoolean(MONUMENT_LOOT, true);
            BlockPos center = guardian.blockPosition();
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-28, -12, -28), center.offset(28, 12, 28))) {
                if (!level.getBlockState(pos).is(Blocks.GOLD_BLOCK)) continue;
                int roll = level.random.nextInt(10);
                level.setBlock(pos, (roll == 8 ? Blocks.EMERALD_BLOCK : roll == 9
                    ? Blocks.DIAMOND_BLOCK : Blocks.GOLD_BLOCK).defaultBlockState(), 3);
            }
        }
    }

    private static void cleanBossBars(ServerLevel level) {
        for (var entry : new ArrayList<>(ELDER_BARS.entrySet())) {
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof ElderGuardian guardian) || !guardian.isAlive()) {
                entry.getValue().removeAllPlayers();
                ELDER_BARS.remove(entry.getKey());
            }
        }
    }

    private static void updatePoweredWithers(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            for (WitherBoss wither : level.getEntitiesOfClass(WitherBoss.class,
                    player.getBoundingBox().inflate(64.0), WitherBoss::isPowered)) {
                var damage = wither.getAttribute(Attributes.ATTACK_DAMAGE);
                if (damage != null) damage.setBaseValue(12.0);
                if (ticks % 40 == 0 && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                    for (BlockPos pos : BlockPos.betweenClosed(wither.blockPosition().offset(-2, -1, -2),
                            wither.blockPosition().offset(2, 3, 2))) {
                        var state = level.getBlockState(pos);
                        if (!state.isAir() && state.getDestroySpeed(level, pos) >= 0.0F
                                && state.getDestroySpeed(level, pos) <= 50.0F) {
                            level.destroyBlock(pos, true, wither);
                        }
                    }
                }
            }
        }
    }
}
