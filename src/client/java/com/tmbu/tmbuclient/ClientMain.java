package com.tmbu.tmbuclient;

import net.fabricmc.api.ClientModInitializer;

public class ClientMain implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		TmbuClient.INSTANCE.initialize();
	}
}
