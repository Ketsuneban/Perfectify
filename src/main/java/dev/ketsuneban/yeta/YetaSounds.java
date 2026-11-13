package dev.ketsuneban.yeta;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class YetaSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS =
        DeferredRegister.create(Registries.SOUND_EVENT, Yeta.MOD_ID);

    private static final DeferredHolder<SoundEvent, SoundEvent> SPAWNER_BREAK = register("spawner_break");
    private static final DeferredHolder<SoundEvent, SoundEvent> SPAWNER_STEP = register("spawner_step");
    private static final DeferredHolder<SoundEvent, SoundEvent> SPAWNER_PLACE = register("spawner_place");
    private static final DeferredHolder<SoundEvent, SoundEvent> SPAWNER_HIT = register("spawner_hit");
    private static final DeferredHolder<SoundEvent, SoundEvent> SPAWNER_FALL = register("spawner_fall");
    private static SoundType spawnerType;

    private YetaSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, SoundEvent::createVariableRangeEvent);
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }

    public static SoundType spawnerType() {
        if (spawnerType == null) {
            spawnerType = new SoundType(1.0F, 1.0F, SPAWNER_BREAK.get(), SPAWNER_STEP.get(),
                SPAWNER_PLACE.get(), SPAWNER_HIT.get(), SPAWNER_FALL.get());
        }
        return spawnerType;
    }
}
