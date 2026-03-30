package com.tmbu.tmbuclient.mixin.client;

import com.tmbu.tmbuclient.event.EventBus;
import com.tmbu.tmbuclient.event.events.AttackEntityEvent;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class LocalPlayerAttackMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void onAttackPre(Player player, Entity target, CallbackInfo ci) {
        EventBus.INSTANCE.post(new AttackEntityEvent(target));
    }
}
