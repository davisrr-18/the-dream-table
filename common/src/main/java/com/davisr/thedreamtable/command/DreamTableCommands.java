package com.davisr.thedreamtable.command;

import com.davisr.thedreamtable.DreamTableConfig;
import com.davisr.thedreamtable.farm.FarmAreas;
import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.farm.ImportedStructures;
import com.davisr.thedreamtable.platform.Platform;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.List;

/**
 * The server commands, for operators (permission level 2): {@code /dreamtable reload} reads the schematic folders and the
 * configuration again without a restart, and {@code /dreamtable status} prints what the mod is holding.
 */
public final class DreamTableCommands {
	/** The vanilla operator level, like {@code FarmProtection.OPERATOR_LEVEL}. */
	static final int OPERATOR_LEVEL = 2;

	private DreamTableCommands() {
	}

	public static void register() {
		Platform.serverEvents().onCommandRegistration(DreamTableCommands::install);
	}

	static void install(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("dreamtable")
				.requires(source -> source.hasPermissionLevel(OPERATOR_LEVEL))
				.then(CommandManager.literal("reload").executes(DreamTableCommands::reload))
				.then(CommandManager.literal("status").executes(DreamTableCommands::status)));
	}

	/**
	 * The configuration is read at once and the schematic folders on a worker thread; the counts follow when the read is
	 * done, so the server never waits for big files. Farms already built are not touched.
	 */
	private static int reload(CommandContext<ServerCommandSource> context) {
		ServerCommandSource source = context.getSource();
		MinecraftServer server = source.getServer();
		boolean started = FarmTemplates.reloadImportsAsync(server,
				imports -> source.sendFeedback(() -> reloaded(imports), false),
				failure -> source.sendError(Text.translatable("gui.the_dream_table.command_reload_failed")));
		if (!started) {
			source.sendError(Text.translatable("gui.the_dream_table.command_reload_busy"));
			return 0;
		}
		DreamTableConfig.load();
		FarmLoader.requestRenewal();
		source.sendFeedback(() -> Text.translatable("gui.the_dream_table.command_reload_started"), false);
		return 1;
	}

	static Text reloaded(ImportedStructures.Imports imports) {
		return Text.translatable("gui.the_dream_table.command_reload_done", imports.loaded(), imports.rejected().size(), imports.warningCount());
	}

	private static int status(CommandContext<ServerCommandSource> context) {
		ServerCommandSource source = context.getSource();
		MinecraftServer server = source.getServer();
		FarmAreas.Stats total = new FarmAreas.Stats(0, 0, 0, 0, 0, 0);
		for (ServerWorld world : server.getWorlds()) {
			total = total.plus(FarmAreas.of(world).stats());
		}
		ImportedStructures.Imports imports = FarmTemplates.importsSnapshot();
		for (Text line : statusLines(total, FarmLoader.of(server).status(), imports)) {
			source.sendFeedback(() -> line, false);
		}
		return 1;
	}

	static List<Text> statusLines(FarmAreas.Stats areas, FarmLoader.Status loader, ImportedStructures.Imports imports) {
		return List.of(
				Text.translatable("gui.the_dream_table.command_status_header"),
				Text.translatable("gui.the_dream_table.command_status_farms", areas.finished()),
				Text.translatable("gui.the_dream_table.command_status_active", areas.builds(), areas.repairs(), areas.takeDowns()),
				Text.translatable("gui.the_dream_table.command_status_areas", areas.areas()),
				Text.translatable("gui.the_dream_table.command_status_loaded", loader.keptFarms(), loader.keptChunks(), loader.picked()),
				Text.translatable("gui.the_dream_table.command_status_unreadable", areas.unreadable()),
				Text.translatable("gui.the_dream_table.command_status_schematics", imports.loaded(), imports.rejected().size(), imports.warningCount()));
	}
}
