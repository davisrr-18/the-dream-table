package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.farm.FarmAreas;
import com.davisr.thedreamtable.farm.FarmBuilder;
import com.davisr.thedreamtable.farm.FarmDismantler;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.registry.ModBlockEntities;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import com.davisr.thedreamtable.screen.OpenData;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * The table block. It keeps one {@link TableSession} per player, so every player who uses it has their own
 * menu, farm, materials and build. The table itself only ticks the builds, saves the sessions, and syncs them to clients.
 */
public class DreamTableBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<OpenData> {
	public static final String PHASE_IDLE = "idle";
	public static final String PHASE_BUILDING = "building";
	public static final String PHASE_BLOCKED = "blocked";
	public static final String PHASE_NEED = "need";
	public static final String PHASE_PAUSED = "paused";
	public static final String PHASE_DONE = "done";

	private static final UUID SHARED = new UUID(0L, 0L);

	private final Map<UUID, TableSession> sessions = new LinkedHashMap<>();
	/** Discarded builds still being taken apart. Server only; never sent to clients. */
	private final List<FarmDismantler> dismantling = new ArrayList<>();

	public DreamTableBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.DREAM_TABLE, pos, state);
	}

	public static void tick(World world, BlockPos pos, BlockState state, DreamTableBlockEntity table) {
		if (world instanceof ServerWorld serverWorld) {
			for (TableSession session : List.copyOf(table.sessions.values())) {
				session.tick(serverWorld);
			}
			table.tickDismantling(serverWorld);
		}
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
			if (job.tick(world)) {
				FarmAreas.of(world).release(job.areaKey());
				jobs.remove();
			}
		}
		markDirty();
	}

	/**
	 * The session for this player, created on first use. A client holds only the sessions the server sent,
	 * so it gets an empty one until the player picks a farm.
	 */
	public TableSession session(UUID player) {
		TableSession session = sessions.get(player);
		if (session == null) {
			session = sessions.remove(SHARED);
			if (session == null) {
				session = new TableSession(this, player);
			} else {
				session.adopt(player);
			}
			sessions.put(player, session);
		}
		return session;
	}

	public TableSession session(PlayerEntity player) {
		return session(player.getUuid());
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

	/** When the table is broken, everyone's delivered materials drop and its farm areas are freed. Built blocks stay, even half-dismantled ones. */
	public void dropMaterials(World world) {
		for (TableSession session : sessions.values()) {
			session.dropMaterials(world);
		}
		dismantling.clear();
		if (world instanceof ServerWorld serverWorld) {
			FarmAreas.of(serverWorld).releaseTable(pos);
		}
	}

	@Override
	public OpenData getScreenOpeningData(ServerPlayerEntity player) {
		TableSession session = session(player);
		return new OpenData(pos, session.getSelectedFarm(), session.getPage(), DreamTableScreenHandler.phaseOf(session.getPhase()), session.getBuildIndex());
	}

	@Override
	public Text getDisplayName() {
		return Text.translatable("block.the_dream_table.dream_table");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new DreamTableScreenHandler(syncId, playerInventory, session(player));
	}

	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	/** Sent on chunk load and on every sync. It carries only what clients draw, so builds stay cheap to sync while blocks are placed. */
	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
		NbtCompound nbt = new NbtCompound();
		nbt.put("Sessions", sessionList(TableSession::writeForClient));
		return nbt;
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.put("Sessions", sessionList((session, entry) -> session.write(entry, registries)));
		NbtList jobs = new NbtList();
		for (FarmDismantler job : dismantling) {
			NbtCompound entry = new NbtCompound();
			job.write(entry);
			jobs.add(entry);
		}
		nbt.put("Dismantling", jobs);
	}

	private NbtList sessionList(BiConsumer<TableSession, NbtCompound> writer) {
		NbtList list = new NbtList();
		for (TableSession session : sessions.values()) {
			if (!session.isEmpty()) {
				NbtCompound entry = new NbtCompound();
				entry.putUuid("Player", session.player());
				writer.accept(session, entry);
				list.add(entry);
			}
		}
		return list;
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		sessions.clear();
		NbtList list = nbt.getList("Sessions", NbtElement.COMPOUND_TYPE);
		for (int index = 0; index < list.size(); index++) {
			NbtCompound entry = list.getCompound(index);
			if (entry.containsUuid("Player")) {
				TableSession session = new TableSession(this, entry.getUuid("Player"));
				session.read(entry, registries);
				sessions.put(session.player(), session);
			}
		}
		if (!nbt.contains("Sessions", NbtElement.LIST_TYPE)) {
			readSharedState(nbt, registries);
		}
		dismantling.clear();
		NbtList jobs = nbt.getList("Dismantling", NbtElement.COMPOUND_TYPE);
		for (int index = 0; index < jobs.size(); index++) {
			FarmDismantler job = FarmDismantler.read(jobs.getCompound(index));
			if (job != null) {
				dismantling.add(job);
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

	void tell(UUID player, Text text) {
		if (world instanceof ServerWorld serverWorld) {
			ServerPlayerEntity online = serverWorld.getServer().getPlayerManager().getPlayer(player);
			if (online != null) {
				online.sendMessage(text, true);
			}
		}
	}

	void sound(SoundEvent sound, BlockPos where, float volume, float pitch) {
		if (world != null) {
			world.playSound(null, where, sound, SoundCategory.BLOCKS, volume, pitch);
		}
	}

	void sync() {
		markDirty();
		if (world instanceof ServerWorld serverWorld) {
			serverWorld.getChunkManager().markForUpdate(pos);
		}
	}
}
