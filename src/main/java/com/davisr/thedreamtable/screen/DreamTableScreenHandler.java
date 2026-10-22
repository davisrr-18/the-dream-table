package com.davisr.thedreamtable.screen;

import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.farm.FarmOptions;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.farm.MaterialSpec;
import com.davisr.thedreamtable.registry.ModBlocks;
import com.davisr.thedreamtable.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

public class DreamTableScreenHandler extends ScreenHandler {
	public static final int PAGE_SELECT = 0;
	public static final int PAGE_WORK = 1;
	public static final int PAGE_REQUIREMENTS = 2;
	public static final int PAGE_SCHEMES = 3;
	public static final int BUTTON_REQUIREMENTS = 100;
	public static final int BUTTON_BUILD = 101;
	public static final int BUTTON_DISCARD = 102;
	public static final int BUTTON_BACK = 103;
	public static final int BUTTON_SCHEMES = 110;
	public static final int BUTTON_SCHEMES_BACK = 111;
	public static final int BUTTON_SCHEME = 200;

	private final ScreenHandlerContext context;
	private final Inventory materials;
	private final boolean client;
	private String selectedFarm;
	private int page;
	private int buildIndex;
	private int phase;
	private int total;

	public DreamTableScreenHandler(int syncId, PlayerInventory inventory, DreamTableBlockEntity blockEntity) {
		this(syncId, inventory, blockEntity.getPos(), blockEntity.getMaterials(), blockEntity.getSelectedFarm(), blockEntity.getPage(), phaseOf(blockEntity.getPhase()), blockEntity.getBuildIndex(), blockEntity.totalSteps());
	}

	public DreamTableScreenHandler(int syncId, PlayerInventory inventory, OpenData data) {
		this(syncId, inventory, data.pos(), new SimpleInventory(FarmTemplate.MAX_SLOTS), data.farm(), data.page(), data.phase(), data.index(), totalFor(data.farm()));
	}

