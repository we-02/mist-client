package com.tmbu.tmbuclient.module.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.settings.BooleanSetting;
import com.tmbu.tmbuclient.settings.SliderSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class NametagsModule extends Module {
	private final BooleanSetting showPlayers = addSetting(new BooleanSetting("Players", true));
	private final BooleanSetting showMobs = addSetting(new BooleanSetting("Mobs", false));
	private final BooleanSetting showHealth = addSetting(new BooleanSetting("Show Health", true));
	private final BooleanSetting showDistance = addSetting(new BooleanSetting("Show Distance", true));
	private final SliderSetting range = addSetting(new SliderSetting("Range", 48.0, 8.0, 128.0, 1.0));

	public NametagsModule() {
		super("Nametags", "Shows custom nametags above entities with health and distance.", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN);
	}

	@Override
	public void onWorldRender(WorldRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) return;

		PoseStack matrices = context.matrices();
		SubmitNodeCollector commandQueue = context.commandQueue();
		if (matrices == null || commandQueue == null) return;

		Camera camera = client.gameRenderer.getMainCamera();
		Vec3 cameraPos = camera.position();

		// Build CameraRenderState from current camera
		CameraRenderState cameraRenderState = new CameraRenderState();
		cameraRenderState.pos = cameraPos;
		cameraRenderState.blockPos = BlockPos.containing(cameraPos);
		cameraRenderState.orientation = camera.rotation();
		cameraRenderState.initialized = true;

		double r = range.getValue();
		AABB searchBox = new AABB(
			client.player.getX() - r, client.player.getY() - r, client.player.getZ() - r,
			client.player.getX() + r, client.player.getY() + r, client.player.getZ() + r
		);

		for (Entity entity : client.level.getEntities(client.player, searchBox, Entity::isAlive)) {
			if (entity == client.player) continue;

			boolean isPlayer = entity instanceof Player p && !p.isSpectator();
			boolean isMob = entity instanceof LivingEntity && !(entity instanceof Player);

			if ((isPlayer && !showPlayers.getValue()) || (isMob && !showMobs.getValue()) || (!isPlayer && !isMob)) {
				continue;
			}

			Component label = buildLabel(client, entity, isPlayer);

			// Name tag attachment point above the entity's head
			Vec3 attachment = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(1.0f));

			double distSq = cameraPos.distanceToSqr(entity.position());
			int light = LightTexture.pack(15, 15);

			matrices.pushPose();
			// Translate to entity position relative to camera
			matrices.translate(
				entity.getX() - cameraPos.x,
				entity.getY() - cameraPos.y,
				entity.getZ() - cameraPos.z
			);

			commandQueue.submitNameTag(
				matrices,
				attachment,
				0,
				label,
				true,  // seeThrough = visible through walls
				light,
				distSq,
				cameraRenderState
			);

			matrices.popPose();
		}
	}

	private Component buildLabel(Minecraft client, Entity entity, boolean isPlayer) {
		MutableComponent label = Component.literal(entity.getName().getString())
			.withStyle(isPlayer ? ChatFormatting.AQUA : ChatFormatting.RED);

		if (showHealth.getValue() && entity instanceof LivingEntity living) {
			float health = living.getHealth();
			float maxHealth = living.getMaxHealth();
			label = label.append(
				Component.literal(String.format(" %.1f\u2764", health))
					.withStyle(getHealthColor(health, maxHealth))
			);
		}

		if (showDistance.getValue() && client.player != null) {
			double dist = client.player.distanceTo(entity);
			label = label.append(
				Component.literal(String.format(" %.0fm", dist))
					.withStyle(ChatFormatting.GRAY)
			);
		}

		return label;
	}

	private ChatFormatting getHealthColor(float health, float maxHealth) {
		float pct = health / maxHealth;
		if (pct > 0.6f) return ChatFormatting.GREEN;
		if (pct > 0.3f) return ChatFormatting.YELLOW;
		return ChatFormatting.RED;
	}
}