package com.tmbu.tmbuclient.module.impl.player;

import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.settings.BooleanSetting;
import com.tmbu.tmbuclient.settings.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import org.lwjgl.glfw.GLFW;

public class AutoTotemHover extends Module {

    private final SliderSetting hotbarSlot = addSetting(
            new SliderSetting("Hotbar Slot", 0, 0, 9, 1).group("General"));

    private final SliderSetting baseDelay = addSetting(
            new SliderSetting("Base Delay", 5, 1, 20, 1).group("Timing"));

    private final BooleanSetting dynamicDelay = addSetting(
            new BooleanSetting("Dynamic Delay", true).group("Timing"));

    private final SliderSetting dynamicDelayMax = addSetting(
            new SliderSetting("Dynamic Delay Max", 10, 0, 40, 1).group("Timing").visibleWhen(dynamicDelay::getValue));

    private final BooleanSetting jitter = addSetting(
            new BooleanSetting("Jitter", false).group("Timing"));

    private final SliderSetting jitterMax = addSetting(
            new SliderSetting("Jitter Max", 3, 1, 10, 1).group("Timing").visibleWhen(jitter::getValue));

    private final BooleanSetting stopOnShield = addSetting(
            new BooleanSetting("Stop On Shield", true).group("General"));

    // BUG FIX 1: Track in-flight state so we never double-fire before the
    // server has acknowledged the previous swap. Without this, rapid swaps
    // cause the client and server to disagree on what is in the offhand,
    // producing the "ghost totem" desync where the client shows a totem but
    // the server does not — killing the player on what looks like a pop.
    private boolean swapInFlight = false;
    private int flightTimer = 0;

