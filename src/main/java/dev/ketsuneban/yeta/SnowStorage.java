package dev.ketsuneban.yeta;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

public final class SnowStorage extends SavedData {
    private final Map<Long, BlockState> coveredPlants = new HashMap<>();
    private static final Factory<SnowStorage> FACTORY = new Factory<>(SnowStorage::new, SnowStorage::read);

    public static SnowStorage of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "yeta_snow_covered_plants");
    }

    public void cover(BlockPos pos, BlockState plant) {
        coveredPlants.putIfAbsent(pos.asLong(), plant);
        setDirty();
    }

    public BlockState take(BlockPos pos) {
        BlockState state = coveredPlants.remove(pos.asLong());
        if (state != null) setDirty();
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag output, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (var entry : coveredPlants.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("pos", entry.getKey());
            tag.put("state", NbtUtils.writeBlockState(entry.getValue()));
            list.add(tag);
        }
        output.put("plants", list);
        return output;
    }

    private static SnowStorage read(CompoundTag input, HolderLookup.Provider registries) {
        SnowStorage storage = new SnowStorage();
        var blocks = registries.lookupOrThrow(Registries.BLOCK);
        ListTag list = input.getList("plants", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            storage.coveredPlants.put(tag.getLong("pos"),
                NbtUtils.readBlockState(blocks, tag.getCompound("state")));
        }
        return storage;
    }
}
