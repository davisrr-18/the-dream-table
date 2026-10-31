package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.block.DreamTableBlock;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.block.entity.TableSession;
import com.davisr.thedreamtable.client.hologram.GhostRenderer;
import com.davisr.thedreamtable.client.hologram.PlacementOutline;
import com.davisr.thedreamtable.client.map.MapMode;
import com.davisr.thedreamtable.client.map.MapTerrain;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.List;

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
	/** The aimed spot is asked about at most this often; the server allows four checks per second and rotating needs some too. */
	private static final int HOVER_TICKS = 8;
	/** While building, the hologram shows the layer being built and this many above it, unless all layers are on. */
	private static final int LAYERS_AHEAD = 1;
	private static boolean allLayers;
	private static Placement checked;
	private static String problem = "";
	private static int existing;
	private static int recheckTicks;
	/** The spot under the crosshair, its answer, and the ticks since it was last asked. */
	private static Placement hover;
	private static Placement hoverChecked;
	private static String hoverProblem = "";
	private static int hoverTicks;
	/** Map mode: while active, the aimed spot is the map cursor's instead of the crosshair's. */
	private static boolean mapActive;
	private static BlockPos mapCursor;
	/** The range circle of the map, and the world time it was built at: it follows the terrain, so it is rebuilt now and then, not per frame. */
	private static List<Vec3d> mapLoop = List.of();
	private static long mapLoopTime = Long.MIN_VALUE;
	private static final int MAP_LOOP_TICKS = 100;
	/** Long enough to reach past the table's whole aim range from any point the map camera can take. */
	private static final double MAP_RAY_LENGTH = 128.0;

	private DreamTableHologram() {
	}

	public static void register() {
		Platform.clientRendering().onWorldRender(RenderStage.AFTER_TRANSLUCENT, DreamTableHologram::render);
		Platform.clientRendering().hideBlockOutlineWhen(DreamTableHologram::hidesBlockOutline);
	}

	public static void registerChecks() {
		Platform.clientNetwork().registerReceiver(PlacementResultPayload.ID, payload -> {
			if (payload.table().equals(tablePos)) {
				Placement answered = new Placement(payload.anchor(), payload.facing(), payload.mirrored());
				if (hasPlacement() && answered.equals(placedSpot())) {
					checked = answered;
					problem = payload.problem();
					if (payload.existing() != PlacementResultPayload.UNCHANGED) {
						existing = payload.existing();
					}
				} else {
					hoverChecked = answered;
					hoverProblem = payload.problem();
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
			askAboutHover();
		});
	}

	/** Whether the map mode may open: a table is followed, a farm is chosen and no build is running. */
	public static boolean mapAvailable() {
		return tablePos != null && !farmId.isEmpty() && !locked();
	}

	/** Map mode switches the aim from the crosshair to the map cursor; leaving it forgets the cursor. */
	public static void setMapActive(boolean active) {
		mapActive = active;
		mapCursor = null;
		mapLoop = List.of();
		mapLoopTime = Long.MIN_VALUE;
	}

	/** Places the farm at the map cursor like a right-click at the crosshair would; false when there is no valid spot under it. */
	public static boolean mapPlace() {
		PlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null || mapCursor == null || !mapAvailable() || mapCursor.equals(tablePos)) {
			return false;
		}
		anchor(mapCursor);
		check();
		player.swingHand(Hand.MAIN_HAND);
		player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_PLACE, 0.7F, 1.4F);
		return true;
	}

	/** The followed table, for map mode to centre on. Null when none. */
	public static BlockPos mapTable() {
		return tablePos;
	}

	/** While a farm spot is being aimed, the game's selection outline of the cube underneath would only add noise to the marker. */
	static boolean hidesBlockOutline() {
		return hover != null;
	}

	/** Asks the server about the spot under the crosshair when it changed (or its turn did), so the outline can be coloured before the click. */
	private static void askAboutHover() {
		hoverTicks++;
		// Once a spot is placed its own checks use the server's budget; an unanswered hover question is asked again.
		if (hover == null || hasPlacement() || hover.equals(hoverChecked) || hoverTicks < HOVER_TICKS || tablePos == null
				|| !Platform.clientNetwork().canSend(CheckPlacementPayload.ID)) {
			return;
		}
		hoverTicks = 0;
		Platform.clientNetwork().send(new CheckPlacementPayload(tablePos, hover.anchor(), hover.facing(), hover.mirrored(), false));
	}

	/** What the outline of the aimed spot shows: checking until the server answers about exactly this spot. */
	private static PlacementOutline.Status hoverStatus() {
		return PlacementOutline.status(hover != null && hover.equals(hoverChecked), hoverProblem);
	}

	private static Placement placedSpot() {
		return new Placement(aimed, previewFacing(), previewMirrored);
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
		// Another farm or another table starts over: a placement made for one table means nothing at the next.
		if (!nextFarm.equals(farmId) || (tablePos != null && !tablePos.equals(pos))) {
			hoverChecked = null;
			hoverProblem = "";
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
		if (!flipLayers()) {
			return;
		}
		PlayerEntity player = MinecraftClient.getInstance().player;
		if (player != null) {
			player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5F, allLayers ? 1.2F : 0.9F);
			TableNotices.info(Text.translatable(allLayers ? "gui.the_dream_table.layers_all" : "gui.the_dream_table.layers_current"));
		}
	}

	/** Flips the layer mode, only while a table and farm are being followed: H does nothing without a hologram. */
	static boolean flipLayers() {
		if (tablePos == null || farmId.isEmpty()) {
			return false;
		}
		allLayers = !allLayers;
		return true;
	}

	static boolean allLayers() {
		return allLayers;
	}

	public static Direction previewFacing() {
		if (previewFacing == null) {
			PlayerEntity player = MinecraftClient.getInstance().player;
			previewFacing = player == null ? Direction.SOUTH : player.getHorizontalFacing();
		}
		return previewFacing;
	}

	/** The facing the aimed spot would take: the chosen one, or the player's own until a spot is placed, without choosing it yet. */
	private static Direction aimFacing() {
		PlayerEntity player = MinecraftClient.getInstance().player;
		return previewFacing != null || player == null ? previewFacing() : player.getHorizontalFacing();
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
		allLayers = false;
		checked = null;
		problem = "";
		existing = 0;
		hover = null;
		hoverChecked = null;
		hoverProblem = "";
		mapActive = false;
		mapCursor = null;
	}

	private static void render(RenderFrame context) {
		GhostRenderer.beginFrame();
		hover = null;
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
		if (mapActive) {
			updateMap(context);
		}
		BlockPos cursor = idle && !awaitingSession(tableEntity) ? aim(context) : null;
		hover = cursor == null ? null : new Placement(cursor, aimFacing(), previewMirrored);
		if (placement == null && cursor == null) {
			return;
		}
		Vec3d camera = context.camera().getPos();
		Vector3f view = context.camera().getHorizontalPlane();
		Vec3d forward = new Vec3d(view.x(), view.y(), view.z());
		MatrixStack matrices = context.matrixStack();
		float wave = (float) (0.5 + 0.5 * Math.sin(context.world().getTime() * 0.3));
		matrices.push();
		matrices.translate(-camera.x, -camera.y, -camera.z);
		if (mapActive && !mapLoop.isEmpty()) {
			GhostRenderer.drawLoop(matrices, mapLoop, camera, forward);
		}
		if (cursor != null && (placement == null || !cursor.equals(placement.anchor()))) {
			// Once a spot is placed the aim is only a marker; its own outline would need checks the placed spot is using.
			Box aimedFootprint = placement == null && !template.steps().isEmpty()
					? PlacementOutline.footprint(template, tablePos, cursor, aimFacing(), previewMirrored) : null;
			PlacementOutline.Status aimStatus = placement == null ? hoverStatus() : PlacementOutline.Status.CHECKING;
			GhostRenderer.drawOutline(matrices, aimedFootprint, cursor, aimStatus, camera, forward, wave);
			if (aimedFootprint != null) {
				// The farm itself follows the aim, so turning and mirroring can be judged before the click.
				GhostRenderer.drawGhosts(matrices, template, table, cursor, aimFacing(), previewMirrored, 0, false, aimStatus == PlacementOutline.Status.REFUSED, Integer.MAX_VALUE, wave);
			}
		}
		if (placement != null) {
			boolean refused = idle && !placementProblem().isEmpty();
			int topLayer = topLayer(template, shownPlaced, idle);
			if (idle && !template.steps().isEmpty()) {
				PlacementOutline.Status status = checkedNow() ? PlacementOutline.status(true, placementProblem()) : PlacementOutline.Status.CHECKING;
				Box footprint = PlacementOutline.footprint(template, tablePos, placement.anchor(), placement.facing(), placement.mirrored());
				GhostRenderer.drawOutline(matrices, footprint, null, status, camera, forward, wave);
			}
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
	static boolean chunkLoaded(World world, BlockPos pos) {
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

	/** Finds the spot under the map cursor and keeps the range circle up to date. */
	private static void updateMap(RenderFrame context) {
		mapCursor = mapAim(context.world(), MinecraftClient.getInstance());
		long now = context.world().getTime();
		if (mapLoopTime == Long.MIN_VALUE || now - mapLoopTime >= MAP_LOOP_TICKS || now < mapLoopTime) {
			mapLoop = MapTerrain.rangeLoop(context.world(), tablePos);
			mapLoopTime = now;
		}
	}

	/** The anchor the pointer's ray meets first, by the same rules as the crosshair aim; null when it meets no block in reach. */
	private static BlockPos mapAim(World world, MinecraftClient client) {
		Vec3d origin = MapMode.rayOrigin(client);
		Vec3d direction = MapMode.rayDirection(client);
		if (origin == null || direction == null || client.player == null) {
			return null;
		}
		Vec3d end = origin.add(direction.multiply(MAP_RAY_LENGTH));
		BlockHitResult hit = world.raycast(new RaycastContext(origin, end, RaycastContext.ShapeType.OUTLINE,
				RaycastContext.FluidHandling.NONE, client.player));
		return hit.getType() == HitResult.Type.BLOCK ? anchorFrom(hit, world) : null;
	}

	private static BlockPos aim(RenderFrame context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || context.world() == null || tablePos == null) {
			return null;
		}
		if (mapActive) {
			return mapCursor;
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
