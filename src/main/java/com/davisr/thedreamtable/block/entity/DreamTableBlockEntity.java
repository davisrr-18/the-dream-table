package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.farm.MaterialSpec;
import com.davisr.thedreamtable.registry.ModBlockEntities;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import com.davisr.thedreamtable.screen.OpenData;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.UUID;

public class DreamTableBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<OpenData> {
	public static final String PHASE_IDLE = "idle";
	public static final String PHASE_BUILDING = "building";
	public static final String PHASE_BLOCKED = "blocked";
	public static final String PHASE_DONE = "done";
	public static final int TICKS_PER_BLOCK = 20;

	private final SimpleInventory materials = new SimpleInventory(FarmTemplate.MAX_SLOTS);
	private String selectedFarm = "";
	private String phase = PHASE_IDLE;
	private int page;
	private int buildIndex;
	private int ticks;
	private boolean requirementsBoard;
	private UUID owner;
	private BlockPos farmAnchor;
	private Direction farmFacing = Direction.SOUTH;

	public DreamTableBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.DREAM_TABLE, pos, state);
		materials.addListener(inventory -> markDirty());
	}

	public static void tick(World world, BlockPos pos, BlockState state, DreamTableBlockEntity table) {
		if (!(world instanceof ServerWorld serverWorld) || !PHASE_BUILDING.equals(table.phase)) {
			return;
		}
		table.ticks++;
		if (table.ticks < TICKS_PER_BLOCK) {
			return;
		}
		table.ticks = 0;
		com.davisr.thedreamtable.farm.FarmBuilder.placeNext(table, serverWorld);
	}

	public SimpleInventory getMaterials() {
		return materials;
	}

	public String getSelectedFarm() {
		return selectedFarm;
	}

	public void setSelectedFarm(String selectedFarm) {
		this.selectedFarm = selectedFarm == null ? "" : selectedFarm;
		this.requirementsBoard = false;
		clearPlacement();
		sync();
	}

	public boolean hasAnchor() {
		return farmAnchor != null;
	}

	public BlockPos getFarmAnchor() {
		return farmAnchor;
	}

	public Direction getFarmFacing() {
		return farmFacing;
	}

	public void setPlacement(BlockPos anchor, Direction facing) {
		this.farmAnchor = anchor;
		this.farmFacing = facing != null && facing.getAxis().isHorizontal() ? facing : Direction.SOUTH;
		sync();
	}

	public boolean isRequirementsBoard() {
		return requirementsBoard;
	}

	public void setRequirementsBoard(boolean requirementsBoard) {
		this.requirementsBoard = requirementsBoard;
		sync();
	}

	public int getPage() {
		return page;
	}

	public void setPage(int page) {
		this.page = page;
		sync();
	}

	public String getPhase() {
		return phase;
	}

	public int getBuildIndex() {
		return buildIndex;
	}

	public UUID getOwner() {
		return owner;
	}

	public int totalSteps() {
		FarmTemplate template = FarmTemplates.get(selectedFarm);
		return template == null ? 0 : template.steps().size();
	}

	public boolean hasAllMaterials() {
		FarmTemplate template = FarmTemplates.get(selectedFarm);
		if (template == null) {
			return false;
		}
		for (int index = 0; index < template.materials().size(); index++) {
			MaterialSpec spec = template.materials().get(index);
			ItemStack stack = materials.getStack(index);
			if (!stack.isOf(spec.item()) || stack.getCount() < spec.count()) {
				return false;
			}
		}
		return true;
	}

	public void begin(ServerPlayerEntity player, boolean reset) {
		owner = player.getUuid();
		if (reset) {
			buildIndex = 0;
		}
		phase = PHASE_BUILDING;
		ticks = 0;
		sync();
		tell(Text.translatable("gui.the_dream_table.building", buildIndex, totalSteps()));
	}

	public void obstruct(BlockPos where, BlockState obstacle) {
		phase = PHASE_BLOCKED;
		sync();
		tell(Text.translatable("gui.the_dream_table.blocked_at", obstacle.getBlock().getName()));
		sound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), where, 0.8F, 0.5F);
	}

	public void missingMaterial() {
		phase = PHASE_BLOCKED;
		sync();
		tell(Text.translatable("gui.the_dream_table.need_materials"));
		sound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), pos, 0.8F, 0.5F);
	}

	public void finish() {
		phase = PHASE_DONE;
		sync();
		tell(Text.translatable("gui.the_dream_table.done"));
		sound(SoundEvents.BLOCK_BEACON_ACTIVATE, pos, 0.6F, 1.2F);
	}

	public void advance() {
		buildIndex++;
		sync();
	}

	public boolean offerFromHand(PlayerEntity player) {
		if (!requirementsBoard || selectedFarm.isEmpty()) {
			return false;
		}
		FarmTemplate template = FarmTemplates.get(selectedFarm);
		if (template == null) {
			return false;
		}
		Hand hand = Hand.MAIN_HAND;
		ItemStack stack = player.getStackInHand(hand);
		if (stack.isEmpty()) {
			hand = Hand.OFF_HAND;
			stack = player.getStackInHand(hand);
		}
		if (stack.isEmpty()) {
			return false;
		}
		int needed = 0;
		for (MaterialSpec spec : template.materials()) {
			if (stack.isOf(spec.item())) {
				needed += spec.count();
			}
		}
		int have = stored(stack.getItem());
		int take = Math.min(stack.getCount(), needed - have);
		if (take <= 0) {
			return false;
		}
		int before = have;
		for (int index = 0; index < template.materials().size() && stored(stack.getItem()) - before < take; index++) {
			MaterialSpec spec = template.materials().get(index);
			if (!stack.isOf(spec.item())) {
				continue;
			}
			ItemStack slot = materials.getStack(index);
			int slotHave = slot.isOf(spec.item()) ? slot.getCount() : 0;
			int room = Math.min(spec.count(), spec.item().getMaxCount()) - slotHave;
			if (room <= 0) {
				continue;
			}
			int moved = Math.min(room, take - (stored(stack.getItem()) - before));
			if (slot.isEmpty() || !slot.isOf(spec.item())) {
				materials.setStack(index, new ItemStack(spec.item(), moved));
			} else {
				slot.increment(moved);
			}
		}
		int accepted = stored(stack.getItem()) - before;
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

	public int stored(Item item) {
		int count = 0;
		for (int index = 0; index < materials.size(); index++) {
			ItemStack stack = materials.getStack(index);
			if (stack.isOf(item)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	public boolean consumeMaterial(Item item) {
		for (int index = 0; index < materials.size(); index++) {
			ItemStack stack = materials.getStack(index);
			if (stack.isOf(item) && !stack.isEmpty()) {
				stack.decrement(1);
				if (stack.isEmpty()) {
					materials.setStack(index, ItemStack.EMPTY);
				}
				markDirty();
				return true;
			}
		}
		return false;
	}

	public void discard(PlayerEntity player) {
		phase = PHASE_IDLE;
		page = 0;
		buildIndex = 0;
		ticks = 0;
		selectedFarm = "";
		requirementsBoard = false;
		owner = null;
		clearPlacement();
		for (int index = 0; index < materials.size(); index++) {
			ItemStack stack = materials.removeStack(index);
			if (stack.isEmpty()) {
				continue;
			}
			if (!player.getInventory().insertStack(stack) || !stack.isEmpty()) {
				player.dropItem(stack, false);
			}
		}
		sync();
	}

	public void dropMaterials(World world) {
		net.minecraft.util.ItemScatterer.spawn(world, pos, materials);
		materials.clear();
	}

	@Override
	public OpenData getScreenOpeningData(ServerPlayerEntity player) {
		return new OpenData(pos, selectedFarm, page, DreamTableScreenHandler.phaseOf(phase), buildIndex);
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
		nbt.putString("Phase", phase);
		nbt.putInt("Page", page);
		nbt.putInt("BuildIndex", buildIndex);
		nbt.putInt("Ticks", ticks);
		nbt.putBoolean("RequirementsBoard", requirementsBoard);
		if (owner != null) {
			nbt.putUuid("Owner", owner);
		}
		nbt.putString("FarmFacing", farmFacing.asString());
		if (farmAnchor != null) {
			nbt.putInt("AnchorX", farmAnchor.getX());
			nbt.putInt("AnchorY", farmAnchor.getY());
			nbt.putInt("AnchorZ", farmAnchor.getZ());
		}
		nbt.put("Materials", materials.toNbtList(registries));
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		selectedFarm = nbt.getString("SelectedFarm");
		String storedPhase = nbt.getString("Phase");
		phase = storedPhase.isEmpty() ? PHASE_IDLE : storedPhase;
		page = nbt.getInt("Page");
		buildIndex = Math.max(0, nbt.getInt("BuildIndex"));
		ticks = Math.max(0, nbt.getInt("Ticks"));
		requirementsBoard = nbt.getBoolean("RequirementsBoard");
		owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
		Direction storedFacing = Direction.byName(nbt.getString("FarmFacing"));
		farmFacing = storedFacing != null && storedFacing.getAxis().isHorizontal() ? storedFacing : Direction.SOUTH;
		farmAnchor = nbt.contains("AnchorX", NbtElement.INT_TYPE) ? new BlockPos(nbt.getInt("AnchorX"), nbt.getInt("AnchorY"), nbt.getInt("AnchorZ")) : null;
		materials.clear();
		if (nbt.contains("Materials", NbtElement.LIST_TYPE)) {
			materials.readNbtList(nbt.getList("Materials", NbtElement.COMPOUND_TYPE), registries);
		}
	}

	private void clearPlacement() {
		farmAnchor = null;
		farmFacing = Direction.SOUTH;
	}

	private void tell(Text text) {
		if (!(world instanceof ServerWorld serverWorld) || owner == null) {
			return;
		}
		ServerPlayerEntity player = serverWorld.getServer().getPlayerManager().getPlayer(owner);
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
