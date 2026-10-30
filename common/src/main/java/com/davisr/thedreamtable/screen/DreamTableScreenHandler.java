package com.davisr.thedreamtable.screen;

import com.davisr.thedreamtable.block.entity.BuildIssue;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.DreamTableConfig;
import com.davisr.thedreamtable.farm.BuildPace;
import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.farm.FarmPreferences;
import com.davisr.thedreamtable.block.entity.TableSession;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.farm.MaterialSpec;
import com.davisr.thedreamtable.farm.MaterialSwaps;
import com.davisr.thedreamtable.registry.ModBlocks;
import com.davisr.thedreamtable.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.UUID;

public class DreamTableScreenHandler extends ScreenHandler {
	public static final int PAGE_SELECT = 0;
	public static final int PAGE_WORK = 1;
	public static final int PAGE_REQUIREMENTS = 2;
	public static final int PAGE_SCHEMES = 3;
	public static final int PAGE_OPTIONS = 4;
	public static final int PAGE_MATERIALS = 5;
	public static final int PAGE_SWAP = 6;
	public static final int PAGE_SPEED = 7;
	public static final int PAGE_LOADED = 8;
	public static final int PAGE_FARM = 9;
	public static final int PAGE_FARM_INFO = 21;
	/** Plus the category index, so a reopened menu returns to the same category. */
	public static final int PAGE_CATEGORY = 10;

	public static final int BUTTON_REQUIREMENTS = 100;
	public static final int BUTTON_BUILD = 101;
	public static final int BUTTON_DISCARD = 102;
	public static final int BUTTON_BACK = 103;
	public static final int BUTTON_MENU = 104;
	public static final int BUTTON_PAUSE = 105;
	public static final int BUTTON_DELIVER_ALL = 106;
	public static final int BUTTON_DELIVER_NEARBY = 107;
	public static final int BUTTON_PROTECT_FARMS = 108;
	public static final int BUTTON_FARM_INFO = 113;
	public static final int BUTTON_FARM_INFO_BACK = 114;
	public static final int BUTTON_SCHEMES = 110;
	public static final int BUTTON_SCHEMES_BACK = 111;
	public static final int BUTTON_OPTIONS = 120;
	public static final int BUTTON_OPTIONS_BACK = 121;
	public static final int BUTTON_SPEED_OPEN = 140;
	public static final int BUTTON_SPEED_BACK = 141;
	public static final int BUTTON_LOADED_FARMS = 145;
	public static final int BUTTON_LOADED_BACK = 146;
	public static final int BUTTON_FARM_OPEN = 147;
	public static final int BUTTON_FARM_BACK = 148;
	/** Plus the category index. */
	public static final int BUTTON_CATEGORY = 160;
	public static final int BUTTON_CATEGORY_BACK = 170;
	public static final int BUTTON_MATERIALS = 130;
	public static final int BUTTON_MATERIALS_BACK = 131;
	public static final int BUTTON_SWAP_BACK = 132;
	/** Plus the index in {@link BuildPace#LEVELS}. */
	public static final int BUTTON_PACE = 150;
	public static final int BUTTON_SCHEME = 200;
	public static final int BUTTON_SWAP_OPEN = 1000;
	public static final int BUTTON_SWAP_PICK = 2000;
	/** Plus a percentage from 1 to 100, for the custom speed slider. */
	public static final int BUTTON_PACE_CUSTOM = 3000;
	public static final int BUTTON_TAB_BUILD = 3300;
	public static final int BUTTON_TAB_FARMS = 3301;
	public static final int BUTTON_TAB_SCHEMATICS = 3302;
	public static final int BUTTON_TAB_SETTINGS = 3303;
	private final ScreenHandlerContext context;
	private final boolean client;
	private final UUID playerId;
	private String selectedFarm;
	private int page;
	private int buildIndex;
	private int phase;
	private int total;
	private int pace = BuildPace.DEFAULT;
	private int blocksPerSecond;
	private int maxRate = BuildPace.MAX_RATE;
	private int loadedFarms;
	private int loadedLimit;
	private int previewSwap = -1;
	private int nearbyDelivery;
	private int farmProtection;
	private int social;

