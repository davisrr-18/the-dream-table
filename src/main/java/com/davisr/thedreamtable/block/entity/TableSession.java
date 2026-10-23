package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.farm.BuildPermission;
import com.davisr.thedreamtable.farm.FarmBuilder;
import com.davisr.thedreamtable.farm.FarmDismantler;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.FarmTemplates;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

import static com.davisr.thedreamtable.block.entity.DreamTableBlockEntity.*;

/**
 * One player's use of a table: the menu page, the chosen farm, what was delivered, the placement, and the build.
 * A table keeps one session per player, so two players at the same table never share or disturb each other's state.
 * Reads and writes use the same keys the table used before sessions existed, so older worlds still load.
 */
public final class TableSession {
	private final DreamTableBlockEntity table;
	private final SimpleInventory materials = new SimpleInventory(FarmTemplate.MAX_SLOTS);
	private final MaterialLedger ledger = new MaterialLedger();
	private final BuildProgress progress = new BuildProgress();
	private final FarmPlacement placement = new FarmPlacement();
	private UUID player;
	private boolean adjusting;
	private String selectedFarm = "";
	private int page;
	private boolean requirementsBoard;
	private int swapMaterial = -1;

	TableSession(DreamTableBlockEntity table, UUID player) {
		this.table = table;
		this.player = player;
		materials.addListener(inventory -> {
			if (!adjusting) {
				absorbInsertedStacks();
				table.markDirty();
			}
		});
	}

	public UUID player() {
		return player;
	}

	/** A session saved before players had their own is handed to the first player who opens the table. */
	void adopt(UUID newPlayer) {
		player = newPlayer;
	}

