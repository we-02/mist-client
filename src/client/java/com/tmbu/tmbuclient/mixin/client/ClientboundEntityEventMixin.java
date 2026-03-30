package com.tmbu.tmbuclient.mixin.client;

import com.tmbu.tmbuclient.event.EventBus;
import com.tmbu.tmbuclient.event.events.TotemPopEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientboundEntityEventMixin {

    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void onEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        if (packet.getEventId() != 35) return; // 35 = USE_TOTEM_OF_UNDYING

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Entity entity = packet.getEntity(mc.level);
        if (entity instanceof Player player) {
            EventBus.INSTANCE.post(new TotemPopEvent(player));
        }
    }
}
