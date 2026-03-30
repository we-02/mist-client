package com.tmbu.tmbuclient.module.impl.render;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.settings.BooleanSetting;
import com.tmbu.tmbuclient.settings.ColorSetting;
import com.tmbu.tmbuclient.settings.SliderSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * BlastTrajectory — when a player takes knockback, snapshots their position
 * and velocity at that moment, then draws a fixed trajectory line showing
 * where they'll land. The line stays in world space and clears when they
 * touch the ground.
 */
public class BlastTrajectory extends Module {

    private final SliderSetting  range        = addSetting(new SliderSetting("Range", 24, 8, 64, 1).group("General"));
    private final SliderSetting  simTicks     = addSetting(new SliderSetting("Sim Ticks", 30, 5, 80, 1).group("General"));
    private final SliderSetting  minSpeed     = addSetting(new SliderSetting("Min Speed", 0.3, 0.05, 2.0, 0.05).group("General"));
    private final BooleanSetting showSelf     = addSetting(new BooleanSetting("Show Self", true).group("General"));
    private final BooleanSetting showOthers   = addSetting(new BooleanSetting("Show Others", true).group("General"));
    private final BooleanSetting throughWalls = addSetting(new BooleanSetting("Through Walls", true).group("General"));
    private final ColorSetting   selfColor    = addSetting(new ColorSetting("Self Color", 0xFFFF5555).group("Colors"));
    private final ColorSetting   otherColor   = addSetting(new ColorSetting("Other Color", 0xFF55FF55).group("Colors"));

    /** Snapshots of knockback trajectories, keyed by entity ID. */
    private final Map<Integer, TrajectorySnapshot> snapshots = new HashMap<>();
    /** Previous velocity per entity to detect new knockback. */
    private final Map<Integer, Vec3> prevVelocities = new HashMap<>();

    public BlastTrajectory() {
        super("BlastTrajectory", "Predicts where players fly after taking knockback",
              Category.RENDER, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    public void onDisable() {
        snapshots.clear();
        prevVelocities.clear();
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.level == null || client.player == null) return;

        double r = range.getValue();
        double minSpeedSq = minSpeed.getValue() * minSpeed.getValue();
        int ticks = simTicks.getValue().intValue();
        Level level = client.level;

        // Check all nearby players for new knockback events
        for (Player player : level.getEntitiesOfClass(Player.class,
                client.player.getBoundingBox().inflate(r), Player::isAlive)) {
            if (player.isSpectator()) continue;
            if (player == client.player && !showSelf.getValue()) continue;
            if (player != client.player && !showOthers.getValue()) continue;

            int id = player.getId();

            // If player landed, clear their snapshot
            if (player.onGround()) {
                snapshots.remove(id);
                prevVelocities.remove(id);
                continue;
            }

            // Detect new knockback: velocity changed significantly from last tick
            Vec3 vel = player.getDeltaMovement();
            double speedSq = vel.x * vel.x + vel.y * vel.y + vel.z * vel.z;
            Vec3 prev = prevVelocities.get(id);
            prevVelocities.put(id, vel);

            if (speedSq >= minSpeedSq && !player.onGround()) {
                // Re-snapshot if velocity changed at all from last tick (new knockback)
                boolean isNew = !snapshots.containsKey(id);
                if (!isNew && prev != null && !vel.equals(prev)) {
                    isNew = true;
                }

                if (isNew) {
                    Vec3[] points = simulate(player.position(), vel, ticks, level);
                    boolean isSelf = (player == client.player);
                    snapshots.put(id, new TrajectorySnapshot(points, isSelf));
                }
            }
        }

        // Clean up snapshots for entities that no longer exist or are out of range
        Iterator<Map.Entry<Integer, TrajectorySnapshot>> it = snapshots.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, TrajectorySnapshot> entry = it.next();
            Player p = (Player) level.getEntity(entry.getKey());
            if (p == null || !p.isAlive() || p.onGround()
                || client.player.distanceToSqr(p) > r * r) {
                it.remove();
            } else {
                // Advance startIndex: skip points the player has already passed
                TrajectorySnapshot snap = entry.getValue();
                Vec3 playerPos = p.position();
                while (snap.startIndex < snap.points.length - 1) {
                    Vec3 pt = snap.points[snap.startIndex];
                    if (pt == null || playerPos.distanceToSqr(pt) < 1.0) {
                        snap.startIndex++;
                    } else {
                        break;
                    }
                }
                // If all points consumed, remove
                if (snap.startIndex >= snap.points.length - 1) {
                    it.remove();
                }
            }
        }
    }

