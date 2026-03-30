package com.tmbu.tmbuclient.event.events;

import net.minecraft.client.Minecraft;

/**
 * Fired before the local player sends position packets.
 * Modules that need to act on pre-motion (like AutoCrystal) subscribe to this.
 */
public record PreMotionEvent(Minecraft client) {}
