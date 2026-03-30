package com.tmbu.tmbuclient.mixin.client;

import com.tmbu.tmbuclient.event.EventBus;
import com.tmbu.tmbuclient.event.events.PreMotionEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class MixinLocalPlayer {

	@Unique
	private boolean tmbuclient$inPreMotion = false;

	@Inject(method = "sendPosition", at = @At("HEAD"))
	private void onPreMotion(CallbackInfo ci) {
		if (tmbuclient$inPreMotion) return; // prevent re-entrancy
		tmbuclient$inPreMotion = true;
		try {
			Minecraft client = Minecraft.getInstance();
			if (client.player == null) return;
			EventBus.INSTANCE.post(new PreMotionEvent(client));
		} finally {
			tmbuclient$inPreMotion = false;
		}
	}
}
