package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.network.TableNoticePayload;
import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.farm.FarmAreas;
import com.davisr.thedreamtable.farm.FarmBuilder;
import com.davisr.thedreamtable.farm.FarmDismantler;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.farm.ParkedProjects;
import com.davisr.thedreamtable.farm.RepairLease;
import com.davisr.thedreamtable.network.ServerSchematicsPayload;
import com.davisr.thedreamtable.network.SessionSyncPayload;
import com.davisr.thedreamtable.platform.ExtendedMenuProvider;
import com.davisr.thedreamtable.platform.Platform;
import com.davisr.thedreamtable.registry.ModBlockEntities;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import com.davisr.thedreamtable.screen.OpenData;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The table block. It keeps one {@link TableSession} per player, so every player who uses it has their own
 * menu, farm, materials and build. The table itself only ticks the builds, saves the sessions, and syncs them to clients.
 */
public class DreamTableBlockEntity extends BlockEntity implements ExtendedMenuProvider<OpenData> {
	public static final String PHASE_IDLE = "idle";
	public static final String PHASE_BUILDING = "building";
	public static final String PHASE_BLOCKED = "blocked";
	public static final String PHASE_NEED = "need";
	public static final String PHASE_PAUSED = "paused";
	public static final String PHASE_DONE = "done";

	private static final UUID SHARED = new UUID(0L, 0L);
	/** Key in {@link ParkedProjects} for an unreadable saved session that names no player. No player has this id, so nobody is offered it. */
	private static final UUID NO_PLAYER = new UUID(0L, 0L);
	private static final String CLIENT_VIEW = "ClientView";
	private static final double SEND_DISTANCE = 512.0;

	private final Map<UUID, TableSession> sessions = new LinkedHashMap<>();
	/** Discarded builds still being taken apart. Server only; never sent to clients. */
	private final List<FarmDismantler> dismantling = new ArrayList<>();
	/**
	 * Area claims of saved Take Down jobs that could not be read. Their jobs are kept as saved and the farm stands, so the
	 * claims stay until the table is broken: nothing is built over the leftovers meanwhile, and a later version that reads
	 * the job finds its claim. Server only.
	 */
	private final List<FarmAreas.Key> staleClaims = new ArrayList<>();
	/** Saved sessions and take-apart jobs this version couldn't read, written back as they were so a later version can. */
	private final List<NbtCompound> unreadableSessions = new ArrayList<>();
	private final List<NbtCompound> unreadableJobs = new ArrayList<>();
	private boolean ownSessionReceived;

