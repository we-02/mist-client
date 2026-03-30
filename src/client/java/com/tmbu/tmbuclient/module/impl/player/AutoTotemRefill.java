package com.tmbu.tmbuclient.module.impl.player;

import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.settings.BooleanSetting;
import com.tmbu.tmbuclient.settings.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import org.lwjgl.glfw.GLFW;

/**
 * AutoTotemRefill — when you pop a totem, opens inventory, swaps a new totem
 * to offhand (and optionally hotbar), then closes.
 *
 * While the module is working, the inventory screen blocks all player input
 * so you can't accidentally mess up the swap.
 */
public class AutoTotemRefill extends Module {

    private final BooleanSetting stopOnShield = addSetting(new BooleanSetting("Stop On Shield", true).group("General"));
    private final SliderSetting  closeDelay   = addSetting(new SliderSetting("Close Delay", 1, 0, 10, 1).group("General"));

    private final BooleanSetting refillHotbar = addSetting(new BooleanSetting("Refill Hotbar", true).group("Hotbar"));
    private final SliderSetting  hotbarSlot   = addSetting(new SliderSetting("Hotbar Slot", 1, 1, 9, 1)
        .group("Hotbar").visibleWhen(refillHotbar::getValue));

    private final BooleanSetting debug = addSetting(new BooleanSetting("Debug", false));

    private enum State { IDLE, WAITING_OPEN, SWAP_OFFHAND, SWAP_HOTBAR, WAITING_CLOSE }
    private State state = State.IDLE;
    private boolean hadOffhandTotem = false;
    private boolean hadHotbarTotem  = false;
    private int tickCounter = 0;
    private boolean swapInFlight = false;
    private int flightTimer = 0;
    private int stateTimeout = 0; // safety timeout to prevent getting stuck

    /** True while we have the locked inventory open — blocks player input. */
    private boolean lockingInventory = false;

    public AutoTotemRefill() {
        super("AutoTotemRefill", "Opens inventory and refills offhand + hotbar totem after a pop",
              Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    public void onEnable() {
        state = State.IDLE;
        hadOffhandTotem = false;
        hadHotbarTotem = false;
        tickCounter = 0;
        swapInFlight = false;
        flightTimer = 0;
        lockingInventory = false;
    }

    @Override
    public void onDisable() {
        if (lockingInventory) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof LockedInventoryScreen) mc.setScreen(null);
            lockingInventory = false;
        }
    }

    /** Returns true if the module currently has the inventory locked. */
    public boolean isLocking() { return lockingInventory; }