    public AutoTotemHover() {
        super("AutoTotemHover", "Hover a totem to instantly swap it to offhand or hotbar after a pop.", Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    public void onTick(Minecraft client) {
        // BUG FIX 1 (continued): Tick down the in-flight guard. Only clear it
        // once enough time has passed for a full RTT, ensuring the server has
        // processed the packet before we read inventory state or send again.
        if (swapInFlight) {
            if (flightTimer > 0) {
                flightTimer--;
                return;
            }
            swapInFlight = false;
        }

        LocalPlayer player = client.player;
        if (player == null) return;
        if (client.gameMode == null || client.gameMode.getPlayerMode() != GameType.SURVIVAL) return;

        // BUG FIX 2: Only allow swaps from the vanilla inventory screen, not
        // from chests, hoppers, or other containers. When a non-inventory
        // container is open, hovered.index is a slot index in *that* container's
        // slot list, not the player's inventory slot ID. Passing it to
        // handleInventoryMouseClick with the player's containerId causes the
        // server to move the wrong item or reject the packet silently, which
        // again produces a ghost-totem desync. Restricting to InventoryScreen
        // ensures slot indices always correspond to the player's own inventory.
        if (!(client.screen instanceof InventoryScreen screen)) return;

        // Suppress all swaps while the player is holding a shield in either hand.
        // Checked after the in-flight guard so a pending swap still resolves
        // before the shield check can block the next one.
        if (stopOnShield.getValue() && isHoldingShield(player)) return;

        // BUG FIX 3: Read offhand and hotbar state *after* the in-flight guard.
        // Reading it before meant we could see a stale client-side "empty"
        // offhand from a swap that the server hasn't confirmed yet, causing a
        // second swap to fire on top of the unacknowledged first one.
        boolean offhandEmpty = player.getOffhandItem().isEmpty()
                || !player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);

        int hotbarIndex = hotbarSlot.getValue().intValue() - 1;
        boolean hotbarEnabled = hotbarIndex >= 0;

        // BUG FIX 7: Never attempt to fill both offhand and hotbar in the same
        // decision window. If the offhand is empty, fill it first and return —
        // the hotbar will be evaluated on a future tick only after the offhand
        // swap has been acknowledged (guarded by swapInFlight above).
        //
        // Without this, a pop that leaves both slots empty causes two back-to-back
        // SWAP packets targeting the *same* hovered totem: one for offhand (button=40)
        // and one for hotbar (button=hotbarIndex). The server processes them in
        // order — moves the totem on the first packet, then either moves an empty
        // source slot on the second (sending air to hotbar) or silently rejects it.
        // The client predicted both swaps though, showing totems in both slots:
        // the classic ghost that vanishes on the next server sync.
        //
        // Strict offhand-first ordering eliminates this entirely.
        if (offhandEmpty) {
            // Offhand needs a totem — handle that exclusively this tick.
            // Fall through to the swap logic below; hotbar is not evaluated.
        } else {
            // Offhand is confirmed full. Now check hotbar.
            if (!hotbarEnabled) return;
            boolean hotbarEmpty = player.getInventory().getItem(hotbarIndex).isEmpty()
                    || !player.getInventory().getItem(hotbarIndex).is(Items.TOTEM_OF_UNDYING);
            if (!hotbarEmpty) return; // both slots are fine, nothing to do
        }

        Slot hovered = ((com.tmbu.tmbuclient.mixin.client.AbstractContainerScreenAccessor) screen).getHoveredSlot();
        if (hovered == null || !hovered.hasItem() || !hovered.getItem().is(Items.TOTEM_OF_UNDYING)) return;

        // BUG FIX 4: Use the correct containerId. player.inventoryMenu.containerId
        // is always 0 for the player's own inventory. When the player's inventory
        // screen is open (which we now guarantee above), this is correct. But the
        // original code used this even when a chest was open — the chest has a
        // *different* containerId, so the server would reject or misroute the packet.
        // Now that we gate on InventoryScreen this is consistent, but made explicit
        // here for clarity.
        int containerId = player.inventoryMenu.containerId; // always 0, correct for inventory

        int sourceSlot = hovered.index;

        if (offhandEmpty) {
            // Swap to offhand: button=40 is the vanilla "swap with offhand" action.
            client.gameMode.handleInventoryMouseClick(
                    containerId, sourceSlot, 40, ClickType.SWAP, player);
        } else {
            // BUG FIX 5: Swap to hotbar: button must be 0–8 (hotbar slot index).
            // The original code used hotbarIndex which is already 0-based — this
            // was correct. Keeping it explicit and documented so it is not
            // accidentally changed to a 1-based value in the future, which would
            // silently send the totem to the wrong hotbar slot on the server.
            //
            // We only reach this branch when offhand is confirmed full (see BUG FIX 7),
            // so there is no risk of the same totem being sent to both destinations.
            client.gameMode.handleInventoryMouseClick(
                    containerId, sourceSlot, hotbarIndex, ClickType.SWAP, player);
        }

        // BUG FIX 1 (continued): Mark in-flight and set the timer to the full
        // RTT, not just the base delay. The base delay alone is a UI preference;
        // the in-flight timer is a correctness guard and must cover at least one
        // full round-trip so the server-side inventory state is settled before
        // we read it or send another packet.
        swapInFlight = true;
        flightTimer = computeFlightTimer(client);
    }

    /**
     * Computes the in-flight RTT guard timer.
     *
     * This is separate from the visual/UX cooldown concept in the original code.
     * The minimum value is always ceil(pingMs / 50) + 2 ticks:
     *   - ceil(pingMs / 50) covers the one-way trip to the server in ticks
     *     (at 20tps each tick = 50ms)
     *   - +2 gives one tick of server-side processing margin and one tick of
     *     return-trip margin (since getLatency() is already the full RTT, the
     *     one-way formula is conservative — we keep the +2 buffer regardless)
     *
     * Dynamic delay and jitter are layered on top for anti-cheat purposes, but
     * they can never bring the timer *below* the RTT minimum.
     */
    private int computeFlightTimer(Minecraft client) {
        int pingMs = getPingMs(client);

        // Minimum ticks to cover the round-trip. getLatency() returns RTT in ms.
        // Dividing by 50 converts to ticks (50ms per tick at 20tps).
        // BUG FIX 6: The original formula used pingMs / 50.0 as *extra* ticks on
        // top of baseDelay, but baseDelay is a UX preference (how often to swap),
        // not a server-safety floor. A user setting baseDelay=1 with 300ms ping
        // would produce a 7-tick timer — not enough. We now compute the RTT floor
        // independently and take the max of it vs baseDelay + extras, so the
        // server-safety floor can never be undercut by a low baseDelay setting.
        int rttFloorTicks = (int) Math.ceil(pingMs / 50.0) + 2;

        int delay = baseDelay.getValue().intValue();

        if (dynamicDelay.getValue()) {
            int extraTicks = (int) Math.round(pingMs / 50.0);
            int maxExtra = dynamicDelayMax.getValue().intValue();
            extraTicks = Math.min(extraTicks, maxExtra);
            delay += extraTicks;
        }

        if (jitter.getValue()) {
            int maxJitter = jitterMax.getValue().intValue();
            delay += (int) (Math.random() * (maxJitter + 1));
        }

        // Never go below the RTT floor regardless of user settings.
        return Math.max(delay, rttFloorTicks);
    }

    /**
     * Returns true if the player is holding a shield in either hand.
     * Checks both hands to cover the common offhand-shield + main-hand weapon
     * setup as well as the less common main-hand shield case.
     */
    private static boolean isHoldingShield(LocalPlayer player) {
        return player.getMainHandItem().is(Items.SHIELD)
            || player.getOffhandItem().is(Items.SHIELD);
    }

    private int getPingMs(Minecraft client) {
        if (client.player == null || client.getConnection() == null) return 0;
        var info = client.getConnection().getPlayerInfo(client.player.getUUID());
        if (info == null) return 0;
        return info.getLatency();
    }
}