package com.tmbu.tmbuclient.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tmbu.tmbuclient.event.EventBus;
import com.tmbu.tmbuclient.event.events.NameTagRenderEvent;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {

	/**
	 * Cancel the vanilla nametag by both cancelling the method AND nulling the nameTag field.
	 * The null check is a fallback in case ci.cancel() doesn't prevent all code paths.
	 */
	@Inject(method = "submitNameTag", at = @At("HEAD"), cancellable = true)
	private void suppressVanillaNameTag(
		EntityRenderState entityRenderState,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		CameraRenderState cameraRenderState,
		CallbackInfo ci
	) {
		NameTagRenderEvent event = EventBus.INSTANCE.post(new NameTagRenderEvent());
		if (event.isCancelled()) {
			entityRenderState.nameTag = null; // Null it so even if cancel fails, the method returns early
			ci.cancel();
		}
	}

	/**
	 * Also intercept the render state extraction to prevent the nameTag from being set at all.
	 */
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void clearNameTagOnExtract(
		net.minecraft.world.entity.Entity entity,
		EntityRenderState state,
		float partialTick,
		CallbackInfo ci
	) {
		NameTagRenderEvent event = EventBus.INSTANCE.post(new NameTagRenderEvent());
		if (event.isCancelled()) {
			state.nameTag = null;
			state.nameTagAttachment = null;
		}
	}
}
