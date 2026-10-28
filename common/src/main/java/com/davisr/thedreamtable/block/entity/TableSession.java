package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.farm.FarmAreas;
import com.davisr.thedreamtable.farm.FarmBuilder;
import com.davisr.thedreamtable.farm.FarmDismantler;
import com.davisr.thedreamtable.farm.FarmSite;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.FarmTemplates;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
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

	private final DreamTableBlockEntity table;
	private final MaterialDesk desk;
	private final BuildProgress progress = new BuildProgress();
	private final FarmPlacement placement = new FarmPlacement();
	private final SessionAreas areas = new SessionAreas(this, progress);
	private final SessionBuild build = new SessionBuild(this, progress, areas);
	private UUID player;
	private String selectedFarm = "";
	private int page;
	private boolean requirementsBoard;
	private int swapMaterial = -1;
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
		build.tick(world);
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
		requirementsBoard = false;
		desk.reset();
		progress.reset(false);
		swapMaterial = -1;
		placement.clear();
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

	public boolean isRequirementsBoard() {
		return requirementsBoard;
	}

	public void setRequirementsBoard(boolean requirementsBoard) {
		this.requirementsBoard = requirementsBoard;
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

	public void setPlacement(BlockPos anchor, Direction facing) {
		placement.set(anchor, facing);
		sync();
	}

	void clearPlacement() {
		placement.clear();
		sync();
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

	/** Delivers the held stack on the clipboard. When the build waits for exactly this block, it continues right away. */
	public boolean offerFromHand(PlayerEntity giver) {
		if (!requirementsBoard || template() == null) {
			return false;
		}
		Hand hand = giver.getStackInHand(Hand.MAIN_HAND).isEmpty() ? Hand.OFF_HAND : Hand.MAIN_HAND;
		ItemStack stack = giver.getStackInHand(hand);
		boolean creative = giver.getAbilities().creativeMode;
		reconcileMaterials(giver);
		int accepted = desk.accept(stack, creative);
		if (accepted <= 0) {
			return false;
		}
		if (!creative) {
			stack.decrement(accepted);
		}
		sync();
		if (table.getWorld() != null) {
			table.getWorld().playSound(null, getPos(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.5F, 1.15F);
		}
		giver.swingHand(hand);
		if (giver instanceof ServerPlayerEntity deliverer && progress.is(PHASE_NEED) && nextStepReady()) {
			build.begin(deliverer, false);
		}
		return true;
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
			if (!progress.matches(template.signature())) {
				if (discarder != null) {
					discarder.sendMessage(Text.translatable("gui.the_dream_table.discard_changed"), true);
				}
			} else {
				FarmSite site = FarmBuilder.site(this);
				FarmDismantler job = new FarmDismantler(player, selectedFarm, template.signature(), site, desk.swaps(),
						progress.placedSteps(), progress.paidSteps(), progress.index());
				FarmAreas.of(serverWorld).claim(job.areaKey(), site.footprint(template));
				table.startDismantling(job);
				if (discarder != null) {
					discarder.sendMessage(Text.translatable("gui.the_dream_table.dismantling"), true);
				}
			}
		}
		releaseArea();
		desk.returnAll(world, getPos(), discarder);
		progress.reset(true);
		page = 0;
		selectedFarm = "";
		requirementsBoard = false;
		desk.reset();
		swapMaterial = -1;
		placement.clear();
		sync();
	}

	void dropMaterials(World world) {
		desk.returnAll(world, getPos(), null);
		releaseArea();
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
		nbt.putBoolean("RequirementsBoard", requirementsBoard);
		nbt.putInt("SwapMaterial", swapMaterial);
		desk.write(nbt, registries);
		progress.write(nbt);
		placement.write(nbt);
	}

	/** The trimmed copy sent to the owner's client: no slot inventory, no step records, no page, which the menu syncs on its own. */
	NbtCompound writeForClient() {
		NbtCompound nbt = new NbtCompound();
		nbt.putString("SelectedFarm", selectedFarm);
		nbt.putBoolean("RequirementsBoard", requirementsBoard);
		nbt.putInt("SwapMaterial", swapMaterial);
		desk.writeForClient(nbt);
		progress.writeForClient(nbt);
		placement.write(nbt);
		return nbt;
	}

	void read(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		selectedFarm = nbt.getString("SelectedFarm");
		page = nbt.getInt("Page");
		requirementsBoard = nbt.getBoolean("RequirementsBoard");
		swapMaterial = nbt.contains("SwapMaterial", NbtElement.INT_TYPE) ? nbt.getInt("SwapMaterial") : -1;
		progress.read(nbt);
		placement.read(nbt);
		desk.read(nbt, registries);
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

	void tell(Text text) {
		table.tell(player, text);
	}

	void sync() {
		clientDirty = true;
		table.markDirty();
	}
}
