package com.tmbu.tmbuclient.mixin.client;

import com.tmbu.tmbuclient.command.CommandManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ChatMixin {

    /**
     * Intercept outgoing chat messages. If the message starts with the
     * command prefix, dispatch it to our command system and cancel the packet.
     */
    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void onSendChat(String message, CallbackInfo ci) {
        if (CommandManager.INSTANCE.handleMessage(message)) {
            ci.cancel();
        }
    }
}
