package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import com.davisr.thedreamtable.network.TableNoticePayload;
import com.davisr.thedreamtable.DreamTableConfig;
import com.davisr.thedreamtable.farm.FarmAreas;
import com.davisr.thedreamtable.farm.BuildPace;
import com.davisr.thedreamtable.farm.FarmBuilder;
import com.davisr.thedreamtable.farm.FarmDismantler;
import com.davisr.thedreamtable.farm.FarmGeometry;
import com.davisr.thedreamtable.farm.FarmSite;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.farm.ParkedProjects;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.davisr.thedreamtable.block.entity.DreamTableBlockEntity.*;

/**
 * One player's use of a table: the menu page, the chosen farm, what was delivered ({@link MaterialDesk}), the placement,
 * the build ({@link SessionBuild}, {@link BuildProgress}) and its area ({@link SessionAreas}). A table keeps one session per player, so two
 * players at the same table never share or disturb each other's state. Only its owner's client ever receives it.
 * Reads and writes use the same keys the table used before sessions existed, so older worlds still load.
 */
public final class TableSession {
	private static final int MAX_SKIPS_PER_TICK = 4096;
	/** The Alerts page of earlier 1.1.0 builds, gone now; a table saved on it reopens on the farm page. */
	private static final int REMOVED_INVITES_PAGE = 20;
	private static final int REMOVED_ALERTS_PAGE = 21;

	private final DreamTableBlockEntity table;
	private final MaterialDesk desk;
	private final BuildProgress progress = new BuildProgress();
	private final FarmPlacement placement = new FarmPlacement();
	private final SessionAreas areas = new SessionAreas(this, progress);
	private final SessionBuild build = new SessionBuild(this, progress, areas);
	private UUID player;
	private String selectedFarm = "";
	private int page;
	private int swapMaterial = -1;
	/**
	 * The dimension a started build stands in when it came back from a broken table, until it resumes from this one; null
	 * for a build this table started. Saved, so the build can't be resumed or taken apart in the wrong world.
	 */
	private @Nullable Identifier movedBuildDimension;
	/** The spot aimed at and not built yet. Kept with the project when the table breaks; see {@link AimedSpot}. */
	private @Nullable AimedSpot aim;
	private boolean clientDirty = true;

	TableSession(DreamTableBlockEntity table, UUID player) {
		this.table = table;
		this.player = player;
		this.desk = new MaterialDesk(this::template, table::markDirty);
	}

	public UUID player() {
		return player;
	}

	DreamTableBlockEntity table() {
		return table;
	}

	/** A session saved before players had their own is handed to the first player who opens the table. */
	void adopt(UUID newPlayer) {
		player = newPlayer;
	}

	/** An unused session is not saved. */
	boolean isEmpty() {
		return selectedFarm.isEmpty() && !desk.holdsItems();
	}

	// Table access, so farm code can treat a session like the table it belongs to.

	public BlockPos getPos() {
		return table.getPos();
	}

	public BlockState getCachedState() {
		return table.getCachedState();
	}

	public UUID getOwner() {
		return player;
	}

	/** The player, when online and alive: refunds to a dead player are lost on respawn. Builds wait while they are away, since their permissions and game mode decide every step. */
	public ServerPlayerEntity onlineOwner(ServerWorld world) {
		ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(player);
		return owner != null && owner.isAlive() ? owner : null;
	}

	void tick(ServerWorld world) {
		if (!progress.returnNotice().isEmpty()) {
			ServerPlayerEntity online = onlineOwner(world);
			if (online != null && returnPending(online)) {
				return;
			}
		}
		if (retired()) {
			ServerPlayerEntity owner = onlineOwner(world);
			if (owner != null) {
				discard(owner);
				owner.sendMessage(Text.translatable("gui.the_dream_table.farm_retired"), false);
			}
			return;
		}
		resumeForWater(world);
		build.tick(world);
	}

