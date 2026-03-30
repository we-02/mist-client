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

    /**
     * Meteor injects at the renderItem call (BEFORE shift) inside renderFirstPersonItem.
     * In Mojang mappings 1.21.11, renderFirstPersonItem = renderArmWithItem,
     * and the item render call is ItemInHandRenderer.renderItem.
     *
     * We target the renderItem method call inside renderArmWithItem.
     * This fires AFTER all vanilla arm/equip/swing transforms have been applied
     * to the PoseStack, so our rotation happens around the hand's local origin.
     */
    @Inject(method = "renderArmWithItem",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
                     shift = At.Shift.BEFORE))
    private void onRenderItem(AbstractClientPlayer player, float tickProgress, float pitch,
                              InteractionHand hand, float swingProgress, ItemStack item,
                              float equipProgress, PoseStack poseStack,
                              SubmitNodeCollector collector, int light, CallbackInfo ci) {
        HandView.applyTransforms(hand, poseStack);
    }
}
