package dev.ketsuneban.yeta.mixin;

import dev.ketsuneban.yeta.ArmorStandFeatures;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ArmorStand.class)
public abstract class ArmorStandMixin {
    @Inject(method = "interactAt", at = @At("HEAD"), cancellable = true)
    private void yeta$useLeftHand(Player player, Vec3 hit, InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> callback) {
        ArmorStand stand = (ArmorStand)(Object)this;
        if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) return;
        if (!stand.level().isClientSide()) {
            ItemStack held = player.getMainHandItem().copy();
            ItemStack stored = stand.getItemBySlot(EquipmentSlot.OFFHAND).copy();
            if (held.isEmpty() && stored.isEmpty()) {
                ArmorStandFeatures.cyclePose(stand);
            } else {
                stand.setShowArms(true);
                stand.setItemSlot(EquipmentSlot.OFFHAND, held);
                player.setItemInHand(InteractionHand.MAIN_HAND, stored);
            }
        }
        callback.setReturnValue(InteractionResult.sidedSuccess(stand.level().isClientSide()));
    }
}
