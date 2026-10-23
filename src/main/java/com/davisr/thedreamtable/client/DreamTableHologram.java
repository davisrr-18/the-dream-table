package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.block.DreamTableBlock;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.block.entity.TableSession;
import com.davisr.thedreamtable.client.hologram.GhostRenderer;
import com.davisr.thedreamtable.farm.FarmGeometry;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.FarmTemplates;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;
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

	private DreamTableHologram() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(DreamTableHologram::render);
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
		PlayerEntity player = MinecraftClient.getInstance().player;
		if (player != null) {
			player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5F, 1.15F);
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
			GhostRenderer.drawGhosts(context, matrices, template, table, placement.anchor(), placement.facing(), shownPlaced, shownBlocked, wave);
		}
		matrices.pop();
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
