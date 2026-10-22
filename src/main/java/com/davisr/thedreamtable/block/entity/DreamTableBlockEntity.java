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
	private final int[] deposited = new int[FarmTemplate.MAX_SLOTS];
	private final int[] consumed = new int[FarmTemplate.MAX_SLOTS];
	private boolean adjusting;
	private String selectedFarm = "";
	private String phase = PHASE_IDLE;
	private int page;
	private int buildIndex;
	private int ticks;
	private boolean requirementsBoard;
	private UUID owner;
	private BlockPos farmAnchor;
	private Direction farmFacing = Direction.SOUTH;
	private int blocksPerSecond = 1;

	public DreamTableBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.DREAM_TABLE, pos, state);
		materials.addListener(inventory -> {
			if (adjusting) {
				return;
			}
			absorbInsertedStacks();
			markDirty();
		});
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
		for (int placed = 0; placed < table.blocksPerSecond && PHASE_BUILDING.equals(table.phase); placed++) {
			if (!com.davisr.thedreamtable.farm.FarmBuilder.placeNext(table, serverWorld)) {
				break;
			}
		}
	}

	public int getBlocksPerSecond() {
		return blocksPerSecond;
	}

	public void setBlocksPerSecond(int speed) {
		blocksPerSecond = speed == 2 || speed == 4 || speed == 8 ? speed : 1;
		sync();
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
		java.util.Arrays.fill(deposited, 0);
		java.util.Arrays.fill(consumed, 0);
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
			if (deposited[index] < template.materials().get(index).count()) {
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
		int accepted = 0;
		for (int index = 0; index < template.materials().size() && accepted < take; index++) {
			MaterialSpec spec = template.materials().get(index);
			if (!stack.isOf(spec.item())) {
				continue;
			}
			int room = spec.count() - deposited[index];
			if (room <= 0) {
				continue;
			}
			int moved = Math.min(room, take - accepted);
			deposited[index] += moved;
			accepted += moved;
		}
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

	public int deposited(int index) {
		return index >= 0 && index < deposited.length ? deposited[index] : 0;
	}

	public int supplied(int index) {
		int used = index >= 0 && index < consumed.length ? consumed[index] : 0;
		return deposited(index) + used;
	}

	public int stored(Item item) {
		FarmTemplate template = FarmTemplates.get(selectedFarm);
		if (template == null) {
			return 0;
		}
		int count = 0;
		for (int index = 0; index < template.materials().size(); index++) {
			if (template.materials().get(index).item() == item) {
				count += deposited[index] + consumed[index];
			}
		}
		return count;
	}

	public boolean consumeMaterial(Item item) {
		FarmTemplate template = FarmTemplates.get(selectedFarm);
		if (template == null) {
			return false;
		}
		for (int index = 0; index < template.materials().size(); index++) {
			if (template.materials().get(index).item() == item && deposited[index] > 0) {
				deposited[index]--;
				consumed[index]++;
				markDirty();
				return true;
			}
		}
		return false;
	}

	public void discard(PlayerEntity player) {
		returnDeposited(player.getWorld(), player, true);
		phase = PHASE_IDLE;
		page = 0;
		buildIndex = 0;
		ticks = 0;
		selectedFarm = "";
		requirementsBoard = false;
		owner = null;
		clearPlacement();
		sync();
	}

	public void dropMaterials(World world) {
		returnDeposited(world, null, false);
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
		nbt.putInt("BlocksPerSecond", blocksPerSecond);
		nbt.putIntArray("Deposited", deposited);
		nbt.putIntArray("Consumed", consumed);
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
		int storedSpeed = nbt.getInt("BlocksPerSecond");
		blocksPerSecond = storedSpeed == 2 || storedSpeed == 4 || storedSpeed == 8 ? storedSpeed : 1;
		int[] stored = nbt.getIntArray("Deposited");
		int[] used = nbt.getIntArray("Consumed");
		java.util.Arrays.fill(deposited, 0);
		java.util.Arrays.fill(consumed, 0);
		System.arraycopy(stored, 0, deposited, 0, Math.min(stored.length, deposited.length));
		System.arraycopy(used, 0, consumed, 0, Math.min(used.length, consumed.length));
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

	private void absorbInsertedStacks() {
		FarmTemplate template = FarmTemplates.get(selectedFarm);
		if (template == null) {
			return;
		}
		adjusting = true;
		try {
			for (int index = 0; index < template.materials().size() && index < materials.size(); index++) {
				ItemStack stack = materials.getStack(index);
				MaterialSpec spec = template.materials().get(index);
				if (stack.isEmpty() || !stack.isOf(spec.item())) {
					continue;
				}
				deposited[index] += stack.getCount();
				materials.setStack(index, ItemStack.EMPTY);
			}
		} finally {
			adjusting = false;
		}
	}

	private void returnDeposited(World world, PlayerEntity player, boolean toPlayer) {
		FarmTemplate template = FarmTemplates.get(selectedFarm);
		adjusting = true;
		try {
			for (int index = 0; index < deposited.length; index++) {
				int left = deposited[index];
				deposited[index] = 0;
				consumed[index] = 0;
				if (left <= 0 || template == null || index >= template.materials().size()) {
					continue;
				}
				Item item = template.materials().get(index).item();
				while (left > 0) {
					int amount = Math.min(left, Math.max(1, item.getMaxCount()));
					ItemStack stack = new ItemStack(item, amount);
					left -= amount;
					if (toPlayer && player != null && player.getInventory().insertStack(stack) && stack.isEmpty()) {
						continue;
					}
					if (player != null && toPlayer && !stack.isEmpty()) {
						player.dropItem(stack, false);
					} else if (world != null && !stack.isEmpty()) {
						net.minecraft.util.ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
					}
				}
			}
			materials.clear();
		} finally {
			adjusting = false;
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
