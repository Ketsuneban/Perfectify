package dev.ketsuneban.yeta.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Boat.class)
public abstract class BoatMixin {
    @Inject(method = "hasEnoughSpaceFor", at = @At("HEAD"), cancellable = true)
    private void yeta$letHorsesBoard(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (entity instanceof AbstractHorse) callback.setReturnValue(true);
    }
}
