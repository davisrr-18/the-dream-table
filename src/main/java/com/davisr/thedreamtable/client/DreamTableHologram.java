package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.block.DreamTableBlock;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.block.entity.TableSession;
import com.davisr.thedreamtable.client.hologram.GhostRenderer;
import com.davisr.thedreamtable.farm.FarmGeometry;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.network.CheckPlacementPayload;
import com.davisr.thedreamtable.network.PlacementResultPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Where the farm preview sits. Before the build the player aims, anchors with a right-click and rotates with R;
 * once building, the table's saved anchor and facing are used instead.
 */
public final class DreamTableHologram {
	private static BlockPos tablePos;
	private static String farmId = "";
	private static int placed;
	private static boolean blocked;
	private static BlockPos aimed;
	private static boolean anchored;
	private static Direction previewFacing;
	private static int lastPlaceAge = -1;
	private static final int RECHECK_TICKS = 40;
	/** While building, the hologram shows the layer being built and this many above it, unless all layers are on. */
	private static final int LAYERS_AHEAD = 1;
	private static boolean allLayers;
	private static Placement checked;
	private static String problem = "";
	private static int existing;
	private static int recheckTicks;

	private DreamTableHologram() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(DreamTableHologram::render);
	}

	public static void registerChecks() {
		ClientPlayNetworking.registerGlobalReceiver(PlacementResultPayload.ID, (payload, context) -> {
			if (payload.table().equals(tablePos)) {
				checked = new Placement(payload.anchor(), payload.facing());
				problem = payload.problem();
				if (payload.existing() != PlacementResultPayload.UNCHANGED) {
					existing = payload.existing();
				}
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (++recheckTicks >= RECHECK_TICKS) {
				recheckTicks = 0;
				if (hasPlacement() && !locked()) {
					send(false);
				}
			}
		});
	}

	/** The server's reason this placement can't be built, as a lang key, or empty. Only valid for the placement it was asked about. */
	public static String placementProblem() {
		return checkedNow() ? problem : "";
	}

	/** How much of this farm already stands at the placement, in percent, so an older farm can be built over and protected. */
	public static int existingPercent() {
		return checkedNow() ? existing : 0;
	}

	private static boolean checkedNow() {
		return hasPlacement() && new Placement(aimed, previewFacing()).equals(checked);
	}

	/** A new placement forgets the old answer; the regular recheck keeps showing it until the new one arrives. */
	private static void check() {
		checked = null;
		problem = "";
		existing = 0;
		recheckTicks = 0;
		send(true);
	}

	private static void send(boolean announce) {
		if (hasPlacement() && ClientPlayNetworking.canSend(CheckPlacementPayload.ID)) {
			ClientPlayNetworking.send(new CheckPlacementPayload(tablePos, aimed, previewFacing(), announce));
		}
	}

	public static void registerClicks() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!world.isClient() || hand != Hand.MAIN_HAND || !tryPlace(player, hit)) {
				return ActionResult.PASS;
			}
			return ActionResult.FAIL;
		});
	}

	public static void show(BlockPos pos, String farm, int placedBlocks, boolean nextBlocked) {
		String nextFarm = farm == null ? "" : farm;
		if (!nextFarm.equals(farmId)) {
			aimed = null;
			anchored = false;
			previewFacing = null;
			checked = null;
			problem = "";
			existing = 0;
		}
		tablePos = pos;
		farmId = nextFarm;
		placed = placedBlocks;
		blocked = nextBlocked;
	}

	public static void rotate() {
		if (tablePos == null || farmId.isEmpty() || locked()) {
			return;
		}
		previewFacing = previewFacing().rotateYClockwise();
		check();
		PlayerEntity player = MinecraftClient.getInstance().player;
		if (player != null) {
			player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5F, 1.15F);
		}
	}

	/** H switches a running build's hologram between the next layers only and the whole farm. */
	public static void toggleLayers() {
		allLayers = !allLayers;
		PlayerEntity player = MinecraftClient.getInstance().player;
		if (player != null) {
			player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5F, allLayers ? 1.2F : 0.9F);
			player.sendMessage(Text.translatable(allLayers ? "gui.the_dream_table.layers_all" : "gui.the_dream_table.layers_current"), true);
		}
	}

	public static Direction previewFacing() {
		if (previewFacing == null) {
			PlayerEntity player = MinecraftClient.getInstance().player;
			previewFacing = player == null ? Direction.SOUTH : player.getHorizontalFacing();
		}
		return previewFacing;
	}

	public static boolean hasPlacement() {
		return anchored && aimed != null;
	}

	public static BlockPos anchorForBuild() {
		return hasPlacement() ? aimed : BlockPos.ORIGIN;
	}

	public static BlockPos trackedTable() {
		return tablePos;
	}

	public static void clear() {
		tablePos = null;
		farmId = "";
		placed = 0;
		blocked = false;
		aimed = null;
		anchored = false;
		previewFacing = null;
		checked = null;
		problem = "";
		existing = 0;
	}

	private static void render(WorldRenderContext context) {
		if (tablePos == null || context.world() == null || context.matrixStack() == null || !tableStillThere(context.world())) {
			return;
		}
		TableSession table = localSession(context.world().getBlockEntity(tablePos));
		boolean synced = table != null && !table.getSelectedFarm().isEmpty();
		FarmTemplate template = FarmTemplates.get(synced ? table.getSelectedFarm() : farmId);
		if (template == null) {
			return;
		}
		int shownPlaced = synced ? table.getBuildIndex() : placed;
		boolean shownBlocked = synced ? DreamTableBlockEntity.PHASE_BLOCKED.equals(table.getPhase()) : blocked;
		boolean idle = table == null || DreamTableBlockEntity.PHASE_IDLE.equals(table.getPhase());
		Placement placement = placement(table);
		BlockPos cursor = idle ? aim(context) : null;
		if (placement == null && cursor == null) {
			return;
		}
		Vec3d camera = context.camera().getPos();
		MatrixStack matrices = context.matrixStack();
		float wave = (float) (0.5 + 0.5 * Math.sin(context.world().getTime() * 0.3));
		matrices.push();
		matrices.translate(-camera.x, -camera.y, -camera.z);
		if (cursor != null && (placement == null || !cursor.equals(placement.anchor()))) {
			GhostRenderer.drawCursor(matrices, cursor, wave);
		}
		if (placement != null) {
			boolean refused = idle && !placementProblem().isEmpty();
			int topLayer = topLayer(template, shownPlaced, idle);
			GhostRenderer.drawGhosts(context, matrices, template, table, placement.anchor(), placement.facing(), shownPlaced, shownBlocked, refused, topLayer, wave);
		}
		matrices.pop();
	}

	/**
	 * The highest template layer to draw. Placing shows the whole farm to judge the spot; a running build shows the layer
	 * of its next block and the one above, which keeps tall farms readable and cheap to draw.
	 */
	private static int topLayer(FarmTemplate template, int placed, boolean idle) {
		if (idle || allLayers || placed >= template.steps().size()) {
			return Integer.MAX_VALUE;
		}
		return template.steps().get(placed).y() + LAYERS_AHEAD;
	}

	private static boolean locked() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || tablePos == null) {
			return true;
		}
		TableSession table = localSession(client.world.getBlockEntity(tablePos));
		if (table == null) {
			return false;
		}
		return table.hasAnchor() && !DreamTableBlockEntity.PHASE_IDLE.equals(table.getPhase());
	}

	private static boolean tableStillThere(World world) {
		if (tablePos != null && world.getBlockState(tablePos).getBlock() instanceof DreamTableBlock) {
			return true;
		}
		if (tablePos != null) {
			clear();
			DreamTableClipboard.close();
		}
		return false;
	}

	private static boolean tryPlace(PlayerEntity player, BlockHitResult hit) {
		if (!tableStillThere(player.getWorld()) || farmId.isEmpty() || locked()) {
			return false;
		}
		if (player.getWorld().getBlockState(hit.getBlockPos()).getBlock() instanceof DreamTableBlock) {
			return false;
		}
		BlockPos anchor = anchorFrom(hit, player.getWorld());
		if (anchor == null) {
			return false;
		}
		if (player.age == lastPlaceAge) {
			return true;
		}
		lastPlaceAge = player.age;
		aimed = anchor;
		anchored = true;
		check();
		player.swingHand(Hand.MAIN_HAND);
		player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_PLACE, 0.7F, 1.4F);
		return true;
	}

	private static Placement placement(TableSession table) {
		if (table != null && table.hasAnchor() && !DreamTableBlockEntity.PHASE_IDLE.equals(table.getPhase())) {
			return new Placement(table.getFarmAnchor(), table.getFarmFacing());
		}
		return hasPlacement() ? new Placement(aimed, previewFacing()) : null;
	}

	private static BlockPos aim(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || context.world() == null || tablePos == null) {
			return null;
		}
		HitResult hit = client.player.raycast(FarmGeometry.AIM_RANGE, 1.0F, false);
		if (!(hit instanceof BlockHitResult block) || block.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		return anchorFrom(block, context.world());
	}

	private static BlockPos anchorFrom(BlockHitResult hit, World world) {
		if (tablePos == null) {
			return null;
		}
		BlockPos pos = hit.getBlockPos();
		if (world.getBlockState(pos).getBlock() instanceof DreamTableBlock) {
			return null;
		}
		if (!world.getBlockState(pos).isReplaceable()) {
			pos = pos.offset(hit.getSide());
		}
		if (pos.equals(tablePos) || pos.getSquaredDistance(tablePos) > (double) FarmGeometry.AIM_RANGE * FarmGeometry.AIM_RANGE) {
			return null;
		}
		return pos;
	}

	/** The local player's own session; the hologram never shows another player's farm. */
	private static TableSession localSession(BlockEntity blockEntity) {
		PlayerEntity player = MinecraftClient.getInstance().player;
		return player != null && blockEntity instanceof DreamTableBlockEntity table ? table.session(player) : null;
	}

	private record Placement(BlockPos anchor, Direction facing) {
	}
}
