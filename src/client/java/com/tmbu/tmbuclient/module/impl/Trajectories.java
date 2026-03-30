package com.tmbu.tmbuclient.module.impl;

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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Trajectories — shows the predicted path of throwable items (ender pearls,
 * snowballs, eggs, potions, tridents, bows, crossbows) as a line in the world.
 * Also renders a landing marker where the projectile will hit.
 */
public class Trajectories extends Module {

    private final ColorSetting   lineColor   = addSetting(new ColorSetting("Line Color", 0xFFFF9600).group("Render"));
    private final ColorSetting   hitColor    = addSetting(new ColorSetting("Hit Color", 0x60FF5000).group("Render"));
    private final SliderSetting  lineWidth   = addSetting(new SliderSetting("Line Width", 2.0, 0.5, 5.0, 0.5).group("Render"));
    private final SliderSetting  maxSteps    = addSetting(new SliderSetting("Max Steps", 300, 50, 1000, 10).group("General"));
    private final SliderSetting  skipFirst   = addSetting(new SliderSetting("Skip First", 3, 0, 20, 1).group("General"));
    private final BooleanSetting showLanding = addSetting(new BooleanSetting("Show Landing", true).group("Render"));

    public Trajectories() {
        super("Trajectories", "Shows predicted projectile paths", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.level == null) return;

        // Check if holding a throwable item
        ItemStack stack = player.getMainHandItem();
        ProjectileInfo info = getProjectileInfo(stack);
        if (info == null) {
            stack = player.getOffhandItem();
            info = getProjectileInfo(stack);
            if (info == null) return;
        }

        PoseStack matrices = context.matrices();
        if (matrices == null) return;
        Vec3 cam = client.gameRenderer.getMainCamera().position();

        // Simulate the projectile path
        List<Vec3> path = simulatePath(player, client.level, info);
        if (path.size() < 2) return;

        // Render the path as lines
        try (ByteBufferBuilder byteBuffer = new ByteBufferBuilder(RenderType.BIG_BUFFER_SIZE)) {
            MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(byteBuffer);
            PoseStack.Pose pose = matrices.last();
            VertexConsumer lines = bufferSource.getBuffer(RenderTypes.LINES);

            int lc = lineColor.getValue();
            int lr = (lc >> 16) & 0xFF, lg = (lc >> 8) & 0xFF, lb = lc & 0xFF, la = (lc >> 24) & 0xFF;
            float w = lineWidth.getValue().floatValue();

            // Skip first N points so the line appears to start from the crosshair
            int start = Math.min(skipFirst.getValue().intValue() + 1, path.size() - 1);
            for (int i = start; i < path.size(); i++) {
                Vec3 a = path.get(i - 1);
                Vec3 b = path.get(i);
                float x1 = (float)(a.x - cam.x), y1 = (float)(a.y - cam.y), z1 = (float)(a.z - cam.z);
                float x2 = (float)(b.x - cam.x), y2 = (float)(b.y - cam.y), z2 = (float)(b.z - cam.z);
                lines.addVertex(pose, x1, y1, z1).setColor(lr, lg, lb, la).setNormal(pose, 0, 1, 0).setLineWidth(w);
                lines.addVertex(pose, x2, y2, z2).setColor(lr, lg, lb, la).setNormal(pose, 0, 1, 0).setLineWidth(w);
            }
            bufferSource.endBatch(RenderTypes.LINES);

            // Landing marker
            if (showLanding.getValue() && path.size() >= 2) {
                Vec3 last = path.get(path.size() - 1);
                float hx = (float)(last.x - cam.x), hy = (float)(last.y - cam.y), hz = (float)(last.z - cam.z);
                float s = 0.25f;

                VertexConsumer tris = bufferSource.getBuffer(RenderTypes.debugFilledBox());
                int hc = hitColor.getValue();
                int hr = (hc >> 16) & 0xFF, hg = (hc >> 8) & 0xFF, hb = hc & 0xFF, ha = (hc >> 24) & 0xFF;

                // Small box at landing point
                quad(tris, pose, hx-s,hy,hz-s, hx+s,hy,hz-s, hx+s,hy,hz+s, hx-s,hy,hz+s, hr,hg,hb,ha);
                quad(tris, pose, hx-s,hy+s*2,hz-s, hx-s,hy+s*2,hz+s, hx+s,hy+s*2,hz+s, hx+s,hy+s*2,hz-s, hr,hg,hb,ha);
                bufferSource.endBatch(RenderTypes.debugFilledBox());
            }
        }
    }

    // ── Projectile simulation (physics from Minecraft wiki + Meteor) ─────────

