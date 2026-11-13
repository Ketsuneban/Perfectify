package dev.ketsuneban.yeta.mixin;

import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BeaconRenderer.class)
public abstract class BeaconRendererMixin {
    @Inject(method = "getViewDistance", at = @At("HEAD"), cancellable = true)
    private void yeta$extendedBeaconDistance(CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(1024);
    }
}
