package com.tmbu.tmbuclient.mixin.client;

import com.tmbu.tmbuclient.module.impl.render.HandView;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Inject(method = "getCurrentSwingDuration", at = @At("RETURN"), cancellable = true)
    private void modifySwingDuration(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this != Minecraft.getInstance().player) return;
        if (!Minecraft.getInstance().options.getCameraType().isFirstPerson()) return;

        int custom = HandView.getSwingSpeed();
        if (custom != 6) {
            cir.setReturnValue(custom);
        }
    }
}
