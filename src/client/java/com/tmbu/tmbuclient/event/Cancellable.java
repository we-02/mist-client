package com.tmbu.tmbuclient.event;

/**
 * Marker interface for events that can be cancelled.
 * When cancelled, no further listeners will be notified.
 */
public interface Cancellable {
	boolean isCancelled();
	void cancel();
}
