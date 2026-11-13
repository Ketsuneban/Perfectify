package dev.ketsuneban.yeta;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;

public final class ClientCauldronColors {
    private static final Map<Long, Integer> COLORS = new HashMap<>();

    private ClientCauldronColors() {}

    public static void update(BlockPos pos, int color) {
        if (color < 0) COLORS.remove(pos.asLong());
        else COLORS.put(pos.asLong(), color & 0xffffff);
    }

    public static Integer get(BlockPos pos) {
        return COLORS.get(pos.asLong());
    }

    public static void clear() {
        COLORS.clear();
    }
}
