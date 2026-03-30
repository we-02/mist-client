package com.tmbu.tmbuclient.mixin.client;

import com.tmbu.tmbuclient.event.EventBus;
import com.tmbu.tmbuclient.event.events.PostKeybindsEvent;
import com.tmbu.tmbuclient.module.impl.player.FastPlace;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Shadow private int rightClickDelay;

    /**
     * Fire PostKeybindsEvent right after handleKeybinds() in Minecraft.tick().
     * Also applies FastPlace delay reduction.
     */
    @Inject(method = "tick",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/Minecraft;handleKeybinds()V",
                     shift = At.Shift.AFTER))
    private void afterHandleKeybinds(CallbackInfo ci) {
        EventBus.INSTANCE.post(new PostKeybindsEvent((Minecraft)(Object)this));

        // FastPlace: reduce right-click delay
        int modified = FastPlace.getModifiedDelay();
        if (modified >= 0 && rightClickDelay > modified) {
            rightClickDelay = modified;
        }
    }
}
