package com.tmbu.tmbuclient.event.events;

import net.minecraft.world.entity.player.Player;

/**
 * Fired when a player pops a totem of undying.
 */
public record TotemPopEvent(Player player) {}