    @Override
    public void onTick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) return;
        if (client.gameMode == null || client.gameMode.getPlayerMode() != GameType.SURVIVAL) return;

        if (swapInFlight) {
            if (flightTimer > 0) { flightTimer--; return; }
            swapInFlight = false;
        }

        // Safety timeout: if we've been in a non-IDLE state for too long, abort
        if (state != State.IDLE) {
            stateTimeout++;
            if (stateTimeout > 60) { // 3 seconds at 20tps
                dbg("State timeout — aborting from %s", state);
                if (lockingInventory && client.screen instanceof LockedInventoryScreen) {
                    client.setScreen(null);
                }
                lockingInventory = false;
                state = State.IDLE;
                stateTimeout = 0;
                hadOffhandTotem = player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
                return;
            }
        } else {
            stateTimeout = 0;
        }

        // Don't interfere if AutoTotemHover is enabled and inventory is already open
        if (state == State.IDLE && client.screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen
            && !lockingInventory) {
            // Someone else has the inventory open — don't touch it
            return;
        }

        boolean offhandHasTotem = player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
        int hbIdx = hotbarSlot.getValue().intValue() - 1;
        boolean hotbarHasTotem = hbIdx >= 0 && hbIdx <= 8
            && player.getInventory().getItem(hbIdx).is(Items.TOTEM_OF_UNDYING);

        switch (state) {
            case IDLE -> {
                boolean offhandPopped = hadOffhandTotem && !offhandHasTotem;
                boolean hotbarPopped  = refillHotbar.getValue() && hadHotbarTotem && !hotbarHasTotem;

                if (offhandPopped || hotbarPopped) {
                    if (findTotemSlot(player) == -1) {
                        dbg("No totems in inventory");
                        hadOffhandTotem = offhandHasTotem;
                        hadHotbarTotem = hotbarHasTotem;
                        return;
                    }
                    if (stopOnShield.getValue() && isHoldingShield(player)) {
                        dbg("Suppressed — shield");
                        hadOffhandTotem = offhandHasTotem;
                        hadHotbarTotem = hotbarHasTotem;
                        return;
                    }

                    dbg("Pop detected, opening locked inventory");

                    // Open a locked inventory screen that blocks all player input
                    if (!(client.screen instanceof InventoryScreen)) {
                        lockingInventory = true;
                        client.setScreen(new LockedInventoryScreen(player));
                        state = State.WAITING_OPEN;
                        tickCounter = 1;
                    } else {
                        state = offhandPopped ? State.SWAP_OFFHAND : State.SWAP_HOTBAR;
                    }
                }

                hadOffhandTotem = offhandHasTotem;
                hadHotbarTotem = hotbarHasTotem;
            }

            case WAITING_OPEN -> {
                tickCounter--;
                if (tickCounter <= 0) {
                    if (client.screen instanceof InventoryScreen) {
                        boolean needOffhand = !player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
                        state = needOffhand ? State.SWAP_OFFHAND : State.SWAP_HOTBAR;
                    } else {
                        dbg("Inventory didn't open, aborting");
                        lockingInventory = false;
                        resetToIdle(player);
                    }
                }
            }

            case SWAP_OFFHAND -> {
                if (!(client.screen instanceof InventoryScreen)) { lockingInventory = false; resetToIdle(player); return; }

                int slot = findTotemSlot(player);
                if (slot == -1) { dbg("No totem for offhand"); advanceAfterOffhand(player); return; }

                client.gameMode.handleInventoryMouseClick(
                    player.inventoryMenu.containerId, slot, 40, ClickType.SWAP, player);
                dbg("Swapped slot %d → offhand", slot);

                swapInFlight = true;
                flightTimer = computeFlightTimer(client);
                advanceAfterOffhand(player);
            }

            case SWAP_HOTBAR -> {
                if (!(client.screen instanceof InventoryScreen)) { lockingInventory = false; resetToIdle(player); return; }

                int slot = findTotemSlot(player);
                if (slot == -1) { dbg("No totem for hotbar"); goToClose(); return; }

                client.gameMode.handleInventoryMouseClick(
                    player.inventoryMenu.containerId, slot, hbIdx, ClickType.SWAP, player);
                dbg("Swapped slot %d → hotbar %d", slot, hbIdx);

                swapInFlight = true;
                flightTimer = computeFlightTimer(client);
                goToClose();
            }

            case WAITING_CLOSE -> {
                tickCounter--;
                if (tickCounter <= 0) {
                    if (client.screen instanceof InventoryScreen) {
                        client.setScreen(null);
                        dbg("Closed inventory");
                    }
                    lockingInventory = false;
                    resetToIdle(player);
                }
            }
        }
    }

    private void advanceAfterOffhand(LocalPlayer player) {
        int hbIdx = hotbarSlot.getValue().intValue() - 1;
        boolean needHotbar = refillHotbar.getValue() && hbIdx >= 0 && hbIdx <= 8
            && !player.getInventory().getItem(hbIdx).is(Items.TOTEM_OF_UNDYING);
        if (needHotbar && findTotemSlot(player) != -1) {
            state = State.SWAP_HOTBAR;
        } else {
            goToClose();
        }
    }

    private void goToClose() {
        state = State.WAITING_CLOSE;
        tickCounter = closeDelay.getValue().intValue();
    }

    private void resetToIdle(LocalPlayer player) {
        state = State.IDLE;
        hadOffhandTotem = player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
        int hbIdx = hotbarSlot.getValue().intValue() - 1;
        hadHotbarTotem = hbIdx >= 0 && hbIdx <= 8
            && player.getInventory().getItem(hbIdx).is(Items.TOTEM_OF_UNDYING);
    }

    private int findTotemSlot(LocalPlayer player) {
        int skipHotbarSlot = refillHotbar.getValue()
            ? 36 + (hotbarSlot.getValue().intValue() - 1) : -1;
        for (int i = 9; i <= 44; i++) {
            if (i == skipHotbarSlot) continue;
            if (player.inventoryMenu.getSlot(i).getItem().is(Items.TOTEM_OF_UNDYING)) return i;
        }
        return -1;
    }

    private int computeFlightTimer(Minecraft client) {
        int pingMs = getPingMs(client);
        return Math.max(3, (int) Math.ceil(pingMs / 50.0) + 2);
    }

    private static boolean isHoldingShield(LocalPlayer p) {
        return p.getMainHandItem().is(Items.SHIELD) || p.getOffhandItem().is(Items.SHIELD);
    }

    private int getPingMs(Minecraft client) {
        if (client.player == null || client.getConnection() == null) return 0;
        var info = client.getConnection().getPlayerInfo(client.player.getUUID());
        return info == null ? 0 : info.getLatency();
    }

    private void dbg(String fmt, Object... args) {
        if (debug.getValue()) System.out.println("[AutoTotemRefill] " + (args.length > 0 ? String.format(fmt, args) : fmt));
    }

    /**
     * An InventoryScreen that blocks ALL player input — mouse clicks, drags,
     * key presses, scroll. The server sees a normal inventory open, but the
     * player can't interact with it. Also blocks Escape to prevent closing
     * mid-swap.
     */
    private static class LockedInventoryScreen extends InventoryScreen {
        public LockedInventoryScreen(LocalPlayer player) {
            super(player);
        }

        @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) { return true; }
        @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) { return true; }
        @Override public boolean mouseReleased(MouseButtonEvent event) { return true; }
        @Override public boolean mouseScrolled(double x, double y, double h, double v) { return true; }
        @Override public boolean keyPressed(KeyEvent event) {
            // Allow Escape to close — player safety valve if module gets stuck
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                this.onClose();
                return true;
            }
            return true; // Block everything else
        }
        @Override public boolean charTyped(CharacterEvent event) { return true; }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            // Render normally so it looks like a real inventory to the player
            // (and the server sees the window as open)
            super.render(graphics, mouseX, mouseY, delta);

            // Draw a subtle overlay so the player knows it's locked
            graphics.fill(0, 0, this.width, this.height, 0x44000000);
            String msg = "Refilling totem...";
            graphics.drawString(this.font, msg,
                (this.width - this.font.width(msg)) / 2,
                this.height / 2 - 40,
                0xFFFFAA00, true);
        }
    }
}
