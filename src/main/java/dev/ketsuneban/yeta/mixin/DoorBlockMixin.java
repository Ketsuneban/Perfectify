package dev.ketsuneban.yeta.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DoorBlock.class)
public abstract class DoorBlockMixin {
    @Inject(method = "getShape", at = @At("RETURN"), cancellable = true)
    private void yeta$outlineBothDoorHalves(BlockState state, BlockGetter level,
            BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> callback) {
        double dy = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? 1.0 : -1.0;
        callback.setReturnValue(Shapes.or(callback.getReturnValue(), callback.getReturnValue().move(0, dy, 0)));
    }
}
