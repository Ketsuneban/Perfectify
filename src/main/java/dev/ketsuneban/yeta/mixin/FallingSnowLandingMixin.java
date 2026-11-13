package dev.ketsuneban.yeta.mixin;

import dev.ketsuneban.yeta.SnowFeatures;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FallingBlockEntity.class)
public abstract class FallingSnowLandingMixin {
    @Shadow private BlockState blockState;

    @Inject(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/state/BlockState;canBeReplaced(Lnet/minecraft/world/item/context/BlockPlaceContext;)Z",
        shift = At.Shift.BEFORE), cancellable = true)
    private void yeta$mergeFallingSnow(CallbackInfo callback) {
        FallingBlockEntity self = (FallingBlockEntity)(Object)this;
        if (self.level().isClientSide || !blockState.is(Blocks.SNOW) || !self.onGround()) return;
        BlockPos pos = self.blockPosition();
        BlockState existing = self.level().getBlockState(pos);
        if (!existing.is(Blocks.SNOW)) return;
        int merged = Math.min(SnowFeatures.MAX_SNOW_LAYERS,
            existing.getValue(SnowLayerBlock.LAYERS) + blockState.getValue(SnowLayerBlock.LAYERS));
        self.level().setBlock(pos, existing.setValue(SnowLayerBlock.LAYERS, merged), 3);
        self.discard();
        callback.cancel();
    }
}
