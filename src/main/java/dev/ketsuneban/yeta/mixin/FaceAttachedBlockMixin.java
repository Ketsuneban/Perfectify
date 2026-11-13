package dev.ketsuneban.yeta.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FaceAttachedHorizontalDirectionalBlock.class)
public abstract class FaceAttachedBlockMixin {
    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void yeta$keepControlsOnPistons(BlockState state, LevelReader level,
            BlockPos pos, CallbackInfoReturnable<Boolean> callback) {
        Object self = this;
        if (!(self instanceof LeverBlock) && !(self instanceof ButtonBlock)) return;
        AttachFace face = state.getValue(FaceAttachedHorizontalDirectionalBlock.FACE);
        Direction connected = face == AttachFace.CEILING ? Direction.DOWN
            : face == AttachFace.FLOOR ? Direction.UP
            : state.getValue(FaceAttachedHorizontalDirectionalBlock.FACING);
        Direction supportDirection = connected.getOpposite();
        BlockState support = level.getBlockState(pos.relative(supportDirection));
        if (support.is(Blocks.PISTON) || support.is(Blocks.STICKY_PISTON)
                || support.is(Blocks.PISTON_HEAD) || support.is(Blocks.MOVING_PISTON)) {
            callback.setReturnValue(true);
        }
    }
}
