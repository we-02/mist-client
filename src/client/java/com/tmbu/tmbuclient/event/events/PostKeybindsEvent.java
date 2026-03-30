package com.tmbu.tmbuclient.event.events;

import net.minecraft.client.Minecraft;

/** Fired right after Minecraft.handleKeybinds() — the vanilla attack timing. */
public record PostKeybindsEvent(Minecraft client) {}