	/** A build stopped for want of water goes on by itself once a source is nearby. Checked once a second. */
	private void resumeForWater(ServerWorld world) {
		BuildIssue issue = progress.issue();
		FarmTemplate template = template();
		ServerPlayerEntity owner = onlineOwner(world);
		if (owner == null || !retriesForWater(progress.is(PHASE_BLOCKED) || progress.is(PHASE_NEED), issue != null && issue.kind() == BuildIssue.Kind.WATER,
				world.getTime() % BuildPace.TICKS_PER_SECOND == 0, movedBuild(), template != null, template != null && progress.matches(template.signature()))) {
			return;
		}
		if (FarmBuilder.check(this, world) == null) {
			build.begin(owner, false);
		}
	}

	/**
	 * Whether a stopped water build is retried this second. Only a build that {@link SessionBuild#begin} would accept is retried:
	 * a moved build waits for its player's Continue, and a build whose farm file is gone or changed stays as it is. Otherwise
	 * each retry would send the same refusal again.
	 */
	static boolean retriesForWater(boolean stopped, boolean waterIssue, boolean secondTick, boolean movedBuild, boolean hasTemplate, boolean signatureMatches) {
		return stopped && waterIssue && secondTick && !movedBuild && hasTemplate && signatureMatches;
	}

	/** A built-in farm that left the mod in an update. Its project is dropped and the delivered items handed back. */
	private boolean retired() {
		return !selectedFarm.isEmpty() && !FarmTemplates.isImported(selectedFarm) && template() == null;
	}

	// Farm choice and menu

	public String getSelectedFarm() {
		return selectedFarm;
	}

	public FarmTemplate template() {
		return FarmTemplates.get(selectedFarm);
	}

	/**
	 * Chooses another farm. Items still delivered, left over when a finished build skipped steps, go back to the receiver
	 * first, or drop at the table when there is none; the farm they were delivered for is still the selected one.
	 */
	public void setSelectedFarm(String farm, @Nullable PlayerEntity receiver) {
		releaseArea();
		if (desk.holdsItems() && table.getWorld() != null) {
			desk.returnAll(table.getWorld(), getPos(), receiver);
		}
		selectedFarm = farm == null ? "" : farm;
		desk.reset();
		progress.reset(false);
		swapMaterial = -1;
		placement.clear();
		releaseHold();
		movedBuildDimension = null;
		aim = null;
		sync();
	}

	/** True while the farm has a placement, delivered items, or a build that is not finished. */
	public boolean hasProgress() {
		return !progress.is(PHASE_DONE) && (progress.busy() || placement.placed() || desk.anyDelivered());
	}

	public int getPage() {
		return page;
	}

	public void setPage(int page) {
		this.page = page;
		sync();
	}

	public SimpleInventory getMaterials() {
		return desk.slots();
	}

	// Material swaps

	public Item resolve(Item original) {
		return desk.resolve(original);
	}

	public BlockState swapState(BlockState state) {
		return desk.swapState(state);
	}

	Map<Item, Item> swaps() {
		return desk.swaps();
	}

	void restoreSwaps(Map<Item, Item> saved) {
		desk.restoreSwaps(saved);
		sync();
	}

	/** The material swaps as a stable text, so previews built from them can be cached and rebuilt when they change. */
	public String swapKey() {
		StringBuilder key = new StringBuilder();
		desk.swaps().entrySet().stream()
				.map(swap -> Registries.ITEM.getId(swap.getKey()) + ">" + Registries.ITEM.getId(swap.getValue()))
				.sorted()
				.forEach(swap -> key.append(swap).append(';'));
		return key.toString();
	}

	public List<Item> swapOptions(int index) {
		return desk.swapOptions(index);
	}

	/** A material can be swapped only before any of it is delivered and before the build starts. */
	public boolean canSwap(int index) {
		FarmTemplate template = template();
		boolean valid = template != null && index >= 0 && index < template.materials().size();
		return valid && progress.is(PHASE_IDLE) && progress.index() == 0 && desk.untouched(index);
	}

