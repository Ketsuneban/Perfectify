package dev.ketsuneban.yeta.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodeEvaluatorMixin {
    @Inject(method = "getPathTypeFromState", at = @At("RETURN"), cancellable = true)
    private static void yeta$flatBlocksAreWalkable(BlockGetter level, BlockPos pos,
            CallbackInfoReturnable<PathType> callback) {
        var block = level.getBlockState(pos).getBlock();
        if (block instanceof BaseRailBlock || block instanceof CarpetBlock || block instanceof SnowLayerBlock) {
            callback.setReturnValue(PathType.OPEN);
        }
    }
}
