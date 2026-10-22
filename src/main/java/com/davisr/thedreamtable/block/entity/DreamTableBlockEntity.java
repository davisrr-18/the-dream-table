package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.farm.FarmBuilder;
import com.davisr.thedreamtable.farm.FarmDismantler;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.registry.ModBlockEntities;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import com.davisr.thedreamtable.screen.OpenData;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

/**
 * The table: which farm is chosen, what the player delivered, where the farm goes, and how far the build is.
 * Every change is synced to clients, which read it for the menu, the hologram and the clipboard.
 */
public class DreamTableBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<OpenData> {
	public static final String PHASE_IDLE = "idle";
	public static final String PHASE_BUILDING = "building";
	public static final String PHASE_BLOCKED = "blocked";
	public static final String PHASE_NEED = "need";
	public static final String PHASE_PAUSED = "paused";
	public static final String PHASE_DONE = "done";
	public static final int TICKS_PER_BLOCK = 20;

	private final SimpleInventory materials = new SimpleInventory(FarmTemplate.MAX_SLOTS);
	private final MaterialLedger ledger = new MaterialLedger();
	private final BuildProgress progress = new BuildProgress();
	private final FarmPlacement placement = new FarmPlacement();
	private boolean adjusting;
	private String selectedFarm = "";
	private int page;
	private boolean requirementsBoard;
	private int swapMaterial = -1;

	public DreamTableBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.DREAM_TABLE, pos, state);
		materials.addListener(inventory -> {
			if (!adjusting) {
				absorbInsertedStacks();
				markDirty();
			}
		});
	}

	public static void tick(World world, BlockPos pos, BlockState state, DreamTableBlockEntity table) {
		if (!(world instanceof ServerWorld serverWorld) || !table.progress.is(PHASE_BUILDING) || !table.progress.second()) {
			return;
		}
		for (int placed = 0; placed < table.progress.blocksPerSecond() && table.progress.is(PHASE_BUILDING); placed++) {
			if (!FarmBuilder.placeNext(table, serverWorld)) {
				break;
			}
		}
	}

	public static int validSpeed(int speed) {
		return speed == 2 || speed == 4 || speed == 8 ? speed : 1;
	}


	public String getSelectedFarm() {
		return selectedFarm;
	}

	public FarmTemplate template() {
		return FarmTemplates.get(selectedFarm);
	}

	public void setSelectedFarm(String selectedFarm) {
		this.selectedFarm = selectedFarm == null ? "" : selectedFarm;
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

	public UUID getOwner() {
		return progress.owner();
	}

	public boolean creativeOwner(ServerWorld world) {
		ServerPlayerEntity player = progress.owner() == null ? null : world.getServer().getPlayerManager().getPlayer(progress.owner());
		return player != null && player.isCreative();
	}

	public int getBlocksPerSecond() {
		return progress.blocksPerSecond();
	}

	public void setBlocksPerSecond(int speed) {
		progress.setBlocksPerSecond(speed);
		sync();
	}

	public void begin(ServerPlayerEntity player, boolean fromScratch) {
		progress.start(player.getUuid(), fromScratch);
		sync();
		tell(Text.translatable("gui.the_dream_table.building", progress.index(), totalSteps()));
	}

	public void obstruct(BlockPos where, BlockState obstacle) {
		progress.stop(PHASE_BLOCKED);
		sync();
		tell(Text.translatable("gui.the_dream_table.blocked_at", obstacle.getBlock().getName()));
		sound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), where, 0.8F, 0.5F);
	}

	public void missingMaterial() {
		progress.stop(PHASE_NEED);
		sync();
		tell(Text.translatable("gui.the_dream_table.paused"));
		sound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), pos, 0.8F, 0.5F);
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
		sound(SoundEvents.BLOCK_BEACON_ACTIVATE, pos, 0.6F, 1.2F);
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


	public boolean offerFromHand(PlayerEntity player) {
		FarmTemplate template = template();
		if (!requirementsBoard || template == null) {
			return false;
		}
		Hand hand = player.getStackInHand(Hand.MAIN_HAND).isEmpty() ? Hand.OFF_HAND : Hand.MAIN_HAND;
		ItemStack stack = player.getStackInHand(hand);
		int accepted = stack.isEmpty() ? 0 : ledger.accept(template, stack);
		if (accepted <= 0) {
			return false;
		}
		if (!player.getAbilities().creativeMode) {
			stack.decrement(accepted);
		}
		sync();
		if (world != null) {
			world.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.5F, 1.15F);
		}
		player.swingHand(hand);
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
		markDirty();
		return true;
	}

	/** An unfinished build is taken apart first; a finished farm stays standing. */
	public void discard(PlayerEntity player) {
		if (world instanceof ServerWorld serverWorld && !progress.is(PHASE_DONE) && progress.index() > 0) {
			int removed = FarmDismantler.dismantle(serverWorld, this, player);
			if (removed > 0) {
				player.sendMessage(Text.translatable("gui.the_dream_table.dismantled", removed), true);
			}
		}
		returnDeposited(player.getWorld(), player);
		progress.reset(true);
		page = 0;
		selectedFarm = "";
		requirementsBoard = false;
		ledger.reset();
		swapMaterial = -1;
		placement.clear();
		sync();
	}

	public void dropMaterials(World world) {
		returnDeposited(world, null);
	}


	@Override
	public OpenData getScreenOpeningData(ServerPlayerEntity player) {
		return new OpenData(pos, selectedFarm, page, DreamTableScreenHandler.phaseOf(progress.phase()), progress.index());
	}

	@Override
	public Text getDisplayName() {
		return Text.translatable("block.the_dream_table.dream_table");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new DreamTableScreenHandler(syncId, playerInventory, this);
	}

	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
		return createNbt(registries);
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.putString("SelectedFarm", selectedFarm);
		nbt.putInt("Page", page);
		nbt.putBoolean("RequirementsBoard", requirementsBoard);
		nbt.putInt("SwapMaterial", swapMaterial);
		nbt.put("Materials", materials.toNbtList(registries));
		progress.write(nbt);
		placement.write(nbt);
		ledger.write(nbt);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
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
	private void returnDeposited(World world, PlayerEntity player) {
		adjusting = true;
		try {
			for (ItemStack stack : ledger.drain(template())) {
				if (player != null && player.getInventory().insertStack(stack) && stack.isEmpty()) {
					continue;
				}
				if (player != null) {
					player.dropItem(stack, false);
				} else if (world != null) {
					ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
				}
			}
			materials.clear();
		} finally {
			adjusting = false;
		}
	}

	private void tell(Text text) {
		if (!(world instanceof ServerWorld serverWorld) || progress.owner() == null) {
			return;
		}
		ServerPlayerEntity player = serverWorld.getServer().getPlayerManager().getPlayer(progress.owner());
		if (player != null) {
			player.sendMessage(text, true);
		}
	}

	private void sound(SoundEvent sound, BlockPos where, float volume, float pitch) {
		if (world != null) {
			world.playSound(null, where, sound, SoundCategory.BLOCKS, volume, pitch);
		}
	}

	private void sync() {
		markDirty();
		if (world instanceof ServerWorld serverWorld) {
			serverWorld.getChunkManager().markForUpdate(pos);
		}
	}
}