    /**
     * @param power    initial velocity multiplier
     * @param roll     pitch offset in degrees (potions use -20)
     * @param gravity  gravity per tick
     * @param airDrag  velocity multiplier per tick in air
     * @param waterDrag velocity multiplier per tick in water
     * @param thrown   true = ThrownEntity order (gravity→drag→pos), false = Arrow order (pos→drag→gravity)
     */
    private record ProjectileInfo(float power, float roll, double gravity, float airDrag, float waterDrag, boolean thrown) {}

    private static ProjectileInfo getProjectileInfo(ItemStack stack) {
        Item item = stack.getItem();
        // ThrownEntity physics: gravity → drag → position
        if (item instanceof SnowballItem)           return new ProjectileInfo(1.5f, 0, 0.03, 0.99f, 0.8f, true);
        if (item instanceof EggItem)                return new ProjectileInfo(1.5f, 0, 0.03, 0.99f, 0.8f, true);
        if (item == Items.ENDER_PEARL)              return new ProjectileInfo(1.5f, 0, 0.03, 0.99f, 0.8f, true);
        if (item instanceof ThrowablePotionItem)    return new ProjectileInfo(0.5f, -20, 0.05, 0.99f, 0.8f, true);
        if (item instanceof ExperienceBottleItem)   return new ProjectileInfo(0.7f, -20, 0.07, 0.99f, 0.8f, true);
        if (item instanceof WindChargeItem)         return new ProjectileInfo(1.5f, 0, 0, 1.0f, 1.0f, true);
        // PersistentProjectileEntity physics: position → drag → gravity
        if (item instanceof TridentItem)            return new ProjectileInfo(2.5f, 0, 0.05, 0.99f, 0.99f, false);
        if (item instanceof BowItem)                return new ProjectileInfo(3.0f, 0, 0.05, 0.99f, 0.6f, false);
        if (item instanceof CrossbowItem)           return new ProjectileInfo(3.15f, 0, 0.05, 0.99f, 0.6f, false);
        return null;
    }

    private List<Vec3> simulatePath(LocalPlayer player, Level level, ProjectileInfo info) {
        List<Vec3> path = new ArrayList<>();

        // Starting position: player eye position, offset down slightly like vanilla
        Vec3 pos = player.getEyePosition().add(0, -0.1, 0);
        float yaw = player.getYRot();
        float pitch = player.getXRot();

        // Initial velocity: matches vanilla ProjectileEntity.setVelocity()
        // Uses Minecraft's coordinate convention (yaw in degrees, 0.017453292 = PI/180)
        double x = -Math.sin(yaw * 0.017453292) * Math.cos(pitch * 0.017453292);
        double y = -Math.sin((pitch + info.roll) * 0.017453292);
        double z =  Math.cos(yaw * 0.017453292) * Math.cos(pitch * 0.017453292);

        // Normalize and apply power
        double len = Math.sqrt(x * x + y * y + z * z);
        if (len > 0) { x /= len; y /= len; z /= len; }
        double vx = x * info.power;
        double vy = y * info.power;
        double vz = z * info.power;

        path.add(pos);

        float drag = info.airDrag; // TODO: detect water for waterDrag
        int steps = maxSteps.getValue().intValue();
        for (int i = 0; i < steps; i++) {
            Vec3 prevPos = pos;

            if (info.thrown) {
                // ThrownEntity order: gravity → drag → position
                vy -= info.gravity;
                vx *= drag; vy *= drag; vz *= drag;
                pos = new Vec3(pos.x + vx, pos.y + vy, pos.z + vz);
            } else {
                // PersistentProjectileEntity order: position → drag → gravity
                pos = new Vec3(pos.x + vx, pos.y + vy, pos.z + vz);
                vx *= drag; vy *= drag; vz *= drag;
                vy -= info.gravity;
            }

            // Check for block collision
            BlockHitResult hit = level.clip(new ClipContext(
                prevPos, pos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty()
            ));

            if (hit.getType() != HitResult.Type.MISS) {
                path.add(hit.getLocation());
                break;
            }

            path.add(pos);

            // Stop if below world
            if (pos.y < level.getMinY() - 64) break;
        }

        return path;
    }

    private static void quad(VertexConsumer c, PoseStack.Pose pose,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             int r, int g, int b, int a) {
        c.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
        c.addVertex(pose, x2, y2, z2).setColor(r, g, b, a);
        c.addVertex(pose, x3, y3, z3).setColor(r, g, b, a);
        c.addVertex(pose, x4, y4, z4).setColor(r, g, b, a);
    }
}