	public DreamTableBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.DREAM_TABLE.get(), pos, state);
	}

	public static void tick(World world, BlockPos pos, BlockState state, DreamTableBlockEntity table) {
		if (world instanceof ServerWorld serverWorld) {
			for (TableSession session : List.copyOf(table.sessions.values())) {
				session.tick(serverWorld);
			}
			table.tickDismantling(serverWorld);
			table.sendSessions(serverWorld);
		}
	}

	/**
	 * Each owner gets only their own session, and only once per tick however often it changed. An owner too far away
	 * to have the table loaded gets nothing; their client asks again when the table loads.
	 */
	private void sendSessions(ServerWorld world) {
		for (TableSession session : sessions.values()) {
			ServerPlayerEntity owner = session.onlineOwner(world);
			// The change is consumed only when it can be delivered, so an owner who is away or dead still gets it later.
			if (owner != null && owner.getServerWorld() == world && owner.squaredDistanceTo(pos.toCenterPos()) < SEND_DISTANCE * SEND_DISTANCE
					&& session.takeClientDirty()) {
				Platform.network().send(owner, new SessionSyncPayload(pos, session.writeForClient()));
			}
		}
	}

	/** The owner's client asked for its session, after the table's chunk loaded or the menu opened. */
	public void resend(PlayerEntity player) {
		session(player).markClientDirty();
	}

	/** On the client: the server's copy of this player's session. Replaces whatever the client had. */
	public void receiveOwnSession(UUID player, NbtCompound nbt) {
		if (world == null) {
			return;
		}
		TableSession session = new TableSession(this, player);
		session.read(nbt, world.getRegistryManager());
		sessions.put(player, session);
		ownSessionReceived = true;
	}

	/** On the client: false until the server sent this player's session for this copy of the table. */
	public boolean hasOwnSession() {
		return ownSessionReceived;
	}

	void startDismantling(FarmDismantler job) {
		dismantling.add(job);
		markDirty();
	}

	private void tickDismantling(ServerWorld world) {
		if (dismantling.isEmpty()) {
			return;
		}
		Iterator<FarmDismantler> jobs = dismantling.iterator();
		while (jobs.hasNext()) {
			FarmDismantler job = jobs.next();
			boolean over;
			try {
				over = job.tick(world);
			} catch (RuntimeException exception) {
				TheDreamTableMod.LOGGER.error("Taking a farm apart at {} failed", pos, exception);
				ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(job.player());
				if (player != null) {
					player.sendMessage(Text.translatable("gui.the_dream_table.dismantle_failed"), false);
				}
				over = true;
			}
			if (over) {
				try {
					job.restoreRecord(world);
				} catch (RuntimeException exception) {
					TheDreamTableMod.LOGGER.error("Restoring a farm record at {} failed", pos, exception);
				}
				FarmAreas.of(world).release(job.areaKey());
				jobs.remove();
			}
		}
		markDirty();
	}

	/**
	 * The session for this player, created on first use. A client only ever holds its own player's session,
	 * sent by {@link #sendSessions}, and gets an empty one until it arrives.
	 */
	public TableSession session(UUID player) {
		return sessions.computeIfAbsent(player, id -> new TableSession(this, id));
	}

	/**
	 * The session a player gets as they open the table. The legacy shared session of a world saved before players had their own
	 * goes to the first player who opens it; only this path adopts it, so a payload from anyone else never takes it.
	 */
	private TableSession openedSession(UUID player) {
		if (!sessions.containsKey(player)) {
			TableSession shared = sessions.remove(SHARED);
			if (shared != null) {
				shared.adopt(player);
				sessions.put(player, shared);
			}
		}
		return session(player);
	}

	public TableSession session(PlayerEntity player) {
		return session(player.getUuid());
	}

	/** Used only after this table's chunk is loaded to prove a persisted farm lease still has its session. */
	public boolean hasRepairLease(UUID instanceId, RepairLease lease) {
		for (TableSession session : sessions.values()) {
			RepairContext context = session.repairContext();
			if (context != null && context.farmKey().instanceId().equals(instanceId)
					&& context.operationId().equals(lease.operationId()) && context.actorUuid().equals(lease.actorUuid())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * True when another player's farm at this table covers any block of this area. {@link FarmAreas} covers every table,
	 * but builds started before it existed are only known here.
	 */
	boolean overlapsOthers(TableSession asker, Box area) {
		for (TableSession other : sessions.values()) {
			FarmTemplate theirs = other.template();
			if (other != asker && theirs != null && (other.hasAnchor() || other.isBusy()) && area.intersects(FarmBuilder.site(other).footprint(theirs))) {
				return true;
			}
		}
		return false;
	}

	/** Whether anyone's build here has started and is not finished, or a take-down is still running; breaking the table ends them. */
	public boolean hasBuildInProgress() {
		return !dismantling.isEmpty() || sessions.values().stream().anyMatch(TableSession::buildInProgress);
	}

	/**
	 * What each player has at stake here, for {@link TableBreakGuard}: a running or paused build, delivered materials, or a
	 * Take Down or discard still being taken apart. Saved sessions and jobs this version couldn't read are claims of their
	 * own, which stop everyone but operators, since breaking the table would lose them.
	 */
	public List<TableBreakGuard.Claim> breakClaims() {
		List<TableBreakGuard.Claim> claims = new ArrayList<>();
		for (TableSession session : sessions.values()) {
			if (session.buildInProgress()) {
				claims.add(new TableBreakGuard.Claim(session.player(), TableBreakGuard.Reason.BUILD));
			} else if (session.holdsMaterials()) {
				claims.add(new TableBreakGuard.Claim(session.player(), TableBreakGuard.Reason.MATERIALS));
			}
		}
		for (FarmDismantler job : dismantling) {
			claims.add(new TableBreakGuard.Claim(job.player(), TableBreakGuard.Reason.TAKE_DOWN));
		}
		for (NbtCompound raw : unreadableSessions) {
			claims.add(new TableBreakGuard.Claim(namedPlayer(raw), TableBreakGuard.Reason.UNREADABLE));
		}
		for (NbtCompound raw : unreadableJobs) {
			claims.add(new TableBreakGuard.Claim(namedPlayer(raw), TableBreakGuard.Reason.UNREADABLE));
		}
		return claims;
	}

	/** The player a saved entry names, or null when it names none. */
	private static @Nullable UUID namedPlayer(NbtCompound raw) {
		return raw.containsUuid("Player") ? raw.getUuid("Player") : null;
	}

	/**
	 * When the table is broken, however that happens, each player's project is kept for them in {@link ParkedProjects} and comes
	 * back in the next table they open, and its farm areas are freed. Built blocks stay, even half-dismantled ones. Delivered
	 * materials drop only for a project that can't be kept: it belongs to no player, or its player has as many kept as allowed.
	 */
	public void onBroken(World world) {
		if (!(world instanceof ServerWorld serverWorld)) {
			return;
		}
		ParkedProjects parked = ParkedProjects.of(serverWorld.getServer());
		// Saved sessions this version cannot read are kept as they were, for a version that can; take-apart jobs are lost, so the log keeps a copy.
		for (NbtCompound raw : unreadableSessions) {
			UUID player = namedPlayer(raw);
			if (!parked.park(player != null ? player : NO_PLAYER, raw)) {
				TheDreamTableMod.LOGGER.warn("Dream Table at {} broken with an unreadable saved session that could not be kept; its data was: {}", pos, raw);
			}
		}
		for (NbtCompound raw : unreadableJobs) {
			TheDreamTableMod.LOGGER.warn("Dream Table at {} broken with an unreadable saved take-apart job; its data was: {}", pos, raw);
		}
		unreadableSessions.clear();
		RegistryWrapper.WrapperLookup registries = serverWorld.getRegistryManager();
		for (TableSession session : sessions.values()) {
			if (session.player().equals(SHARED) || !parked.canPark(session.player())) {
				// Only a project that would have been kept is worth telling about.
				ServerPlayerEntity owner = session.player().equals(SHARED) || session.parkKind() == TableSession.ParkKind.NOTHING ? null : session.onlineOwner(serverWorld);
				if (owner != null) {
					owner.sendMessage(Text.translatable("gui.the_dream_table.project_not_kept"), false);
				}
				session.dropMaterials(world);
				continue;
			}
			NbtCompound project = session.park(serverWorld, registries);
			if (project != null) {
				parked.park(session.player(), project);
			}
		}
		FarmAreas areas = FarmAreas.of(serverWorld);
		// Jobs saved before they were keyed to their own table may hold a claim under another table.
		dismantling.forEach(job -> {
			job.restoreRecord(serverWorld);
			areas.release(job.areaKey());
		});
		staleClaims.forEach(areas::release);
		areas.releaseTable(pos);
		dismantling.clear();
		staleClaims.clear();
	}

	/**
	 * The player's session as they open the table. When it holds no project of theirs, their oldest project kept from a broken
	 * table comes back into it; one this version can't read stays kept, and the next is tried. A table that holds an unreadable
	 * saved session of theirs restores nothing, since the restored one would replace it on the next save.
	 */
	private TableSession openSession(ServerPlayerEntity player) {
		TableSession session = openedSession(player.getUuid());
		UUID id = player.getUuid();
		if (!session.isEmpty() || !(world instanceof ServerWorld serverWorld)
				|| unreadableSessions.stream().anyMatch(raw -> id.equals(namedPlayer(raw)))) {
			return session;
		}
		boolean restored = ParkedProjects.of(serverWorld.getServer()).restore(id, saved -> {
			TableSession project = new TableSession(this, id);
			try {
				project.read(saved, serverWorld.getRegistryManager());
			} catch (RuntimeException corrupt) {
				TheDreamTableMod.LOGGER.error("Keeping an unreadable kept project of {} unchanged", id, corrupt);
				return false;
			}
			sessions.put(id, project);
			return true;
		});
		if (!restored) {
			return session;
		}
		session = sessions.get(id);
		session.sync();
		TableNoticePayload.info(player, session.movedBuild()
				? Text.translatable("gui.the_dream_table.project_back_build", session.getBuildIndex(), session.totalSteps())
				: Text.translatable("gui.the_dream_table.project_back"));
		if (session.checkRestoredAim(serverWorld, player)) {
			TableNoticePayload.problem(player, Text.translatable("gui.the_dream_table.aim_dropped"));
		}
		return session;
	}

	@Override
	public OpenData openingData(ServerPlayerEntity player) {
		TableSession session = openSession(player);
		return new OpenData(pos, session.getSelectedFarm(), session.getPage(), DreamTableScreenHandler.phaseOf(session.getPhase()), session.getBuildIndex());
	}

	@Override
	public Text getDisplayName() {
		return Text.translatable("block.the_dream_table.dream_table");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		TableSession session = player instanceof ServerPlayerEntity serverPlayer ? openSession(serverPlayer) : session(player);
		session.markClientDirty();
		if (player instanceof ServerPlayerEntity serverPlayer) {
			FarmTemplates.refreshImports(serverPlayer.getServer());
			ServerSchematicsPayload.send(serverPlayer);
		}
		return new DreamTableScreenHandler(syncId, playerInventory, session);
	}

	/**
	 * Chunk data carries no sessions at all, so no player can read another's. Each owner gets theirs through
	 * {@link SessionSyncPayload}; the marker tells {@link #readNbt} that this is not a save to load.
	 */
	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
		NbtCompound nbt = new NbtCompound();
		nbt.putBoolean(CLIENT_VIEW, true);
		return nbt;
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		NbtList list = new NbtList();
		Set<UUID> written = new HashSet<>();
		for (TableSession session : sessions.values()) {
			if (!session.isEmpty()) {
				NbtCompound entry = new NbtCompound();
				entry.putUuid("Player", session.player());
				session.write(entry, registries);
				list.add(entry);
				written.add(session.player());
			}
		}
		// A player who started a new session since the load must not be read back as the old one next time.
		GuardedRead.dropSuperseded(unreadableSessions, written);
		unreadableSessions.forEach(raw -> list.add(raw.copy()));
		nbt.put("Sessions", list);
		NbtList jobs = new NbtList();
		for (FarmDismantler job : dismantling) {
			NbtCompound entry = new NbtCompound();
			job.write(entry);
			jobs.add(entry);
		}
		unreadableJobs.forEach(raw -> jobs.add(raw.copy()));
		nbt.put("Dismantling", jobs);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		if (nbt.getBoolean(CLIENT_VIEW)) {
			return;
		}
		sessions.clear();
		unreadableSessions.clear();
		unreadableJobs.clear();
		NbtList list = nbt.getList("Sessions", NbtElement.COMPOUND_TYPE);
		unreadableSessions.addAll(GuardedRead.forEach(list, "session at " + pos, entry -> {
			if (!entry.containsUuid("Player")) {
				throw new IllegalArgumentException("saved session names no player");
			}
			TableSession session = new TableSession(this, entry.getUuid("Player"));
			session.read(entry, registries);
			sessions.put(session.player(), session);
		}));
		GuardedRead.dropSuperseded(unreadableSessions, sessions.keySet());
		if (!nbt.contains("Sessions", NbtElement.LIST_TYPE)) {
			readSharedState(nbt, registries);
		}
		dismantling.clear();
		staleClaims.clear();
		NbtList jobs = nbt.getList("Dismantling", NbtElement.COMPOUND_TYPE);
		for (int index = 0; index < jobs.size(); index++) {
			FarmDismantler job = FarmDismantler.read(jobs.getCompound(index));
			if (job != null) {
				dismantling.add(job);
			} else {
				unreadableJobs.add(jobs.getCompound(index).copy());
				FarmAreas.Key claim = FarmDismantler.claimOf(jobs.getCompound(index));
				if (claim != null) {
					staleClaims.add(claim);
				}
			}
		}
	}

	/** Worlds saved before sessions kept one shared state. It goes to the player who started its build, or else to whoever opens the table first. */
	private void readSharedState(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		TableSession shared = new TableSession(this, SHARED);
		shared.read(nbt, registries);
		if (shared.isEmpty()) {
			return;
		}
		UUID owner = shared.savedOwner();
		shared.adopt(owner != null ? owner : SHARED);
		sessions.put(shared.player(), shared);
	}

	void tell(UUID player, Text text, TableNoticePayload.Severity severity) {
		if (world instanceof ServerWorld serverWorld) {
			ServerPlayerEntity online = serverWorld.getServer().getPlayerManager().getPlayer(player);
			if (online != null) {
				TableNoticePayload.send(online, text, severity);
			}
		}
	}

	void sound(SoundEvent sound, BlockPos where, float volume, float pitch) {
		if (world != null) {
			world.playSound(null, where, sound, SoundCategory.BLOCKS, volume, pitch);
		}
	}


}
