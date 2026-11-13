package dev.ketsuneban.yeta.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BedBlock.class)
public abstract class BedBlockMixin {
    @Inject(method = "getShape", at = @At("RETURN"), cancellable = true)
    private void yeta$outlineWholeBed(BlockState state, BlockGetter level,
            BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> callback) {
        Direction direction = BedBlock.getConnectedDirection(state);
        VoxelShape other = callback.getReturnValue().move(direction.getStepX(), 0, direction.getStepZ());
        callback.setReturnValue(Shapes.or(callback.getReturnValue(), other));
    }
}
