package com.tmbu.tmbuclient.module.impl.combat;

import com.tmbu.tmbuclient.event.EventBus;
import com.tmbu.tmbuclient.event.events.PreMotionEvent;
import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.settings.BooleanSetting;
import com.tmbu.tmbuclient.settings.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * SuperKnockback — increases knockback dealt to entities by sprint-resetting.
 *
 * Grim's PacketOrderF flags if sprint packets (ENTITY_ACTION) and attack
 * (INTERACT_ENTITY) are in the same tick. To avoid this, we use a two-tick
 * approach:
 *
 * Tick N:   Detect that we're about to attack (crosshair on entity + cooldown ready).
 *           Send STOP_SPRINTING. This is the only packet this tick.
 * Tick N+1: Vanilla sprint-start happens naturally because the player is moving
 *           forward (or we send START_SPRINTING). Then the attack happens normally.
 *           The sprint toggle was in a previous tick, so PacketOrderF doesn't flag.
 *
 * This mimics a real W-tap: release W briefly, then press W again and attack.
 */
public class SuperKnockback extends Module {

    private final SliderSetting  hurtTime     = addSetting(new SliderSetting("Hurt Time", 10, 0, 10, 1).group("General"));
    private final SliderSetting  chance       = addSetting(new SliderSetting("Chance", 100, 0, 100, 1).group("General"));
    private final BooleanSetting onlyOnGround = addSetting(new BooleanSetting("Only On Ground", false).group("Conditions"));
    private final BooleanSetting onlyMoving   = addSetting(new BooleanSetting("Only Moving", true).group("Conditions"));
    private final BooleanSetting notInWater   = addSetting(new BooleanSetting("Not In Water", true).group("Conditions"));

    /** When true, we sent STOP_SPRINTING last tick and need to send START_SPRINTING this tick. */
    private boolean pendingResprint = false;

    private final Consumer<PreMotionEvent> preMotionHandler = e -> onPreMotion(e.client());

    public SuperKnockback() {
        super("SuperKnockback", "Increases knockback dealt via sprint-reset",
              Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    protected void registerEvents(EventBus bus) {
        // Run before other combat modules so the sprint state is correct
        bus.subscribe(PreMotionEvent.class, 5, preMotionHandler);
    }

    @Override
    protected void unregisterEvents(EventBus bus) {
        bus.unsubscribe(PreMotionEvent.class, preMotionHandler);
    }

    @Override
    public void onEnable() {
        pendingResprint = false;
    }

    @Override
    public void onDisable() {
        pendingResprint = false;
    }

    private void onPreMotion(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.getConnection() == null) return;

        // Phase 2: Re-sprint after the stop from last tick
        if (pendingResprint) {
            pendingResprint = false;
            // Actually start sprinting again (client + server)
            player.setSprinting(true);
            return;
        }

        // Phase 1: Check if we should sprint-reset this tick
        if (!player.isSprinting()) return;
        if (onlyOnGround.getValue() && !player.onGround()) return;
        if (notInWater.getValue() && player.isInWater()) return;
        if (onlyMoving.getValue() && player.getDeltaMovement().horizontalDistanceSqr() < 0.0001) return;

        // Check if we're looking at a valid target that we're about to attack
        Entity target = getLookedTarget(client, player);
        if (target == null) return;

        if (!(target instanceof LivingEntity living)) return;
        if (living.hurtTime > hurtTime.getValue().intValue()) return;

        float cooldown = player.getAttackStrengthScale(0.5f);
        if (cooldown < 0.9f) return;

        if (Math.random() * 100 > chance.getValue()) return;

        // Actually stop sprinting (client-side too, so Simulation matches)
        player.setSprinting(false);
        pendingResprint = true;
    }

    private Entity getLookedTarget(Minecraft client, LocalPlayer player) {
        if (client.hitResult == null || client.hitResult.getType() != HitResult.Type.ENTITY) return null;
        if (!(client.hitResult instanceof EntityHitResult eHit)) return null;
        Entity target = eHit.getEntity();
        if (!target.isAlive() || target == player) return null;
        if (player.distanceToSqr(target) > 9.0) return null; // ~3 block range
        return target;
    }
}
