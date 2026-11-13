package dev.ketsuneban.yeta;

import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;

@Mod(Yeta.MOD_ID)
public final class Yeta {
    public static final String MOD_ID = "yeta";

    public Yeta(IEventBus modBus) {
        YetaSounds.register(modBus);
    }
}
