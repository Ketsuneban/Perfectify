package dev.ketsuneban.yeta.mixin;

import dev.ketsuneban.yeta.ClientCauldronColors;
import dev.ketsuneban.yeta.YetaClient;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockColors.class)
public abstract class BlockColorsMixin {
    @Inject(method = "getColor(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;I)I",
        at = @At("RETURN"), cancellable = true)
    private void yeta$weatherAndCauldronColors(BlockState state, BlockAndTintGetter level,
            BlockPos pos, int tintIndex, CallbackInfoReturnable<Integer> callback) {
        if (pos != null && state.is(Blocks.WATER_CAULDRON)) {
            Integer color = ClientCauldronColors.get(pos);
            if (color != null) {
                callback.setReturnValue(color);
                return;
            }
        }
        if (!(state.getBlock() instanceof LeavesBlock)) return;
        int base = callback.getReturnValue();
        if (base < 0) return;
        float amount = YetaClient.snowWhiteness();
        int r = (base >> 16) & 255;
        int g = (base >> 8) & 255;
        int b = base & 255;
        int snow = 238;
        r = Math.round(r + (snow - r) * amount);
        g = Math.round(g + (snow - g) * amount);
        b = Math.round(b + (snow - b) * amount);
        callback.setReturnValue((r << 16) | (g << 8) | b);
    }
}
