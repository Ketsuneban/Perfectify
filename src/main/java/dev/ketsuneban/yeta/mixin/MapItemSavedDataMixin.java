package dev.ketsuneban.yeta.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(MapItemSavedData.class)
public abstract class MapItemSavedDataMixin {
    @ModifyArgs(
        method = "addDecoration",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/saveddata/maps/MapDecoration;<init>(Lnet/minecraft/core/Holder;BBBLjava/util/Optional;)V")
    )
    private void yeta$pointOffMapMarkerTowardMap(Args args) {
        Holder<MapDecorationType> type = args.get(0);
        if (!type.is(MapDecorationTypes.PLAYER_OFF_MAP) && !type.is(MapDecorationTypes.PLAYER_OFF_LIMITS)) return;
        byte x = args.get(1);
        byte y = args.get(2);
        double angle = Math.atan2(-x, -y);
        byte rotation = (byte)(((int)Math.floor(angle * 16.0 / (Math.PI * 2.0) + 0.5)) & 15);
        args.set(3, rotation);
    }
}
