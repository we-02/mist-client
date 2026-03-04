package com.tmbu.tmbuclient.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tmbu.tmbuclient.TmbuClient;
import com.tmbu.tmbuclient.module.ModuleManager;
import com.tmbu.tmbuclient.module.impl.NametagsModule;
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

	@Inject(method = "submitNameTag", at = @At("HEAD"), cancellable = true)
	private void suppressVanillaNameTag(
		EntityRenderState entityRenderState,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		CameraRenderState cameraRenderState,
		CallbackInfo ci
	) {
		ModuleManager manager = TmbuClient.INSTANCE.getModuleManager();
		if (manager == null) return;

		for (var module : manager.getModules()) {
			if (module instanceof NametagsModule && module.isEnabled()) {
				ci.cancel();
				return;
			}
		}
	}
}