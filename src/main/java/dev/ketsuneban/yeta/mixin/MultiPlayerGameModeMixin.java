package dev.ketsuneban.yeta.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private BlockPos destroyBlockPos;
    @Shadow private ItemStack destroyingItem;

    @Inject(method = "sameDestroyTarget", at = @At("HEAD"), cancellable = true)
    private void yeta$keepProgressWithSuitableTool(BlockPos pos,
                                                   CallbackInfoReturnable<Boolean> callback) {
        if (!pos.equals(destroyBlockPos) || minecraft.level == null) return;
        ItemStack current = minecraft.player.getMainHandItem();
        BlockState state = minecraft.level.getBlockState(pos);
        boolean oldSuitable = destroyingItem.isCorrectToolForDrops(state)
            || destroyingItem.getDestroySpeed(state) > 1.0F;
        boolean currentSuitable = current.isCorrectToolForDrops(state)
            || current.getDestroySpeed(state) > 1.0F;
        if (oldSuitable && currentSuitable) callback.setReturnValue(true);
    }
}
