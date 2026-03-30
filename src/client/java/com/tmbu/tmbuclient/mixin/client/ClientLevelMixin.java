package com.tmbu.tmbuclient.mixin.client;

import com.tmbu.tmbuclient.module.impl.Ambience;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.ClientLevelData.class)
public class ClientLevelMixin {

    @Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
    private void overrideDayTime(CallbackInfoReturnable<Long> cir) {
        if (Ambience.hasTimeOverride()) {
            cir.setReturnValue(Ambience.getTimeOverride());
        }
    }
}
