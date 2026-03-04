package com.tmbu.tmbuclient.mixin.client;

import com.tmbu.tmbuclient.TmbuClient;
import com.tmbu.tmbuclient.module.impl.AutoCrystal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class MixinLocalPlayer {

	@Inject(method = "sendPosition", at = @At("HEAD"))
	private void onPreMotion(CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return;

		for (var module : TmbuClient.INSTANCE.getModuleManager().getModules()) {
			if (module instanceof AutoCrystal ac && ac.isEnabled()) {
				ac.onPreMotion(client);
			}
		}
	}
}
