package dev.ketsuneban.yeta.mixin;

import net.minecraft.world.entity.animal.IronGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IronGolem.class)
public abstract class IronGolemMixin {
    @Shadow private int offerFlowerTick;

    @Inject(method = "offerFlower", at = @At("RETURN"))
    private void yeta$longerFlowerGesture(boolean offering, CallbackInfo callback) {
        if (offering) offerFlowerTick = 800;
    }

    @Inject(method = "handleEntityEvent", at = @At("RETURN"))
    private void yeta$syncLongerFlowerGesture(byte event, CallbackInfo callback) {
        if (event == 11) offerFlowerTick = 800;
    }
}
