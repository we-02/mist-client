package com.tmbu.tmbuclient.mixin.client;

import com.tmbu.tmbuclient.module.impl.render.Ambience;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public class SkyRendererMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void overrideSkyColor(ClientLevel level, float partialTick, Camera camera,
                                  SkyRenderState state, CallbackInfo ci) {
        if (Ambience.hasSkyOverride()) {
            state.skyColor = Ambience.getSkyColor();
        }
    }
}
