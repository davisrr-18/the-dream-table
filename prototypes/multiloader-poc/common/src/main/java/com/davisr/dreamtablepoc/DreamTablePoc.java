package com.davisr.dreamtablepoc;

import com.davisr.dreamtablepoc.network.PocNetworking;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DreamTablePoc {
	public static final String MOD_ID = "dream_table_poc";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private DreamTablePoc() {
	}

	public static void init() {
		PocContent.register();
		PocNetworking.registerCommon();

		// Logged on every start so smoke runs can prove the counter survived a restart.
		LifecycleEvent.SERVER_STARTED.register(server ->
				LOGGER.info("[poc] server started, counter={}", CounterState.get(server).value()));

		// A console command drives the same persistent state headlessly, without a client.
		CommandRegistrationEvent.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(CommandManager.literal("poccounter")
						.requires(source -> source.hasPermissionLevel(2))
						.then(CommandManager.literal("increment").executes(context -> {
							int value = CounterState.get(context.getSource().getServer()).increment();
							context.getSource().sendFeedback(() -> Text.literal("[poc] counter=" + value), false);
							return value;
						}))));
	}
}
