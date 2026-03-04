package com.tmbu.tmbuclient.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tmbu.tmbuclient.TmbuClient;
import com.tmbu.tmbuclient.module.impl.NametagsModule;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {

	@Inject(method = "submitNameTag", at = @At("HEAD"), cancellable = true)
	private void suppressVanillaNameTag(
		AvatarRenderState avatarRenderState,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		CameraRenderState cameraRenderState,
		CallbackInfo ci
	) {
		for (var module : TmbuClient.INSTANCE.getModuleManager().getModules()) {
			if (module instanceof NametagsModule && module.isEnabled()) {
				ci.cancel();
				return;
			}
		}
	}
}