	public boolean setSwap(int index, int option) {
		if (!canSwap(index) || !desk.swap(index, option)) {
			return false;
		}
		sync();
		return true;
	}

	public int getSwapMaterial() {
		return swapMaterial;
	}

	public void setSwapMaterial(int index) {
		swapMaterial = index;
		sync();
	}

	// Placement

	public boolean hasAnchor() {
		return placement.placed();
	}

	public BlockPos getFarmAnchor() {
		return placement.anchor();
	}

	public Direction getFarmFacing() {
		return placement.facing();
	}

	public boolean getFarmMirrored() {
		return placement.mirrored();
	}

	public void setPlacement(BlockPos anchor, Direction facing, boolean mirrored) {
		placement.set(anchor, facing, mirrored);
		sync();
	}

	public void clearPlacement() {
		placement.clear();
		sync();
	}

	/** The spot the hologram was last aimed at and not built yet, or null. Dropped when a build starts, or the farm changes. */
	public @Nullable AimedSpot aim() {
		return aim;
	}

	/** Remembers where the hologram was aimed, so a broken table keeps it. Not a placement: nothing is reserved or started. */
	public void rememberAim(Identifier dimension, BlockPos anchor, Direction facing, boolean mirrored) {
		AimedSpot spot = new AimedSpot(anchor, facing, mirrored, dimension);
		if (!spot.equals(aim)) {
			aim = spot;
			sync();
		}
	}

	void clearAim() {
		if (aim != null) {
			aim = null;
			sync();
		}
	}

	/**
	 * Keeps the aimed spot a project came back with only while it is still a spot from this table. Otherwise it is dropped and
	 * the farm and its materials stay. Returns true when it was dropped.
	 */
	boolean checkRestoredAim(ServerWorld world, ServerPlayerEntity player) {
		if (aim == null) {
			return false;
		}
		boolean sameWorld = aim.dimension().equals(world.getRegistryKey().getValue());
		boolean free = problemAt(world, player, new FarmSite(getPos(), aim.anchor(), aim.facing(), aim.mirrored())) == null;
		boolean usable = restoredAimUsable(template() != null, sameWorld, FarmGeometry.withinReach(getPos(), aim.anchor()), free);
		if (!usable) {
			aim = null;
			sync();
		}
		return !usable;
	}

	/**
	 * Whether a restored aimed spot is still usable: this table has a farm to place, the spot is in its world and reach, and
	 * it passes the placement checks ({@code free}).
	 */
	static boolean restoredAimUsable(boolean hasTemplate, boolean sameWorld, boolean withinReach, boolean free) {
		return hasTemplate && AimedSpot.usableHere(sameWorld, withinReach) && free;
	}

	/**
	 * Why a repair of this farm can't start at its site, as a lang key, or null. It is asked before the session takes the farm
	 * on, so a refused repair changes nothing. The farm's dimension rule reads the selected farm, so the question is asked with
	 * the repaired farm selected, and the selection is put back at once.
	 */
	String repairProblem(ServerWorld world, ServerPlayerEntity player, FarmTemplate template, FarmSite site, UUID instanceId) {
		if (!progress.returnNotice().isEmpty()) {
			return "gui.the_dream_table.repair_busy";
		}
		String chosen = selectedFarm;
		selectedFarm = template.id();
		try {
			return areas.problemAt(world, player, site, template, instanceId);
		} finally {
			selectedFarm = chosen;
		}
	}

	/** Why the farm can't start at the site, as a lang key, or null. See {@link SessionAreas#problemAt}. */
	public String problemAt(ServerWorld world, ServerPlayerEntity asker, FarmSite site) {
		FarmTemplate template = template();
		return template == null ? null : areas.problemAt(world, asker, site, template);
	}