	/** Protect My Farms for this player, as synced from the server: off, on, or {@link #PROTECTION_OFF_SERVER}. */
	public static final int PROTECTION_OFF = 0;
	public static final int PROTECTION_ON = 1;
	public static final int PROTECTION_OFF_SERVER = 2;
	/** Bits of the synced Protect My Farms state. */
	private static final int PROTECTION_MASK = 3;
	/** Bit of the synced social state; zero (nothing synced yet) reads as "allowed". */
	private static final int OTHER_FARMS_OFF_SERVER_BIT = 1;

	public DreamTableScreenHandler(int syncId, PlayerInventory inventory, TableSession session) {
		this(syncId, inventory, session.getPos(), session.getMaterials(), session.getSelectedFarm(), session.getPage(), phaseOf(session.getPhase()), session.getBuildIndex());
	}

	public DreamTableScreenHandler(int syncId, PlayerInventory inventory, OpenData data) {
		this(syncId, inventory, data.pos(), new SimpleInventory(FarmTemplate.MAX_SLOTS), data.farm(), data.page(), data.phase(), data.index());
	}

	private DreamTableScreenHandler(int syncId, PlayerInventory inventory, BlockPos pos, Inventory materials, String selectedFarm, int page, int phase, int buildIndex) {
		super(ModScreenHandlers.DREAM_TABLE.get(), syncId);
		this.context = ScreenHandlerContext.create(inventory.player.getWorld(), pos);
		this.client = inventory.player.getWorld().isClient();
		this.playerId = inventory.player.getUuid();
		this.selectedFarm = selectedFarm == null ? "" : selectedFarm;
		this.page = page;
		this.phase = phase;
		this.buildIndex = buildIndex;
		this.total = totalFor(this.selectedFarm);
		addSlots(inventory, materials);
		addProperties(new Properties());
	}

