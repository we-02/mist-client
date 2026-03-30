package com.tmbu.tmbuclient.event.events;

import com.tmbu.tmbuclient.event.Cancellable;

/**
 * Fired when a vanilla nametag is about to render.
 * Cancel this event to suppress the vanilla nametag (e.g. for custom nametags).
 */
public class NameTagRenderEvent implements Cancellable {
	private boolean cancelled;

	@Override
	public boolean isCancelled() { return cancelled; }

	@Override
	public void cancel() { cancelled = true; }
}
