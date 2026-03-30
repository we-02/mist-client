package com.tmbu.tmbuclient.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tmbu.tmbuclient.event.EventBus;
import com.tmbu.tmbuclient.event.events.NameTagRenderEvent;
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

	// AvatarRenderer inherits submitNameTag from EntityRenderer.
	// The EntityRendererMixin already handles suppression for all entity renderers.
	// This mixin is kept as a safety net in case AvatarRenderer overrides it in the future.
	@Inject(method = "submitNameTag", at = @At("HEAD"), cancellable = true, require = 0)
	private void suppressVanillaNameTag(
		AvatarRenderState avatarRenderState,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		CameraRenderState cameraRenderState,
		CallbackInfo ci
	) {
		NameTagRenderEvent event = EventBus.INSTANCE.post(new NameTagRenderEvent());
		if (event.isCancelled()) {
			ci.cancel();
		}
	}
}
