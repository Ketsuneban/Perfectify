package dev.ketsuneban.yeta.mixin;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BedBlock.class)
public abstract class BedRespawnMixin {
    @Inject(method = "findStandUpPosition", at = @At("RETURN"), cancellable = true)
    private static void yeta$allowEndPortalOverBed(EntityType<?> type, CollisionGetter level,
            BlockPos pos, Direction direction, float angle,
            CallbackInfoReturnable<Optional<Vec3>> callback) {
        if (callback.getReturnValue().isPresent()) return;
        BlockState state = level.getBlockState(pos);
        BlockPos other = state.getBlock() instanceof BedBlock
            ? pos.relative(BedBlock.getConnectedDirection(state)) : pos.relative(direction);
        if (level.getBlockState(pos.above()).is(Blocks.END_PORTAL)
                || level.getBlockState(other.above()).is(Blocks.END_PORTAL)) {
            callback.setReturnValue(Optional.of(Vec3.atBottomCenterOf(pos.above())));
        }
    }
}
