package com.tmbu.tmbuclient.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tmbu.tmbuclient.module.impl.HandView;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {

    @Inject(method = "renderArmWithItem",
            at = @At(value = "INVOKE",
                     target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
                     ordinal = 0,
                     shift = At.Shift.AFTER))
    private void onAfterPushPose(AbstractClientPlayer player, float tickProgress, float pitch,
                                 InteractionHand hand, float swingProgress, ItemStack item,
                                 float equipProgress, PoseStack poseStack,
                                 SubmitNodeCollector collector, int light, CallbackInfo ci) {
        HandView.applyTransforms(hand, poseStack);
    }
}
