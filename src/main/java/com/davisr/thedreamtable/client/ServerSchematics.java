package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.farm.FarmOptions.FarmOption;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.farm.ImportNote;
import com.davisr.thedreamtable.farm.TemplateCodec;
import com.davisr.thedreamtable.network.ServerSchematicsPayload;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * On a server that runs on another computer, My Schematics lists the server's files, and each farm is fetched when the
 * hologram, clipboard or miniature first needs it. In singleplayer the client and server share the same files already.
 */
public final class ServerSchematics {
	private static final Set<String> REQUESTED = new HashSet<>();

	private ServerSchematics() {
	}

	public static void register() {
		Platform.clientNetwork().registerReceiver(ServerSchematicsPayload.ID, payload -> {
			if (onThisComputer(MinecraftClient.getInstance())) {
				return;
			}
			List<FarmOption> options = new ArrayList<>();
			Map<String, Integer> steps = new HashMap<>();
			Map<String, List<ImportNote>> notes = new HashMap<>();
			for (ServerSchematicsPayload.Entry entry : payload.schematics()) {
				options.add(new FarmOption(entry.id(), icon(entry.icon()), "farm.the_dream_table.imported", entry.name()));
				steps.put(entry.id(), entry.steps());
				if (!entry.notes().isEmpty()) {
					notes.put(entry.id(), entry.notes());
				}
			}
			REQUESTED.clear();
			FarmTemplates.useRemote(options, steps, notes, payload.rejected());
		});
		Platform.clientNetwork().registerReceiver(ServerSchematicsPayload.Farm.ID, payload -> {
			if (!onThisComputer(MinecraftClient.getInstance())) {
				FarmTemplates.putRemote(TemplateCodec.decode(payload.id(), payload.data()));
			}
		});
		FarmTemplates.onMissing(ServerSchematics::request);
		Platform.clientEvents().onJoin(client -> {
			if (!onThisComputer(client)) {
				REQUESTED.clear();
				FarmTemplates.useRemote(List.of(), Map.of(), Map.of(), List.of());
			}
		});
		Platform.clientEvents().onDisconnect(client -> {
			REQUESTED.clear();
			FarmTemplates.useLocal();
		});
	}

	private static void request(String id) {
		if (REQUESTED.add(id) && Platform.clientNetwork().canSend(ServerSchematicsPayload.Request.ID)) {
			Platform.clientNetwork().send(new ServerSchematicsPayload.Request(id));
		}
	}

	private static boolean onThisComputer(MinecraftClient client) {
		return client.isIntegratedServerRunning();
	}

	private static Item icon(String id) {
		Identifier item = Identifier.tryParse(id);
		return item != null && Registries.ITEM.containsId(item) ? Registries.ITEM.get(item) : Items.STRUCTURE_BLOCK;
	}
}