	public int existingPercent(ServerWorld world, FarmSite site) {
		FarmTemplate template = template();
		return template == null ? 0 : areas.existingPercent(world, site, template);
	}

	// Build progress

	public String getPhase() {
		return progress.phase();
	}

	public boolean isBusy() {
		return progress.busy();
	}

	/** Whether the player has delivered items, or items in the material slots, that breaking the table would drop. */
	boolean holdsMaterials() {
		return desk.holdsItems();
	}

	/** A started build that isn't finished, running or stopped partway; a repair counts too. */
	boolean buildInProgress() {
		return !selectedFarm.isEmpty() && (progress.busy() || (progress.index() > 0 && !progress.is(PHASE_DONE)));
	}

	/** Starting, resuming, pacing and stopping the build. */
	public SessionBuild build() {
		return build;
	}

	RepairContext repairContext() {
		return progress.repairContext();
	}

	public int getBuildIndex() {
		return progress.index();
	}

	public int totalSteps() {
		FarmTemplate template = template();
		return template == null ? 0 : template.steps().size();
	}

	// Delivered materials

	/**
	 * Hands back deliveries made for a design this farm no longer has (the farm file changed under them), to the receiver
	 * or at the table, so they can't block new deliveries or make the materials look complete. Checked whenever the player
	 * acts on the table, since nothing else notices the file changing.
	 */
	public void reconcileMaterials(@Nullable PlayerEntity receiver) {
		if (receiver instanceof ServerPlayerEntity returning && returnPending(returning)) {
			return;
		}
		if (table.getWorld() != null && desk.returnIncompatible(table.getWorld(), getPos(), receiver)) {
			sync();
		}
	}

	/** Items put into the Requirements slots count at once; a build that waits for exactly this block continues right away. */
	public void resumeAfterSlots(ServerPlayerEntity giver) {
		continueIfWaiting(giver);
	}

	/** Moves everything the farm still needs from the player's inventory and offhand. */
	public boolean deliverAll(ServerPlayerEntity giver) {
		if (!canDeliver(giver)) {
			return false;
		}
		MaterialDelivery.Tally tally = MaterialDelivery.pull(giver.getInventory(), MaterialDelivery.PLAYER_SLOTS, receiver());
		return finishBulk(giver, tally, false, 0);
	}

	/**
	 * Moves what the farm still needs from usable containers within a few blocks of the table. The server setting decides
	 * whether it is available at all; the player's own switch only hides the button.
	 */
	public boolean deliverNearby(ServerPlayerEntity giver) {
		if (!canDeliver(giver) || !(table.getWorld() instanceof ServerWorld world)) {
			return false;
		}
		if (!DreamTableConfig.deliverFromNearby()) {
			TableNoticePayload.problem(giver, Text.translatable("gui.the_dream_table.deliver_nearby_off_server"));
			return false;
		}
		if (!MaterialDelivery.NEARBY_REQUESTS.allow(giver.getUuid(), world.getServer().getTicks())) {
			TableNoticePayload.problem(giver, Text.translatable("gui.the_dream_table.deliver_nearby_wait"));
			return false;
		}
		NearbyContainers.Found found = NearbyContainers.find(world, giver, getPos());
		MaterialDelivery.Receiver receiver = receiver();
		MaterialDelivery.Tally tally = MaterialDelivery.Tally.NONE;
		for (Inventory source : found.inventories()) {
			tally = tally.plus(MaterialDelivery.pull(source, MaterialDelivery.ALL_SLOTS, receiver));
		}
		return finishBulk(giver, tally, true, found.skipped());
	}

	private boolean canDeliver(ServerPlayerEntity giver) {
		if (template() == null || giver.isSpectator()) {
			return false;
		}
		if (giver.getAbilities().creativeMode) {
			TableNoticePayload.problem(giver, Text.translatable("gui.the_dream_table.deliver_creative"));
			return false;
		}
		return true;
	}

