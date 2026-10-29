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
import com.davisr.thedreamtable.platform.Platform;
import com.davisr.thedreamtable.platform.RenderFrame;
import com.davisr.thedreamtable.platform.RenderStage;
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
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Where the farm preview sits. Before the build the player aims, anchors with a right-click, rotates with R and mirrors with M;
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
	private static boolean previewMirrored;
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
		Platform.clientRendering().onWorldRender(RenderStage.AFTER_TRANSLUCENT, DreamTableHologram::render);
	}

	public static void registerChecks() {
		Platform.clientNetwork().registerReceiver(PlacementResultPayload.ID, payload -> {
			if (payload.table().equals(tablePos)) {
				checked = new Placement(payload.anchor(), payload.facing(), payload.mirrored());
				problem = payload.problem();
				if (payload.existing() != PlacementResultPayload.UNCHANGED) {
					existing = payload.existing();
				}
			}
		});
		Platform.clientEvents().onTickEnd(client -> {
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
		return hasPlacement() && new Placement(aimed, previewFacing(), previewMirrored).equals(checked);
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
		if (hasPlacement() && Platform.clientNetwork().canSend(CheckPlacementPayload.ID)) {
			Platform.clientNetwork().send(new CheckPlacementPayload(tablePos, aimed, previewFacing(), previewMirrored, announce));
		}
	}

	public static void registerClicks() {
		Platform.clientEvents().onUseBlock((player, world, hand, hit) -> {
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
			previewMirrored = false;
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

	/** M flips the preview left to right. It turns and mirrors independently, so any of the eight orientations is reachable. */
	public static void mirror() {
		if (tablePos == null || farmId.isEmpty() || locked()) {
			return;
		}
		previewMirrored = !previewMirrored;
		check();
		PlayerEntity player = MinecraftClient.getInstance().player;
		if (player != null) {
			player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5F, previewMirrored ? 1.25F : 1.05F);
			TableNotices.info(Text.translatable(previewMirrored ? "gui.the_dream_table.mirror_on" : "gui.the_dream_table.mirror_off"));
		}
	}

	/** H switches a running build's hologram between the next layers only and the whole farm. */
	public static void toggleLayers() {
		allLayers = !allLayers;
		PlayerEntity player = MinecraftClient.getInstance().player;
		if (player != null) {
			player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5F, allLayers ? 1.2F : 0.9F);
			TableNotices.info(Text.translatable(allLayers ? "gui.the_dream_table.layers_all" : "gui.the_dream_table.layers_current"));
		}
	}

	public static Direction previewFacing() {
		if (previewFacing == null) {
			PlayerEntity player = MinecraftClient.getInstance().player;
			previewFacing = player == null ? Direction.SOUTH : player.getHorizontalFacing();
		}
		return previewFacing;
	}

	public static boolean previewMirrored() {
		return previewMirrored;
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
		previewMirrored = false;
		checked = null;
		problem = "";
		existing = 0;
	}

	private static void render(RenderFrame context) {
		if (tablePos == null || context.world() == null || context.matrixStack() == null || !tableStillThere(context.world())) {
			return;
		}
		BlockEntity tableEntity = context.world().getBlockEntity(tablePos);
		TableSession table = localSession(tableEntity);
		boolean synced = table != null && !table.getSelectedFarm().isEmpty();
		FarmTemplate template = FarmTemplates.get(synced ? table.getSelectedFarm() : farmId);
		if (template == null) {
			return;
		}
		int shownPlaced = synced ? table.getBuildIndex() : placed;
		boolean shownBlocked = synced ? DreamTableBlockEntity.PHASE_BLOCKED.equals(table.getPhase()) : blocked;
		boolean idle = table == null || DreamTableBlockEntity.PHASE_IDLE.equals(table.getPhase());
		Placement placement = placement(table);
		// A table whose chunk just loaded again reads as idle until the server sends the session, so no aim marker yet.
		BlockPos cursor = idle && !awaitingSession(tableEntity) ? aim(context) : null;
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
			GhostRenderer.drawGhosts(matrices, template, table, placement.anchor(), placement.facing(), placement.mirrored(), shownPlaced, shownBlocked, refused, topLayer, wave);
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
		if (client.world == null || tablePos == null || !chunkLoaded(client.world, tablePos)) {
			return true;
		}
		BlockEntity tableEntity = client.world.getBlockEntity(tablePos);
		if (awaitingSession(tableEntity)) {
			return true;
		}
		TableSession table = localSession(tableEntity);
		if (table == null) {
			return false;
		}
		return table.hasAnchor() && !DreamTableBlockEntity.PHASE_IDLE.equals(table.getPhase());
	}

	/** Where the followed table is, as far as this client can tell. */
	enum TableView {
		/** Its chunk is not loaded on the client (the player walked away): nothing can be seen, so nothing is forgotten. */
		AWAY,
		STANDING,
		/** Its chunk is loaded and holds no table: it was broken. */
		GONE
	}

	static TableView viewOf(boolean chunkLoaded, boolean tableBlock) {
		if (!chunkLoaded) {
			return TableView.AWAY;
		}
		return tableBlock ? TableView.STANDING : TableView.GONE;
	}

	/**
	 * Whether the followed table stands. While its chunk is not loaded on the client it reads as air, so the placement is
	 * kept and nothing is drawn; only a table seen gone from a loaded chunk forgets the farm.
	 */
	private static boolean tableStillThere(World world) {
		if (tablePos == null) {
			return false;
		}
		boolean loaded = chunkLoaded(world, tablePos);
		return settle(viewOf(loaded, loaded && world.getBlockState(tablePos).getBlock() instanceof DreamTableBlock));
	}

	static boolean settle(TableView view) {
		if (view == TableView.GONE) {
			clear();
			DreamTableClipboard.close();
		}
		return view == TableView.STANDING;
	}

	/**
	 * Whether the chunk is loaded on the client. {@code ClientWorld.isChunkLoaded} is hard-wired to true, so the world's
	 * own answer says nothing here; the client chunk manager only holds the chunks the server has sent.
	 */
	private static boolean chunkLoaded(World world, BlockPos pos) {
		return world.getChunkManager().isChunkLoaded(ChunkSectionPos.getSectionCoord(pos.getX()), ChunkSectionPos.getSectionCoord(pos.getZ()));
	}

	/** The table is here but the server has not yet sent this player's session, as after its chunk loaded again. */
	private static boolean awaitingSession(BlockEntity blockEntity) {
		return blockEntity instanceof DreamTableBlockEntity table && !table.hasOwnSession();
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
		anchor(anchor);
		check();
		player.swingHand(Hand.MAIN_HAND);
		player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_PLACE, 0.7F, 1.4F);
		return true;
	}

	static void anchor(BlockPos anchor) {
		aimed = anchor;
		anchored = true;
	}

	private static Placement placement(TableSession table) {
		if (table != null && table.hasAnchor() && !DreamTableBlockEntity.PHASE_IDLE.equals(table.getPhase())) {
			return new Placement(table.getFarmAnchor(), table.getFarmFacing(), table.getFarmMirrored());
		}
		return hasPlacement() ? new Placement(aimed, previewFacing(), previewMirrored) : null;
	}

	private static BlockPos aim(RenderFrame context) {
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

	private record Placement(BlockPos anchor, Direction facing, boolean mirrored) {
	}
}