    private Vec3[] simulate(Vec3 startPos, Vec3 startVel, int ticks, Level level) {
        Vec3[] points = new Vec3[ticks + 1];
        double px = startPos.x, py = startPos.y, pz = startPos.z;
        double vx = startVel.x, vy = startVel.y, vz = startVel.z;

        points[0] = startPos;

        for (int t = 0; t < ticks; t++) {
            vy -= 0.08; // gravity
            vx *= 0.91; vy *= 0.98; vz *= 0.91; // drag

            // Ground collision
            BlockPos below = BlockPos.containing(px, py - 0.1, pz);
            if (!level.getBlockState(below).isAir() && vy <= 0) {
                vy = 0;
                vx *= 0.6; vz *= 0.6;
            }

            px += vx; py += vy; pz += vz;
            points[t + 1] = new Vec3(px, py, pz);

            if (vx * vx + vy * vy + vz * vz < 0.0001) {
                // Fill remaining with last point
                for (int j = t + 2; j <= ticks; j++) points[j] = points[t + 1];
                break;
            }
        }

        return points;
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        if (snapshots.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        PoseStack matrices = context.matrices();
        if (matrices == null) return;

        Vec3 cam = mc.gameRenderer.getMainCamera().position();

        if (throughWalls.getValue()) GL11.glDisable(GL11.GL_DEPTH_TEST);

        try (ByteBufferBuilder buf = new ByteBufferBuilder(RenderType.BIG_BUFFER_SIZE)) {
            MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(buf);
            VertexConsumer lines = bufferSource.getBuffer(RenderTypes.LINES);
            PoseStack.Pose pose = matrices.last();

            for (TrajectorySnapshot snap : snapshots.values()) {
                int color = snap.isSelf ? selfColor.getColor() : otherColor.getColor();
                int cr = (color >> 16) & 0xFF, cg = (color >> 8) & 0xFF, cb = color & 0xFF, ca = (color >> 24) & 0xFF;

                Vec3[] pts = snap.points;
                int total = pts.length;
                int start = snap.startIndex;

                for (int i = start; i < total - 1; i++) {
                    if (pts[i] == null || pts[i + 1] == null) break;
                    if (pts[i].equals(pts[i + 1])) break;

                    int remaining = total - start;
                    int a = Math.max(10, (int)(ca * (1.0 - (double)(i - start) / remaining)));

                    float x0 = (float)(pts[i].x - cam.x), y0 = (float)(pts[i].y - cam.y), z0 = (float)(pts[i].z - cam.z);
                    float x1 = (float)(pts[i+1].x - cam.x), y1 = (float)(pts[i+1].y - cam.y), z1 = (float)(pts[i+1].z - cam.z);

                    lines.addVertex(pose, x0, y0, z0).setColor(cr, cg, cb, a).setNormal(pose, 0, 1, 0).setLineWidth(2f);
                    lines.addVertex(pose, x1, y1, z1).setColor(cr, cg, cb, a).setNormal(pose, 0, 1, 0).setLineWidth(2f);
                }
            }

            bufferSource.endBatch(RenderTypes.LINES);
        }

        if (throughWalls.getValue()) GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    private static class TrajectorySnapshot {
        final Vec3[] points;
        final boolean isSelf;
        int startIndex = 0; // advances as player passes points

        TrajectorySnapshot(Vec3[] points, boolean isSelf) {
            this.points = points;
            this.isSelf = isSelf;
        }
    }
}