	private void addSlots(PlayerInventory inventory, Inventory materials) {
		for (int index = 0; index < FarmTemplate.MAX_SLOTS; index++) {
			addSlot(new MaterialSlot(materials, index, DreamTableLayout.materialX(index), DreamTableLayout.materialY(index), this));
		}
		FarmTemplate template = FarmTemplates.get(selectedFarm);
		int originX = DreamTableLayout.inventoryX();
		int originY = DreamTableLayout.inventoryY(template == null ? 0 : template.materials().size());
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				addSlot(new PlayerSlot(inventory, column + row * 9 + 9, originX + column * 18, originY + row * 18));
			}
		}
		for (int column = 0; column < 9; column++) {
			addSlot(new PlayerSlot(inventory, column, originX + column * 18, originY + 3 * 18 + 4));
		}
	}

	/** On the server the live session decides, since a repair or another action can change its farm without a button press; the client uses its synced copy. */
	public String getSelectedFarm() {
		TableSession table = client ? null : table();
		return table == null ? selectedFarm : table.getSelectedFarm();
	}

	public int getPage() {
		TableSession table = client ? null : table();
		return table == null ? page : table.getPage();
	}

	public int getBuildIndex() {
		return buildIndex;
	}

	public int getPhase() {
		return phase;
	}

	public int getTotal() {
		return total;
	}

	/** Why the build stopped or waits, from this player's synced session, or null. */
	public BuildIssue getIssue() {
		TableSession table = table();
		return table == null ? null : table.build().issue();
	}

	public int getPace() {
		return BuildPace.valid(pace);
	}

	/** The server's actual rate, which counts its configured limit and Creative; estimated from the pace until it arrives. */
	public int getBlocksPerSecond() {
		return blocksPerSecond > 0 ? blocksPerSecond : blocksPerSecondAt(getPace());
	}

	/** How many finished farms this player keeps loaded, and the server's limit for it. */
	public int getLoadedFarms() {
		return loadedFarms;
	}

	public int getLoadedLimit() {
		return loadedLimit;
	}

	/** Whether the server lets players deliver from containers near the table; synced so the button can say why it is off. */
	public boolean nearbyDeliveryAllowed() {
		return nearbyDelivery != 0;
	}

	/** This player's "Protect My Farms" as the server holds it; {@link #PROTECTION_OFF_SERVER} when the server turned the feature off. */
	public int farmProtection() {
		return farmProtection & PROTECTION_MASK;
	}

	/** Whether the server lets players see other players' farm areas; synced so the Options card can say why it is off. */
	public boolean otherFarmsAllowed() {
		return (social & OTHER_FARMS_OFF_SERVER_BIT) == 0;
	}

	/** The synced social state for the server's "Show Other Farms" switch. */
	static int socialState(boolean otherFarmsEnabled) {
		return otherFarmsEnabled ? 0 : OTHER_FARMS_OFF_SERVER_BIT;
	}

	private int socialOnServer() {
		return socialState(DreamTableConfig.showOtherFarmsEnabled());
	}

	private int farmProtectionOnServer() {
		if (!DreamTableConfig.farmProtectionEnabled()) {
			return PROTECTION_OFF_SERVER;
		}
		int[] state = {PROTECTION_OFF};
		context.run((world, pos) -> {
			if (world.getServer() != null) {
				state[0] = FarmPreferences.of(world.getServer()).protectsFarms(playerId) ? PROTECTION_ON : PROTECTION_OFF;
			}
		});
		return state[0];
	}

	/** The rate a speed level gives under the server's limit, for the estimates on the Options cards. */
	public int blocksPerSecondAt(int level) {
		return BuildPace.blocksPerSecond(level, maxRate);
	}

	public int deposited(int index) {
		TableSession table = table();
		return table == null ? 0 : table.deposited(index);
	}

	public int supplied(int index) {
		TableSession table = table();
		return table == null ? 0 : table.supplied(index);
	}

	public MaterialSpec material(int index) {
		FarmTemplate template = FarmTemplates.get(getSelectedFarm());
		if (template == null || index < 0 || index >= template.materials().size()) {
			return null;
		}
		return template.materials().get(index);
	}

	/** The item the player delivers for this material, after any swap. */
	public Item materialItem(int index) {
		MaterialSpec spec = material(index);
		if (spec == null) {
			return null;
		}
		TableSession table = table();
		return table == null ? spec.item() : table.resolve(spec.item());
	}

	public List<Item> swapOptions(int index) {
		MaterialSpec spec = material(index);
		return spec == null ? List.of() : MaterialSwaps.options(FarmTemplates.get(getSelectedFarm()), spec.item());
	}

	public boolean canSwap(int index) {
		TableSession table = table();
		return table != null && table.canSwap(index) && !swapOptions(index).isEmpty();
	}

	public int getSwapMaterial() {
		if (client && previewSwap >= 0) {
			return previewSwap;
		}
		TableSession table = table();
		return table == null ? -1 : table.getSwapMaterial();
	}

	public void previewSwap(int index) {
		previewSwap = index;
	}

	public void previewPace(int value) {
		pace = BuildPace.valid(value);
	}

	/** Shows a page on the client right away, before the server confirms it. */
	public void preview(String farm, int nextPage) {
		this.selectedFarm = farm;
		this.page = nextPage;
		this.total = totalFor(farm);
	}

	public BlockPos getTablePos() {
		return context.get((world, pos) -> pos, BlockPos.ORIGIN);
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		TableSession table = table();
		if (!canUse(player) || !(player instanceof ServerPlayerEntity serverPlayer) || table == null) {
			return false;
		}
		FarmTemplates.refreshImports(serverPlayer.getServerWorld().getServer());
		table.reconcileMaterials(serverPlayer);
		if (!TableButtons.press(table, serverPlayer, id)) {
			return false;
		}
		copyFrom(table);
		return true;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		if (getPage() != PAGE_REQUIREMENTS) {
			return ItemStack.EMPTY;
		}
		Slot slot = this.slots.get(index);
		if (!slot.hasStack()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getStack();
		ItemStack original = stack.copy();
		boolean moved = index < FarmTemplate.MAX_SLOTS
				? insertItem(stack, FarmTemplate.MAX_SLOTS, this.slots.size(), true)
				: insertItem(stack, 0, FarmTemplate.MAX_SLOTS, false);
		if (!moved) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setStack(ItemStack.EMPTY);
		} else {
			slot.markDirty();
		}
		return original;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return canUse(context, player, ModBlocks.DREAM_TABLE.get());
	}

	public static int phaseOf(String phase) {
		return switch (phase) {
			case DreamTableBlockEntity.PHASE_BUILDING -> 1;
			case DreamTableBlockEntity.PHASE_BLOCKED -> 2;
			case DreamTableBlockEntity.PHASE_DONE -> 3;
			case DreamTableBlockEntity.PHASE_NEED -> 4;
			case DreamTableBlockEntity.PHASE_PAUSED -> 5;
			default -> 0;
		};
	}

	private void copyFrom(TableSession table) {
		selectedFarm = table.getSelectedFarm();
		page = table.getPage();
		phase = phaseOf(table.getPhase());
		buildIndex = table.getBuildIndex();
		total = table.totalSteps();
		pace = table.build().pace();
		blocksPerSecond = table.build().blocksPerSecond();
		maxRate = DreamTableConfig.maxBlocksPerSecond();
	}

	/** This player's session at the table, looked up fresh each time because the client's copy is replaced on every sync. */
	private TableSession table() {
		TableSession[] found = new TableSession[1];
		context.run((world, pos) -> {
			if (world.getBlockEntity(pos) instanceof DreamTableBlockEntity blockEntity) {
				found[0] = blockEntity.session(playerId);
			}
		});
		return found[0];
	}

	private int loadedFarmsOnServer() {
		int[] count = {0};
		context.run((world, pos) -> {
			if (world.getServer() != null) {
				count[0] = FarmLoader.of(world.getServer()).loadedCount(playerId);
			}
		});
		return count[0];
	}

	private static int totalFor(String farm) {
		FarmTemplate template = FarmTemplates.get(farm);
		return template == null ? 0 : template.steps().size();
	}

	/** Page, build index, phase, step total, speed level, actual rate, server limit, loaded farms, whether nearby delivery is on, this player's Protect My Farms state and the social state (the server's Show Other Farms switch), read from the table on the server and stored on the client. */
	private final class Properties implements PropertyDelegate {
		@Override
		public int get(int index) {
			TableSession table = client ? null : table();
			if (table != null) {
				return switch (index) {
					case 0 -> table.getPage();
					case 1 -> table.getBuildIndex();
					case 2 -> phaseOf(table.getPhase());
					case 3 -> table.totalSteps();
					case 4 -> table.build().pace();
					case 5 -> table.build().blocksPerSecond();
					case 6 -> DreamTableConfig.maxBlocksPerSecond();
					case 7 -> loadedFarmsOnServer();
					case 8 -> DreamTableConfig.maxLoadedFarms();
					case 9 -> DreamTableConfig.deliverFromNearby() ? 1 : 0;
					case 10 -> farmProtectionOnServer();
					case 11 -> socialOnServer();
					default -> 0;
				};
			}
			return switch (index) {
				case 0 -> page;
				case 1 -> buildIndex;
				case 2 -> phase;
				case 3 -> total;
				case 4 -> pace;
				case 5 -> blocksPerSecond;
				case 6 -> maxRate;
				case 7 -> loadedFarms;
				case 8 -> loadedLimit;
				case 9 -> nearbyDelivery;
				case 10 -> farmProtection;
				case 11 -> social;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
			switch (index) {
				case 0 -> page = value;
				case 1 -> buildIndex = value;
				case 2 -> phase = value;
				case 3 -> total = value;
				case 4 -> pace = value;
				case 5 -> blocksPerSecond = value;
				case 6 -> maxRate = value;
				case 7 -> loadedFarms = value;
				case 8 -> loadedLimit = value;
				case 9 -> nearbyDelivery = value;
				case 10 -> farmProtection = value;
				case 11 -> social = value;
				default -> {
				}
			}
		}

		@Override
		public int size() {
			return 12;
		}
	}

	private final class PlayerSlot extends Slot {
		private PlayerSlot(PlayerInventory inventory, int index, int x, int y) {
			super(inventory, index, x, y);
		}

		@Override
		public boolean isEnabled() {
			return getPage() == PAGE_REQUIREMENTS;
		}
	}
}
