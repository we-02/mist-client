package com.tmbu.tmbuclient.module.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tmbu.tmbuclient.event.EventBus;
import com.tmbu.tmbuclient.event.events.NameTagRenderEvent;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public class NametagsModule extends Module {
    // ── Entities ─────────────────────────────────────────────────────────────
    private final BooleanSetting showPlayers  = addSetting(new BooleanSetting("Players", true).group("Entities"));
    private final BooleanSetting showMobs     = addSetting(new BooleanSetting("Mobs", false).group("Entities"));
    private final BooleanSetting showInvisible = addSetting(new BooleanSetting("Invisible", true).group("Entities"));
    private final SliderSetting  range        = addSetting(new SliderSetting("Range", 48.0, 8.0, 128.0, 1.0).group("Entities"));

    // ── Info ─────────────────────────────────────────────────────────────────
    private final BooleanSetting showHealth     = addSetting(new BooleanSetting("Health", true).group("Info"));
    private final BooleanSetting showAbsorption = addSetting(new BooleanSetting("Absorption", true).group("Info"));
    private final BooleanSetting showDistance   = addSetting(new BooleanSetting("Distance", true).group("Info"));
    private final BooleanSetting showPing       = addSetting(new BooleanSetting("Ping", true).group("Info"));
    private final BooleanSetting showGamemode   = addSetting(new BooleanSetting("Gamemode", false).group("Info"));

    // ── Items ────────────────────────────────────────────────────────────────
    private final BooleanSetting showMainHand = addSetting(new BooleanSetting("Main Hand", true).group("Items"));
    private final BooleanSetting showOffHand  = addSetting(new BooleanSetting("Off Hand", true).group("Items"));
    private final BooleanSetting showArmor    = addSetting(new BooleanSetting("Armor Value", true).group("Items"));
    private final BooleanSetting showTotems   = addSetting(new BooleanSetting("Totem Count", true).group("Items"));

    private final Consumer<NameTagRenderEvent> nameTagHandler = NameTagRenderEvent::cancel;

    public NametagsModule() {
        super("Nametags", "Custom nametags with health, items, ping, and more.", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    protected void registerEvents(EventBus bus) {
        bus.subscribe(NameTagRenderEvent.class, nameTagHandler);
    }

    @Override
    protected void unregisterEvents(EventBus bus) {
        bus.unsubscribe(NameTagRenderEvent.class, nameTagHandler);
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
            if (!showInvisible.getValue() && entity.isInvisible()) continue;

            boolean isPlayer = entity instanceof Player p && !p.isSpectator();
            boolean isMob = entity instanceof LivingEntity && !(entity instanceof Player);

            if ((isPlayer && !showPlayers.getValue()) || (isMob && !showMobs.getValue()) || (!isPlayer && !isMob))
                continue;

            Component label = buildLabel(client, entity, isPlayer);

            Vec3 attachment = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(1.0f));
            double distSq = cameraPos.distanceToSqr(entity.position());
            int light = LightTexture.pack(15, 15);

            matrices.pushPose();
            matrices.translate(
                entity.getX() - cameraPos.x,
                entity.getY() - cameraPos.y,
                entity.getZ() - cameraPos.z
            );

            commandQueue.submitNameTag(matrices, attachment, 0, label, true, light, distSq, cameraRenderState);
            matrices.popPose();
        }
    }

    private Component buildLabel(Minecraft client, Entity entity, boolean isPlayer) {
        MutableComponent label = Component.empty();

        // ═══ LINE 1: Name + Gamemode ═════════════════════════════════════════
        label.append(Component.literal(entity.getName().getString())
            .withStyle(isPlayer ? ChatFormatting.WHITE : ChatFormatting.RED)
            .withStyle(ChatFormatting.BOLD));

        if (showGamemode.getValue() && entity instanceof Player player) {
            String gm = getGamemodeTag(client, player);
            if (gm != null) {
                label.append(Component.literal(" " + gm).withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        // ═══ LINE 2: Health bar + Ping + Distance ════════════════════════════
        if (entity instanceof LivingEntity living) {
            label.append(Component.literal("\n"));

            if (showHealth.getValue()) {
                float hp = living.getHealth();
                float maxHp = living.getMaxHealth();
                // Health as colored number
                label.append(Component.literal(String.format("%.1f", hp))
                    .withStyle(getHealthColor(hp, maxHp)));
                label.append(Component.literal("/" + String.format("%.0f", maxHp) + " HP")
                    .withStyle(ChatFormatting.DARK_GRAY));
            }

            if (showAbsorption.getValue() && living.getAbsorptionAmount() > 0) {
                label.append(Component.literal(String.format(" +%.0f", living.getAbsorptionAmount()))
                    .withStyle(ChatFormatting.GOLD));
            }

            if (showArmor.getValue() && living.getArmorValue() > 0) {
                label.append(Component.literal(" A:" + living.getArmorValue())
                    .withStyle(ChatFormatting.BLUE));
            }

            if (showPing.getValue() && entity instanceof Player player && client.getConnection() != null) {
                var info = client.getConnection().getPlayerInfo(player.getUUID());
                if (info != null) {
                    int ping = info.getLatency();
                    ChatFormatting pc = ping < 80 ? ChatFormatting.GREEN : ping < 150 ? ChatFormatting.YELLOW : ChatFormatting.RED;
                    label.append(Component.literal(" " + ping + "ms").withStyle(pc));
                }
            }

            if (showDistance.getValue() && client.player != null) {
                double dist = client.player.distanceTo(entity);
                label.append(Component.literal(String.format(" %.0fm", dist)).withStyle(ChatFormatting.GRAY));
            }
        }

        // ═══ LINE 3: Items ═══════════════════════════════════════════════════
        if (entity instanceof LivingEntity living) {
            MutableComponent itemLine = Component.empty();
            boolean hasInfo = false;

            if (showMainHand.getValue() && !living.getMainHandItem().isEmpty()) {
                itemLine.append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY));
                itemLine.append(formatItem(living.getMainHandItem(), ChatFormatting.WHITE));
                itemLine.append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
                hasInfo = true;
            }

            if (showOffHand.getValue() && !living.getOffhandItem().isEmpty()) {
                if (hasInfo) itemLine.append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY));
                itemLine.append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY));
                itemLine.append(formatItem(living.getOffhandItem(), ChatFormatting.AQUA));
                itemLine.append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
                hasInfo = true;
            }

            if (showTotems.getValue() && entity instanceof Player player) {
                int totems = countTotems(player);
                if (totems > 0) {
                    if (hasInfo) itemLine.append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY));
                    itemLine.append(Component.literal(totems + "T").withStyle(ChatFormatting.GOLD));
                    hasInfo = true;
                }
            }

            if (hasInfo) {
                label.append(Component.literal("\n"));
                label.append(itemLine);
            }
        }

        return label;
    }

    private static Component formatItem(ItemStack stack, ChatFormatting color) {
        String name = stack.getHoverName().getString();
        // Truncate long names
        if (name.length() > 16) name = name.substring(0, 14) + "..";
        MutableComponent comp = Component.literal(name).withStyle(color);
        if (stack.getCount() > 1) {
            comp.append(Component.literal(" x" + stack.getCount()).withStyle(ChatFormatting.GRAY));
        }
        return comp;
    }

    private static int countTotems(Player player) {
        int count = 0;
        if (player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) count++;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(Items.TOTEM_OF_UNDYING)) count++;
        }
        return count;
    }

    private static String getGamemodeTag(Minecraft client, Player player) {
        if (client.getConnection() == null) return null;
        var info = client.getConnection().getPlayerInfo(player.getUUID());
        if (info == null || info.getGameMode() == null) return null;
        return switch (info.getGameMode()) {
            case SURVIVAL -> "S";
            case CREATIVE -> "C";
            case ADVENTURE -> "A";
            case SPECTATOR -> "SP";
        };
    }

    private static ChatFormatting getHealthColor(float health, float maxHealth) {
        float pct = maxHealth > 0 ? health / maxHealth : 0;
        if (pct > 0.6f) return ChatFormatting.GREEN;
        if (pct > 0.3f) return ChatFormatting.YELLOW;
        return ChatFormatting.RED;
    }
}
