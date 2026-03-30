package com.tmbu.tmbuclient.mixin.client;

import com.tmbu.tmbuclient.module.impl.HandView;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntitySwingMixin {

    @Inject(method = "getHandSwingDuration", at = @At("RETURN"), cancellable = true)
    private void modifySwingDuration(CallbackInfoReturnable<Integer> cir) {
        // Only modify for the local player in first person
        if ((Object) this != Minecraft.getInstance().player) return;
        if (!Minecraft.getInstance().options.getCameraType().isFirstPerson()) return;

        int speed = HandView.getSwingSpeed();
        if (speed != 6) {
            cir.setReturnValue(Math.max(1, speed));
        }
    }
}
