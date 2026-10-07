package org.cn.xiaofan.client;

import net.fabricmc.api.ClientModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NoiseGenDebugClient implements ClientModInitializer {

	public static final Logger LOGGER = LogManager.getLogger("noise_gen_debug");

	@Override
	public void onInitializeClient() {
		LOGGER.info("[noise_gen_debug] client init");
	}
}


