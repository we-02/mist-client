package com.tmbu.tmbuclient.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Lightweight event bus for decoupling modules from mixins and other systems.
 * Modules subscribe to events they care about; mixins post events without
 * knowing which modules exist.
 */
public final class EventBus {
	public static final EventBus INSTANCE = new EventBus();

	private final Map<Class<?>, List<PrioritizedListener<?>>> listeners = new ConcurrentHashMap<>();

	private EventBus() {}

	/**
	 * Subscribe to an event type with default priority (0).
	 */
	public <T> void subscribe(Class<T> eventType, Consumer<T> listener) {
		subscribe(eventType, 0, listener);
	}

	/**
	 * Subscribe to an event type with a priority. Lower values run first.
	 */
	public <T> void subscribe(Class<T> eventType, int priority, Consumer<T> listener) {
		listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
			.add(new PrioritizedListener<>(priority, listener));
		// Re-sort after adding
		listeners.get(eventType).sort(null);
	}

	/**
	 * Unsubscribe a listener.
	 */
	public <T> void unsubscribe(Class<T> eventType, Consumer<T> listener) {
		List<PrioritizedListener<?>> list = listeners.get(eventType);
		if (list != null) {
			list.removeIf(pl -> pl.listener == listener);
		}
	}

	/**
	 * Post an event to all subscribers.
	 * Returns the event so callers can check cancellation state.
	 */
	@SuppressWarnings("unchecked")
	public <T> T post(T event) {
		List<PrioritizedListener<?>> list = listeners.get(event.getClass());
		if (list != null) {
			for (PrioritizedListener<?> pl : list) {
				if (event instanceof Cancellable c && c.isCancelled()) break;
				((Consumer<T>) pl.listener).accept(event);
			}
		}
		return event;
	}

	private record PrioritizedListener<T>(int priority, Consumer<T> listener)
		implements Comparable<PrioritizedListener<?>> {
		@Override
		public int compareTo(PrioritizedListener<?> o) {
			return Integer.compare(this.priority, o.priority);
		}
	}
}
