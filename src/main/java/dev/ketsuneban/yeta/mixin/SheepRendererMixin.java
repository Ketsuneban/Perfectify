package dev.ketsuneban.yeta.mixin;

import dev.ketsuneban.yeta.client.ShearedSheepTextures;
import net.minecraft.client.renderer.entity.SheepRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SheepRenderer.class)
public abstract class SheepRendererMixin {
    @Inject(method = "getTextureLocation", at = @At("RETURN"), cancellable = true)
    private void studio$recolorAfterShearing(
            Sheep sheep, CallbackInfoReturnable<ResourceLocation> cir) {
        if (sheep.isSheared()) {
            boolean rainbow = sheep.hasCustomName() && "jeb_".equals(sheep.getName().getString());
            DyeColor color = rainbow ? DyeColor.byId((sheep.tickCount / 25) % 16) : sheep.getColor();
            cir.setReturnValue(ShearedSheepTextures.forColor(color));
        }
    }
}
