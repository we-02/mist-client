package com.tmbu.tmbuclient.module.impl.render;

import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.settings.BooleanSetting;
import com.tmbu.tmbuclient.settings.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.lwjgl.glfw.GLFW;

/**
 * Renders a customizable particle trail behind the player.
 * Adapted from Meteor Client's Trail module.
 */
public class Trail extends Module {

    public enum TrailParticle {
        FLAME(ParticleTypes.FLAME),
        SOUL_FIRE_FLAME(ParticleTypes.SOUL_FIRE_FLAME),
        CAMPFIRE_SMOKE(ParticleTypes.CAMPFIRE_COSY_SMOKE),
        DRIPPING_OBSIDIAN_TEAR(ParticleTypes.DRIPPING_OBSIDIAN_TEAR),
        END_ROD(ParticleTypes.END_ROD),
        HEART(ParticleTypes.HEART),
        TOTEM(ParticleTypes.TOTEM_OF_UNDYING),
        CLOUD(ParticleTypes.CLOUD),
        SMOKE(ParticleTypes.SMOKE),
        CHERRY_LEAVES(ParticleTypes.CHERRY_LEAVES),
        WITCH(ParticleTypes.WITCH),
        PORTAL(ParticleTypes.PORTAL),
        NOTE(ParticleTypes.NOTE),
        SNOWFLAKE(ParticleTypes.SNOWFLAKE),
        ELECTRIC_SPARK(ParticleTypes.ELECTRIC_SPARK);

        public final ParticleOptions particle;

        TrailParticle(ParticleOptions particle) {
            this.particle = particle;
        }
    }

    private final EnumSetting<TrailParticle> particle = addSetting(
        new EnumSetting<>("Particle", TrailParticle.DRIPPING_OBSIDIAN_TEAR));
    private final BooleanSetting pauseWhenStill = addSetting(
        new BooleanSetting("Pause When Still", true));

    public Trail() {
        super("Trail", "Renders a particle trail behind you",
              Category.RENDER, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null) return;

        if (pauseWhenStill.getValue()
            && client.player.getX() == client.player.xOld
            && client.player.getY() == client.player.yOld
            && client.player.getZ() == client.player.zOld) return;

        client.level.addParticle(
            particle.getValue().particle,
            client.player.getX(),
            client.player.getY(),
            client.player.getZ(),
            0, 0, 0
        );
    }
}
