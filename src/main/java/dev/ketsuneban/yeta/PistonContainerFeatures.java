package dev.ketsuneban.yeta;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Yeta.MOD_ID)
public final class PistonContainerFeatures {
    private record Pending(CompoundTag tag, long expiresAt) {}
    private static final Map<ResourceKey<Level>, Map<Long, Pending>> PENDING = new HashMap<>();

    private PistonContainerFeatures() {}

    public static boolean isStorage(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof Container;
    }

    public static void capture(Level level, BlockPos pistonPos, Direction facing, boolean extending) {
        if (!(level instanceof ServerLevel server)) return;
        PistonStructureResolver resolver = new PistonStructureResolver(level, pistonPos, facing, extending);
        if (!resolver.resolve()) return;
        Direction movement = extending ? facing : facing.getOpposite();
        Map<Long, Pending> pending = PENDING.computeIfAbsent(level.dimension(), key -> new HashMap<>());
        for (BlockPos source : resolver.getToPush()) {
            BlockEntity entity = level.getBlockEntity(source);
            if (!(entity instanceof Container)) continue;
            BlockPos destination = source.relative(movement);
            pending.put(destination.asLong(), new Pending(
                entity.saveWithFullMetadata(level.registryAccess()), level.getGameTime() + 40));
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            Map<Long, Pending> pending = PENDING.get(level.dimension());
            if (pending == null) continue;
            Iterator<Map.Entry<Long, Pending>> iterator = pending.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                BlockPos pos = BlockPos.of(entry.getKey());
                if (level.getBlockState(pos).is(Blocks.MOVING_PISTON)) continue;
                BlockEntity entity = level.getBlockEntity(pos);
                if (entity instanceof Container) {
                    entity.loadWithComponents(entry.getValue().tag(), level.registryAccess());
                    entity.setChanged();
                    level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
                    iterator.remove();
                } else if (level.getGameTime() >= entry.getValue().expiresAt()) {
                    iterator.remove();
                }
            }
            if (pending.isEmpty()) PENDING.remove(level.dimension());
        }
    }
}
