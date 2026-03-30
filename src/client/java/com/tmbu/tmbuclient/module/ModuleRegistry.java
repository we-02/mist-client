package com.tmbu.tmbuclient.module;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Central registry for module factories. New modules register themselves here
 * (typically in a static block or from a single registration class) so that
 * TmbuClient doesn't need to import every module individually.
 *
 * To add a new module, just add one line to {@link Modules#register()}.
 */
public final class ModuleRegistry {
	private static final List<Supplier<Module>> factories = new ArrayList<>();

	private ModuleRegistry() {}

	/**
	 * Register a module factory. Called during initialization.
	 */
	public static void add(Supplier<Module> factory) {
		factories.add(factory);
	}

	/**
	 * Create all registered modules and register them with the manager.
	 */
	public static void registerAll(ModuleManager manager) {
		Module[] modules = factories.stream()
			.map(Supplier::get)
			.toArray(Module[]::new);
		manager.register(modules);
	}
}