	/** An unused session is not saved. */
	boolean isEmpty() {
		return selectedFarm.isEmpty() && !ledger.anyDelivered();
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

	public boolean creativeOwner(ServerWorld world) {
		ServerPlayerEntity online = world.getServer().getPlayerManager().getPlayer(player);
		return online != null && online.isCreative();
	}

	void tick(ServerWorld world) {
		if (!progress.is(PHASE_BUILDING) || !progress.second()) {
			return;
		}
		for (int placed = 0; placed < progress.blocksPerSecond() && progress.is(PHASE_BUILDING); placed++) {
			if (!FarmBuilder.placeNext(this, world)) {
				break;
			}
		}
	}

	// Farm choice and menu

	public String getSelectedFarm() {
		return selectedFarm;
	}

	public FarmTemplate template() {
		return FarmTemplates.get(selectedFarm);
	}

	public void setSelectedFarm(String farm) {
		selectedFarm = farm == null ? "" : farm;
		requirementsBoard = false;
		ledger.reset();
		progress.reset(false);
		swapMaterial = -1;
		placement.clear();
		sync();
	}

	/** True while the farm has a placement, delivered items, or a build that is not finished. */
	public boolean hasProgress() {
		return !progress.is(PHASE_DONE) && (progress.busy() || placement.placed() || ledger.anyDelivered());
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
		return materials;
	}

	// Material swaps

	public Item resolve(Item original) {
		return ledger.resolve(original);
	}

	public BlockState swapState(BlockState state) {
		return ledger.swapState(state);
	}

	public List<Item> swapOptions(int index) {
		return ledger.swapOptions(template(), index);
	}

	/** A material can be swapped only before any of it is delivered and before the build starts. */
	public boolean canSwap(int index) {
		FarmTemplate template = template();
		boolean valid = template != null && index >= 0 && index < template.materials().size();
		return valid && progress.is(PHASE_IDLE) && progress.index() == 0 && ledger.untouched(index);
	}

	public boolean setSwap(int index, int option) {
		if (!canSwap(index) || !ledger.swap(template(), index, option)) {
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

	// Build progress

	public String getPhase() {
		return progress.phase();
	}

	public boolean isBusy() {
		return progress.busy();
	}

	public boolean canResume() {
		return progress.resumable();
	}

	public int getBuildIndex() {
		return progress.index();
	}

	public int totalSteps() {
		FarmTemplate template = template();
		return template == null ? 0 : template.steps().size();
	}

	public int getBlocksPerSecond() {
		return progress.blocksPerSecond();
	}

	public void setBlocksPerSecond(int speed) {
		progress.setBlocksPerSecond(speed);
		sync();
	}

	/**
	 * Starts or resumes the build. It refuses, and tells the player why, when a new build would reach outside the world
	 * or overlap another player's farm at this table, or when a resumed build belongs to a farm file that has changed.
	 */
	public boolean begin(ServerPlayerEntity starter, boolean fromScratch) {
		FarmTemplate template = template();
		if (template == null) {
			return false;
		}
		if (fromScratch && !canStartHere(starter, template)) {
			return false;
		}
		if (!fromScratch && !progress.matches(template.signature())) {
			starter.sendMessage(Text.translatable("gui.the_dream_table.farm_changed"), true);
			return false;
		}
		progress.start(player, fromScratch, template.signature());
		sync();
		tell(Text.translatable("gui.the_dream_table.building", progress.index(), totalSteps()));
		return true;
	}

	private boolean canStartHere(ServerPlayerEntity starter, FarmTemplate template) {
		if (!(table.getWorld() instanceof ServerWorld world)) {
			return true;
		}
		String problem = null;
		if (!FarmBuilder.fits(world, this, template)) {
			problem = "gui.the_dream_table.out_of_world";
		} else if (table.overlapsOthers(this, template)) {
			problem = "gui.the_dream_table.farm_overlaps";
		} else if (!BuildPermission.canBuildAll(world, starter, this, template)) {
			problem = "gui.the_dream_table.protected_area";
		}
		if (problem == null) {
			return true;
		}
		placement.clear();
		sync();
		starter.sendMessage(Text.translatable(problem), true);
		return false;
	}

	/** A block could not be placed, or the step failed unexpectedly. Nothing was spent; Try Again repeats the step. */
	public void placementFailed(BlockPos where) {
		progress.stop(PHASE_BLOCKED);
		sync();
		tell(Text.translatable("gui.the_dream_table.place_failed", where.getX(), where.getY(), where.getZ()));
		table.sound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), where, 0.8F, 0.5F);
	}

	/** The owner isn't allowed to build at this spot, by spawn protection, the border, or a claim. Try Again checks again. */
	public void protectedSpot(BlockPos where) {
		progress.stop(PHASE_BLOCKED);
		sync();
		tell(Text.translatable("gui.the_dream_table.protected_spot", where.getX(), where.getY(), where.getZ()));
		table.sound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), where, 0.8F, 0.5F);
	}

	public void obstruct(BlockPos where, BlockState obstacle) {
		progress.stop(PHASE_BLOCKED);
		sync();
		tell(Text.translatable("gui.the_dream_table.blocked_at", obstacle.getBlock().getName()));
		table.sound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), where, 0.8F, 0.5F);
	}

	public void missingMaterial() {
		progress.stop(PHASE_NEED);
		sync();
		tell(Text.translatable("gui.the_dream_table.paused"));
		table.sound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), table.getPos(), 0.8F, 0.5F);
	}

	/** Stops after the current step; nothing is undone and resuming continues from the next block. */
	public boolean pause() {
		if (!progress.is(PHASE_BUILDING)) {
			return false;
		}
		progress.stop(PHASE_PAUSED);
		sync();
		tell(Text.translatable("gui.the_dream_table.paused_build", progress.index(), totalSteps()));
		return true;
	}

	public void finish() {
		progress.stop(PHASE_DONE);
		sync();
		tell(Text.translatable("gui.the_dream_table.done"));
		table.sound(SoundEvents.BLOCK_BEACON_ACTIVATE, table.getPos(), 0.6F, 1.2F);
	}

	public void advance() {
		progress.advance();
		sync();
	}

	/** Marks the current step as placed by the table, and whether it used a delivered item. */
	public void recordPlaced(boolean usedItem) {
		progress.recordPlaced(usedItem);
	}

	public boolean wasPlaced(int step) {
		return progress.placed(step);
	}

	public boolean wasPaid(int step) {
		return progress.paid(step);
	}

	// Delivered materials

	public boolean offerFromHand(PlayerEntity giver) {
		FarmTemplate template = template();
		if (!requirementsBoard || template == null) {
			return false;
		}
		Hand hand = giver.getStackInHand(Hand.MAIN_HAND).isEmpty() ? Hand.OFF_HAND : Hand.MAIN_HAND;
		ItemStack stack = giver.getStackInHand(hand);
		int accepted = stack.isEmpty() ? 0 : ledger.accept(template, stack);
		if (accepted <= 0) {
			return false;
		}
		if (!giver.getAbilities().creativeMode) {
			stack.decrement(accepted);
		}
		sync();
		if (table.getWorld() != null) {
			table.getWorld().playSound(null, table.getPos(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.5F, 1.15F);
		}
		giver.swingHand(hand);
		return true;
	}

	public boolean hasAllMaterials() {
		FarmTemplate template = template();
		return template != null && ledger.complete(template);
	}

	public int deposited(int index) {
		return ledger.deposited(index);
	}

	public int supplied(int index) {
		return ledger.supplied(index);
	}

	public int stored(Item item) {
		FarmTemplate template = template();
		return template == null ? 0 : ledger.stored(template, item);
	}

	public boolean hasMaterial(Item item) {
		FarmTemplate template = template();
		return template != null && ledger.has(template, item);
	}

	public boolean consumeMaterial(Item item) {
		FarmTemplate template = template();
		if (template == null || !ledger.consume(template, item)) {
			return false;
		}
		table.markDirty();
		return true;
	}

	/** An unfinished build is taken apart first; a finished farm stays standing. */
	public void discard(PlayerEntity discarder) {
		FarmTemplate template = template();
		if (table.getWorld() instanceof ServerWorld serverWorld && template != null && !progress.is(PHASE_DONE) && progress.index() > 0) {
			if (!progress.matches(template.signature())) {
				discarder.sendMessage(Text.translatable("gui.the_dream_table.discard_changed"), true);
			} else {
				int removed = FarmDismantler.dismantle(serverWorld, this, discarder);
				if (removed > 0) {
					discarder.sendMessage(Text.translatable("gui.the_dream_table.dismantled", removed), true);
				}
			}
		}
		returnDeposited(discarder.getWorld(), discarder);
		progress.reset(true);
		page = 0;
		selectedFarm = "";
		requirementsBoard = false;
		ledger.reset();
		swapMaterial = -1;
		placement.clear();
		sync();
	}

	void dropMaterials(World world) {
		returnDeposited(world, null);
	}

	// Saving

	void write(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		nbt.putString("SelectedFarm", selectedFarm);
		nbt.putInt("Page", page);
		nbt.putBoolean("RequirementsBoard", requirementsBoard);
		nbt.putInt("SwapMaterial", swapMaterial);
		nbt.put("Materials", materials.toNbtList(registries));
		progress.write(nbt);
		placement.write(nbt);
		ledger.write(nbt);
	}

	/** The trimmed copy sent to clients: no slot inventory, no step records, no page, which the menu syncs on its own. */
	void writeForClient(NbtCompound nbt) {
		nbt.putString("SelectedFarm", selectedFarm);
		nbt.putBoolean("RequirementsBoard", requirementsBoard);
		nbt.putInt("SwapMaterial", swapMaterial);
		progress.writeForClient(nbt);
		placement.write(nbt);
		ledger.write(nbt);
	}

	void read(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		selectedFarm = nbt.getString("SelectedFarm");
		page = nbt.getInt("Page");
		requirementsBoard = nbt.getBoolean("RequirementsBoard");
		swapMaterial = nbt.contains("SwapMaterial", NbtElement.INT_TYPE) ? nbt.getInt("SwapMaterial") : -1;
		materials.clear();
		if (nbt.contains("Materials", NbtElement.LIST_TYPE)) {
			materials.readNbtList(nbt.getList("Materials", NbtElement.COMPOUND_TYPE), registries);
		}
		progress.read(nbt);
		placement.read(nbt);
		ledger.read(nbt);
	}

	/** The player who started the build, for sessions saved before players had their own. */
	UUID savedOwner() {
		return progress.owner();
	}

	private void absorbInsertedStacks() {
		FarmTemplate template = template();
		if (template == null) {
			return;
		}
		adjusting = true;
		try {
			ledger.absorb(template, materials);
		} finally {
			adjusting = false;
		}
	}

	/** Gives the unused delivered items back to the player, or drops them at the table. */
	private void returnDeposited(World world, PlayerEntity receiver) {
		adjusting = true;
		try {
			for (ItemStack stack : ledger.drain(template())) {
				if (receiver != null && receiver.getInventory().insertStack(stack) && stack.isEmpty()) {
					continue;
				}
				if (receiver != null) {
					receiver.dropItem(stack, false);
				} else if (world != null) {
					BlockPos pos = table.getPos();
					ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
				}
			}
			materials.clear();
		} finally {
			adjusting = false;
		}
	}

	private void tell(Text text) {
		table.tell(player, text);
	}

	private void sync() {
		table.sync();
	}
}
