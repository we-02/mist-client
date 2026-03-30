package com.tmbu.tmbuclient.event.events;

import net.minecraft.world.entity.Entity;

/**
 * Fired when the local player attacks an entity.
 * Used by KeepSprint and SuperKnockback.
 */
public record AttackEntityEvent(Entity target) {}
