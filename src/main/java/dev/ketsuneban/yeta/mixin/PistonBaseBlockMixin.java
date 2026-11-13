package dev.ketsuneban.yeta.mixin;

import dev.ketsuneban.yeta.PistonContainerFeatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonBaseBlock.class)
public abstract class PistonBaseBlockMixin {
    @Inject(method = "isPushable", at = @At("RETURN"), cancellable = true)
    private static void yeta$allowStorageBlocks(BlockState state, Level level, BlockPos pos,
            Direction movement, boolean allowDestroy, Direction pistonDirection,
            CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValue() || !state.hasBlockEntity()
                || !PistonContainerFeatures.isStorage(level, pos)) return;
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(pos)
                || state.getDestroySpeed(level, pos) == -1.0F) return;
        PushReaction reaction = state.getPistonPushReaction();
        if (reaction == PushReaction.NORMAL
                || reaction == PushReaction.PUSH_ONLY && movement == pistonDirection) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "moveBlocks", at = @At("HEAD"))
    private void yeta$captureStorage(Level level, BlockPos pistonPos, Direction facing,
            boolean extending, CallbackInfoReturnable<Boolean> callback) {
        PistonContainerFeatures.capture(level, pistonPos, facing, extending);
    }
}