	private MaterialDelivery.Receiver receiver() {
		return new MaterialDelivery.Receiver() {
			@Override
			public int accept(ItemStack stack) {
				return desk.accept(stack, false);
			}

			@Override
			public boolean wants(ItemStack stack) {
				return desk.wants(stack);
			}
		};
	}

	private boolean finishBulk(ServerPlayerEntity giver, MaterialDelivery.Tally tally, boolean nearby, int skippedContainers) {
		TableNoticePayload.send(giver, MaterialDelivery.summary(tally, nearby, skippedContainers), MaterialDelivery.severity(tally));
		if (tally.delivered() <= 0) {
			return false;
		}
		finishDelivery(giver);
		return true;
	}

	/** The shared end of every delivery: sync, sound, and continuing a build that waits for exactly what arrived. */
	private void finishDelivery(PlayerEntity giver) {
		sync();
		if (table.getWorld() != null) {
			table.getWorld().playSound(null, getPos(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.5F, 1.15F);
		}
		if (giver instanceof ServerPlayerEntity deliverer) {
			continueIfWaiting(deliverer);
		}
	}

	private void continueIfWaiting(ServerPlayerEntity deliverer) {
		if (progress.is(PHASE_NEED) && nextStepReady()) {
			build.begin(deliverer, false);
		}
	}

	private boolean nextStepReady() {
		FarmTemplate template = template();
		if (template == null || progress.index() >= template.steps().size()) {
			return false;
		}
		Item needed = template.steps().get(progress.index()).item();
		return needed == null || desk.has(needed);
	}

	public boolean hasAllMaterials() {
		return desk.complete();
	}

	public int deposited(int index) {
		return desk.deposited(index);
	}

	public int supplied(int index) {
		return desk.supplied(index);
	}

	public int stored(Item item) {
		return desk.stored(item);
	}

	public boolean hasMaterial(Item item) {
		return desk.has(item);
	}

	/** Water near the table, read at most once per world tick: a step asks several times, and each answer is a full scan. Not saved. */
	private long waterTick = Long.MIN_VALUE;
	private boolean waterNear;

	/** Whether infinite water is within reach of the table, outside every Dream Table farm. See {@link NearbyWater}. */
	public boolean hasInfiniteWater(ServerWorld world) {
		if (world.getTime() != waterTick) {
			waterTick = world.getTime();
			waterNear = NearbyWater.sourceNear(world, getPos());
		}
		return waterNear;
	}

	/** Uses one delivered item. Returns true when it was one the player paid for, so the step can be refunded later. */
	public boolean spendMaterial(Item item) {
		boolean paid = desk.spend(item);
		table.markDirty();
		return paid;
	}

	/**
	 * Gives back the delivered items and frees the session at once. An unfinished build is handed to a
	 * {@link FarmDismantler}, which takes it apart over the next ticks; a finished farm stays standing.
	 */
	public void discard(PlayerEntity discarder) {
		discard(discarder.getWorld(), discarder, true);
	}

	/**
	 * Ends a repair that can't go on, its actor's access gone or its lease lost. Unlike a discard it takes nothing apart:
	 * what the repair placed stays in the farm and is added to the farm's record when that still exists, the lease is
	 * released, and only the delivered items it did not spend come back. A player who is away gets them when they return.
	 */
	void cancelRepair(ServerWorld world, String messageKey) {
		ServerPlayerEntity actor = onlineOwner(world);
		RepairContext context = progress.repairContext();
		if (context != null) {
			FarmAreas.of(world).mergePlacement(context.farmKey().instanceId(), progress.placedSteps(), progress.paidSteps());
		}
		if (actor != null) {
			actor.sendMessage(Text.translatable(messageKey), false);
			discard(world, actor, false);
			return;
		}
		// The actor is away: the repair ends now, so the owner can take the farm down, but the materials stay with the actor.
		// Their farm stays selected, so the ledger still matches it, and they are handed back when the actor returns.
		// The anchor is cleared so the waiting session doesn't hold the farm's footprint against other repairs here.
		releaseArea();
		placement.clear();
		progress.reset(true);
		progress.setReturnNotice(messageKey);
		sync();
	}

	/**
	 * Hands back what a repair that ended while this player was away left in their session, and tells them why.
	 * Returns true when it did, which also frees the session. Checked on every tick the player is online and before they act.
	 */
	boolean returnPending(ServerPlayerEntity returning) {
		String notice = progress.returnNotice();
		if (notice.isEmpty()) {
			return false;
		}
		returning.sendMessage(Text.translatable(notice), false);
		discard(returning.getWorld(), returning, false);
		return true;
	}

	private void discard(World world, @Nullable PlayerEntity discarder, boolean takeApart) {
		FarmTemplate template = template();
		if (takeApart && table.getWorld() instanceof ServerWorld serverWorld && template != null && !progress.is(PHASE_DONE) && progress.index() > 0) {
			if (movedBuildDimension != null && !movedBuildHere(serverWorld)) {
				// A build from a broken table that stands in another world or out of reach is not this table's to take apart.
				if (discarder instanceof ServerPlayerEntity serverDiscarder) {
					TableNoticePayload.info(serverDiscarder, Text.translatable("gui.the_dream_table.moved_build_left_standing"));
				}
			} else if (!progress.matches(template.signature())) {
				if (discarder != null) {
					if (discarder instanceof ServerPlayerEntity serverDiscarder) {
						TableNoticePayload.problem(serverDiscarder, Text.translatable("gui.the_dream_table.discard_changed"));
					}
				}
			} else {
				FarmSite site = FarmBuilder.site(this);
				FarmDismantler job = new FarmDismantler(player, selectedFarm, template.signature(), site, desk.swaps(),
						progress.placedSteps(), progress.paidSteps(), progress.index());
				FarmAreas.of(serverWorld).claim(job.areaKey(), site.footprint(template));
				table.startDismantling(job);
				if (discarder != null) {
					if (discarder instanceof ServerPlayerEntity serverDiscarder) {
						TableNoticePayload.info(serverDiscarder, Text.translatable("gui.the_dream_table.dismantling"));
					}
				}
			}
		}
		releaseArea();
		desk.returnAll(world, getPos(), discarder);
		progress.reset(true);
		page = 0;
		selectedFarm = "";
		desk.reset();
		swapMaterial = -1;
		placement.clear();
		releaseHold();
		movedBuildDimension = null;
		aim = null;
		sync();
	}

	/** For a project the broken table can't keep: what a repair placed is merged into the farm's record, as {@link #park} does. */
	void dropMaterials(World world) {
		RepairContext repair = progress.repairContext();
		if (repair != null && world instanceof ServerWorld serverWorld) {
			FarmAreas.of(serverWorld).mergePlacement(repair.farmKey().instanceId(), progress.placedSteps(), progress.paidSteps());
		}
		desk.returnAll(world, getPos(), null);
		releaseArea();
	}

	/**
	 * The project as it is kept for its player when the table is broken ({@link com.davisr.thedreamtable.farm.ParkedProjects}),
	 * or null when there is nothing to keep. A started build with a placement is kept paused at its spot, with the dimension
	 * it stands in. A repair ends like {@link #cancelRepair}, its placed blocks merged into the farm's record. Anything else
	 * keeps only the farm and what was delivered, since a spot picked from this table may be out of reach of the next one.
	 * The area is freed and the live session emptied of its items, so nothing is handed out twice.
	 */
	@Nullable NbtCompound park(ServerWorld world, RegistryWrapper.WrapperLookup registries) {
		RepairContext repair = progress.repairContext();
		ParkKind kind = parkKind();
		if (kind == ParkKind.NOTHING) {
			releaseArea();
			return null;
		}
		if (repair != null) {
			FarmAreas.of(world).mergePlacement(repair.farmKey().instanceId(), progress.placedSteps(), progress.paidSteps());
		}
		releaseArea();
		if (kind == ParkKind.BUILD) {
			if (progress.is(PHASE_BUILDING)) {
				progress.stop(PHASE_PAUSED);
			}
			if (movedBuildDimension == null) {
				movedBuildDimension = world.getRegistryKey().getValue();
			}
		} else {
			// Materials a repair left for an actor who was away still come back to them, with the same message.
			String returnNotice = progress.returnNotice();
			progress.reset(true);
			progress.setReturnNotice(returnNotice);
			desk.forgetUsed();
			placement.clear();
			movedBuildDimension = null;
		}
		NbtCompound nbt = new NbtCompound();
		write(nbt, registries);
		desk.slots().clear();
		desk.reset();
		return nbt;
	}

	/** What a broken table would keep of this session now. */
	ParkKind parkKind() {
		return parkKind(isEmpty(), progress.is(PHASE_DONE), desk.holdsItems(), progress.repairContext() != null, buildInProgress(), placement.placed());
	}

	/** What a broken table keeps of a session. */
	enum ParkKind {
		NOTHING,
		/** The started build, paused at its spot. */
		BUILD,
		/** The farm and what was delivered, without a spot or build progress. */
		PROJECT
	}

	/** An unused session or a finished farm with nothing left delivered is not kept; a repair is never kept as a build. */
	static ParkKind parkKind(boolean empty, boolean done, boolean holdsItems, boolean repair, boolean buildStarted, boolean placed) {
		if (empty || done && !holdsItems) {
			return ParkKind.NOTHING;
		}
		return !repair && buildStarted && placed ? ParkKind.BUILD : ParkKind.PROJECT;
	}

	/**
	 * The area a kept project reserves, from its saved spot and farm: only a build keeps one, and it is the box its farm
	 * occupies at the anchor, in the world it stands in. Null for a project without a spot, or whose farm this server does not know.
	 */
	public static @Nullable ParkedProjects.Reservation keptReservation(NbtCompound project) {
		Identifier world = project.contains("MovedBuildDimension", NbtElement.STRING_TYPE)
				? Identifier.tryParse(project.getString("MovedBuildDimension")) : null;
		FarmTemplate template = world == null || !project.contains("AnchorX", NbtElement.INT_TYPE)
				? null : FarmTemplates.get(project.getString("SelectedFarm"));
		if (template == null) {
			return null;
		}
		FarmPlacement placement = new FarmPlacement();
		placement.read(project);
		// The anchor is what places the farm, so the table position, which a kept project does not save, is never read.
		FarmSite site = new FarmSite(placement.anchor(), placement.anchor(), placement.facing(), placement.mirrored());
		return new ParkedProjects.Reservation(world, site.footprint(template));
	}

	/** The seat a build restored into this table is held in: this owner at this table, in its world. */
	ParkedProjects.Seat seat(ServerWorld world) {
		return new ParkedProjects.Seat(player(), world.getRegistryKey().getValue(), getPos().asLong());
	}

	/**
	 * Lifts the hold of a build restored into this table, when it is resumed (claimed), discarded (taken apart) or the table breaks
	 * (parked again). Does nothing when nothing is held.
	 */
	void releaseHold() {
		if (table.getWorld() instanceof ServerWorld world) {
			ParkedProjects.of(world.getServer()).release(seat(world));
		}
	}

	/** Whether this is a started build that came back from a broken table and has not resumed here yet. */
	boolean movedBuild() {
		return movedBuildDimension != null;
	}

	/** Whether the moved build stands in this table's world and within the reach of this table. */
	private boolean movedBuildHere(ServerWorld world) {
		return movedBuildBlocker(world) == null;
	}

	private @Nullable String movedBuildBlocker(ServerWorld world) {
		return movedBuildBlocker(world.getRegistryKey().getValue().equals(movedBuildDimension),
				placement.placed() && FarmGeometry.withinReach(getPos(), placement.anchor()));
	}

	/**
	 * Why a build that came back from a broken table can't resume from this one, as a lang key, or null when it can: it stands
	 * in another world, out of this table's reach, or its spot no longer passes {@link SessionAreas#problemAt}.
	 */
	@Nullable String movedBuildProblem(ServerWorld world, ServerPlayerEntity asker, FarmTemplate template) {
		String blocker = movedBuildBlocker(world);
		return blocker != null ? blocker : areas.resumeProblemAt(world, asker, FarmBuilder.site(this), template);
	}

	/** Why a moved build can't be worked on from this table, before its spot is checked, as a lang key, or null. */
	static @Nullable String movedBuildBlocker(boolean sameWorld, boolean withinReach) {
		if (!sameWorld) {
			return "gui.the_dream_table.moved_build_other_world";
		}
		return withinReach ? null : "gui.the_dream_table.moved_build_too_far";
	}

	/** The moved build resumed from this table and is now this table's build. */
	void settleMovedBuild() {
		movedBuildDimension = null;
	}

	private void releaseArea() {
		if (table.getWorld() instanceof ServerWorld world) {
			areas.release(world);
		}
	}

	// Saving and syncing

	void write(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		nbt.putString("SelectedFarm", selectedFarm);
		nbt.putInt("Page", page);
		nbt.putInt("SwapMaterial", swapMaterial);
		desk.write(nbt, registries);
		progress.write(nbt);
		placement.write(nbt);
		if (movedBuildDimension != null) {
			nbt.putString("MovedBuildDimension", movedBuildDimension.toString());
		}
		if (aim != null) {
			nbt.put("Aim", aim.toNbt());
		}
	}

	/** The trimmed copy sent to the owner's client: no slot inventory, no step records, no page, which the menu syncs on its own. */
	NbtCompound writeForClient() {
		NbtCompound nbt = new NbtCompound();
		nbt.putString("SelectedFarm", selectedFarm);
		nbt.putInt("SwapMaterial", swapMaterial);
		desk.writeForClient(nbt);
		progress.writeForClient(nbt);
		placement.write(nbt);
		if (aim != null) {
			nbt.put("Aim", aim.toNbt());
		}
		return nbt;
	}

	void read(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		selectedFarm = nbt.getString("SelectedFarm");
		page = nbt.getInt("Page");
		// The farm page needs a farm chosen in My Farms on the client, which a reload doesn't have.
		if (page == REMOVED_INVITES_PAGE || page == REMOVED_ALERTS_PAGE) {
			page = DreamTableScreenHandler.PAGE_LOADED;
		}
		swapMaterial = nbt.contains("SwapMaterial", NbtElement.INT_TYPE) ? nbt.getInt("SwapMaterial") : -1;
		progress.read(nbt);
		placement.read(nbt);
		desk.read(nbt, registries);
		movedBuildDimension = nbt.contains("MovedBuildDimension", NbtElement.STRING_TYPE) ? Identifier.tryParse(nbt.getString("MovedBuildDimension")) : null;
		aim = nbt.contains("Aim", NbtElement.COMPOUND_TYPE) ? AimedSpot.fromNbt(nbt.getCompound("Aim")) : null;
	}

	/** The player who started the build, for sessions saved before players had their own. */
	UUID savedOwner() {
		return progress.owner();
	}

	/** True once after every change, so the table sends the owner one update per tick at most. */
	boolean takeClientDirty() {
		boolean dirty = clientDirty;
		clientDirty = false;
		return dirty;
	}

	void markClientDirty() {
		clientDirty = true;
	}

	void tell(Text text, TableNoticePayload.Severity severity) {
		table.tell(player, text, severity);
	}

	void sync() {
		clientDirty = true;
		table.markDirty();
	}
}