	private DreamTableScreenHandler(int syncId, PlayerInventory inventory, net.minecraft.util.math.BlockPos pos, Inventory materials, String selectedFarm, int page, int phase, int buildIndex, int total) {
		super(ModScreenHandlers.DREAM_TABLE, syncId);
		this.context = ScreenHandlerContext.create(inventory.player.getWorld(), pos);
		this.materials = materials;
		this.client = inventory.player.getWorld().isClient();
		this.selectedFarm = selectedFarm == null ? "" : selectedFarm;
		this.page = page;
		this.phase = phase;
		this.buildIndex = buildIndex;
		this.total = total;
		for (int index = 0; index < FarmTemplate.MAX_SLOTS; index++) {
			addSlot(new MaterialSlot(materials, index, DreamTableLayout.materialX(index), DreamTableLayout.materialY(index), this));
		}
		int originX = DreamTableLayout.inventoryX();
		int originY = DreamTableLayout.inventoryY();
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				addSlot(new PlayerMaterialSlot(inventory, column + row * 9 + 9, originX + column * 18, originY + row * 18, this));
			}
		}
		for (int column = 0; column < 9; column++) {
			addSlot(new PlayerMaterialSlot(inventory, column, originX + column * 18, originY + 3 * 18 + 4, this));
		}
		addProperties(new net.minecraft.screen.PropertyDelegate() {
			@Override
			public int get(int index) {
				if (!client) {
					DreamTableBlockEntity table = table();
					if (table != null) {
						return switch (index) {
							case 0 -> table.getPage();
							case 1 -> table.getBuildIndex();
							case 2 -> phaseOf(table.getPhase());
							case 3 -> table.totalSteps();
							default -> 0;
						};
					}
				}
				return switch (index) {
					case 0 -> page;
					case 1 -> buildIndex;
					case 2 -> phase;
					case 3 -> total;
					default -> 0;
				};
			}

			@Override
			public void set(int index, int value) {
				switch (index) {
					case 0 -> DreamTableScreenHandler.this.page = value;
					case 1 -> DreamTableScreenHandler.this.buildIndex = value;
					case 2 -> DreamTableScreenHandler.this.phase = value;
					case 3 -> DreamTableScreenHandler.this.total = value;
					default -> {
					}
				}
			}

			@Override
			public int size() {
				return 4;
			}
		});
	}

	public String getSelectedFarm() {
		return selectedFarm;
	}

	public int getPage() {
		return page;
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

	public MaterialSpec material(int index) {
		FarmTemplate template = FarmTemplates.get(selectedFarm);
		if (template == null || index < 0 || index >= template.materials().size()) {
			return null;
		}
		return template.materials().get(index);
	}

	public net.minecraft.util.math.BlockPos getTablePos() {
		return context.get((world, pos) -> pos, net.minecraft.util.math.BlockPos.ORIGIN);
	}

	public void preview(String farm, int nextPage) {
		this.selectedFarm = farm;
		this.page = nextPage;
		FarmTemplate template = FarmTemplates.get(farm);
		this.total = template == null ? 0 : template.steps().size();
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!canUse(player)) {
			return false;
		}
		DreamTableBlockEntity table = table();
		if (!(player instanceof ServerPlayerEntity serverPlayer) || table == null) {
			return false;
		}
		java.nio.file.Path worldRoot = serverPlayer.getServerWorld().getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT);
		FarmTemplates.reloadImports(worldRoot);
		if (id >= 0 && id < FarmOptions.ALL.size()) {
			table.setSelectedFarm(FarmOptions.ALL.get(id).id());
			table.setPage(PAGE_WORK);
			selectedFarm = table.getSelectedFarm();
			page = PAGE_WORK;
			total = table.totalSteps();
			return true;
		}
		if (id >= BUTTON_SCHEME) {
			int index = id - BUTTON_SCHEME;
			List<FarmOptions.FarmOption> imported = FarmTemplates.imported();
			if (index >= 0 && index < imported.size()) {
				table.setSelectedFarm(imported.get(index).id());
				table.setPage(PAGE_WORK);
				selectedFarm = table.getSelectedFarm();
				page = PAGE_WORK;
				total = table.totalSteps();
				return true;
			}
		}
		return switch (id) {
			case BUTTON_REQUIREMENTS -> {
				table.setRequirementsBoard(true);
				yield true;
			}
			case BUTTON_BACK -> {
				table.setPage(PAGE_WORK);
				page = PAGE_WORK;
				yield true;
			}
			case BUTTON_SCHEMES -> {
				table.setPage(PAGE_SCHEMES);
				page = PAGE_SCHEMES;
				yield true;
			}
			case BUTTON_SCHEMES_BACK -> {
				table.setPage(PAGE_SELECT);
				page = PAGE_SELECT;
				yield true;
			}
			case BUTTON_BUILD -> {
				if (DreamTableBlockEntity.PHASE_BLOCKED.equals(table.getPhase())) {
					table.begin(serverPlayer, false);
				} else if (DreamTableBlockEntity.PHASE_IDLE.equals(table.getPhase()) && table.hasAllMaterials()) {
					table.begin(serverPlayer, true);
				} else {
					yield false;
				}
				phase = phaseOf(table.getPhase());
				yield true;
			}
			case BUTTON_DISCARD -> {
				table.discard(serverPlayer);
				selectedFarm = "";
				page = PAGE_SELECT;
				phase = 0;
				buildIndex = 0;
				total = 0;
				yield true;
			}
			default -> false;
		};
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		if (page != PAGE_REQUIREMENTS) {
			return ItemStack.EMPTY;
		}
		Slot slot = this.slots.get(index);
		if (!slot.hasStack()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getStack();
		ItemStack original = stack.copy();
		if (index < FarmTemplate.MAX_SLOTS) {
			if (!insertItem(stack, FarmTemplate.MAX_SLOTS, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (!insertItem(stack, 0, FarmTemplate.MAX_SLOTS, false)) {
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
		return canUse(context, player, ModBlocks.DREAM_TABLE);
	}

	private DreamTableBlockEntity table() {
		DreamTableBlockEntity[] found = new DreamTableBlockEntity[1];
		context.run((world, pos) -> {
			if (world.getBlockEntity(pos) instanceof DreamTableBlockEntity blockEntity) {
				found[0] = blockEntity;
			}
		});
		return found[0];
	}

	private static int totalFor(String farm) {
		FarmTemplate template = FarmTemplates.get(farm);
		return template == null ? 0 : template.steps().size();
	}

	public static int phaseOf(String phase) {
		return switch (phase) {
			case DreamTableBlockEntity.PHASE_BUILDING -> 1;
			case DreamTableBlockEntity.PHASE_BLOCKED -> 2;
			case DreamTableBlockEntity.PHASE_DONE -> 3;
			default -> 0;
		};
	}

	private static final class PlayerMaterialSlot extends Slot {
		private final DreamTableScreenHandler handler;

		private PlayerMaterialSlot(PlayerInventory inventory, int index, int x, int y, DreamTableScreenHandler handler) {
			super(inventory, index, x, y);
			this.handler = handler;
		}

		@Override
		public boolean isEnabled() {
			return handler.page == PAGE_REQUIREMENTS;
		}
	}
}
