package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.block.DreamTableBlock;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.block.entity.TableSession;
import com.davisr.thedreamtable.client.clipboard.ClipboardDraw;
import com.davisr.thedreamtable.client.clipboard.ClipboardLayout;
import com.davisr.thedreamtable.client.clipboard.ClipboardLayout.Rows;
import com.davisr.thedreamtable.client.clipboard.ClipboardLayout.Zone;
import com.davisr.thedreamtable.client.clipboard.ClipboardLines;
import com.davisr.thedreamtable.client.clipboard.ClipboardLines.Line;
import com.davisr.thedreamtable.client.clipboard.ClipboardSheet;
import com.davisr.thedreamtable.client.clipboard.ClipboardSheet.Column;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.network.DepositMaterialPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.List;

import static com.davisr.thedreamtable.client.clipboard.ClipboardLayout.*;

/** The requirements board floating beside the table: where it is, which pages show, and what clicking it does. */
public final class DreamTableClipboard {
	private static final double SIDE_OFFSET = 4.6;
	private static final double HEIGHT_OFFSET = 1.55;
	private static final double REACH = 7.0;

	private static BlockPos pinned;
	private static Box hitbox;
	private static Vec3d anchor = Vec3d.ZERO;
	private static float yaw;
	private static int totalsPage;
	private static int totalsPages = 1;
	private static int orderPage;
	private static int orderPages = 1;
	private static boolean useWasDown;
	private static int lastClickAge = -1;

	private DreamTableClipboard() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_TRANSLUCENT.register(DreamTableClipboard::render);
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!world.isClient() || hand != Hand.MAIN_HAND || !isCloserThanBlock(player, hit.getPos())) {
				return ActionResult.PASS;
			}
			return interact(player) ? ActionResult.FAIL : ActionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getStackInHand(hand);
			if (!world.isClient() || hand != Hand.MAIN_HAND || !hits(player)) {
				return TypedActionResult.pass(stack);
			}
			return interact(player) ? TypedActionResult.fail(stack) : TypedActionResult.pass(stack);
		});
		ClientTickEvents.END_CLIENT_TICK.register(DreamTableClipboard::turnPageWithEmptyHand);
	}

	public static void pin(BlockPos pos) {
		pinned = pos;
		totalsPage = 0;
		orderPage = 0;
	}

	public static BlockPos pinnedTable() {
		return pinned;
	}

	public static void close() {
		pinned = null;
		hitbox = null;
		totalsPage = 0;
		orderPage = 0;
	}

	/** Right-clicking with an empty hand fires no use callback, so the page buttons are polled here. */
	private static void turnPageWithEmptyHand(MinecraftClient client) {
		if (client.player == null || client.currentScreen != null) {
			useWasDown = false;
			return;
		}
		boolean down = client.options.useKey.isPressed();
		boolean pressed = down && !useWasDown;
		useWasDown = down;
		if (!pressed || !client.player.getMainHandStack().isEmpty() || !hits(client.player)) {
			return;
		}
		if (client.crosshairTarget instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK && !isCloserThanBlock(client.player, block.getPos())) {
			return;
		}
		interact(client.player);
	}

	private static void render(WorldRenderContext context) {
		hitbox = null;
		if (context.world() == null || context.matrixStack() == null || context.camera() == null) {
			return;
		}
		TableSession table = shownTable(context);
		FarmTemplate template = table == null ? null : FarmTemplates.get(table.getSelectedFarm());
		if (template == null) {
			return;
		}
		Direction facing = context.world().getBlockState(table.getPos()).get(Properties.HORIZONTAL_FACING);
		Direction side = facing.rotateYClockwise();
		anchor = Vec3d.ofCenter(table.getPos()).add(side.getOffsetX() * SIDE_OFFSET, HEIGHT_OFFSET, side.getOffsetZ() * SIDE_OFFSET);
		hitbox = new Box(anchor, anchor).expand(HALF_W + 0.15, HALF_H + 0.15, 0.45);
		yaw = -facing.asRotation();

		ClipboardSheet.State state = sheetState(table, template);
		Vec3d camera = context.camera().getPos();
		MatrixStack matrices = context.matrixStack();
		matrices.push();
		matrices.translate(anchor.x - camera.x, anchor.y - camera.y, anchor.z - camera.z);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
		ClipboardDraw.board(matrices);
		ClipboardSheet.draw(matrices, MinecraftClient.getInstance().textRenderer, Rows.of(MinecraftClient.getInstance().textRenderer), state);
		matrices.pop();
	}

	/** The local player's own session at the pinned table: every player sees only their own board. */
	private static TableSession shownTable(WorldRenderContext context) {
		BlockPos pos = pinned != null ? pinned : DreamTableHologram.trackedTable();
		if (pos == null || !(context.world().getBlockState(pos).getBlock() instanceof DreamTableBlock)) {
			return null;
		}
		PlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null || !(context.world().getBlockEntity(pos) instanceof DreamTableBlockEntity blockEntity)) {
			return null;
		}
		TableSession table = blockEntity.session(player);
		if (table.getSelectedFarm().isEmpty()) {
			return null;
		}
		if (table.isRequirementsBoard()) {
			pinned = pos;
			return table;
		}
		return pos.equals(pinned) ? table : null;
	}

	private static ClipboardSheet.State sheetState(TableSession table, FarmTemplate template) {
		List<Line> totals = ClipboardLines.totals(table, template);
		List<Line> order = ClipboardLines.order(table, template);
		totalsPages = ClipboardLayout.pages(totals.size());
		orderPages = ClipboardLayout.pages(order.size());
		totalsPage = Math.min(totalsPage, totalsPages - 1);
		orderPage = Math.min(orderPage, orderPages - 1);
		int itemHave = 0;
		int itemNeed = 0;
		for (Line line : totals) {
			itemHave += Math.min(line.have(), line.need());
			itemNeed += line.need();
		}
		PlayerEntity player = MinecraftClient.getInstance().player;
		Vec3d look = player == null ? null : planeHit(player);
		ItemStack held = player == null ? ItemStack.EMPTY : player.getMainHandStack();
		return new ClipboardSheet.State(
				new Column(ClipboardLines.page(totals, totalsPage), TOTALS_LEFT, TOTALS_RIGHT, totalsPage, totalsPages),
				new Column(ClipboardLines.page(order, orderPage), ORDER_LEFT, ORDER_RIGHT, orderPage, orderPages),
				itemHave, itemNeed, table.getBuildIndex(), table.totalSteps(), held,
				look == null ? 0.0 : localX(look), look == null ? 0.0 : localY(look), look != null);
	}

	private static boolean interact(PlayerEntity player) {
		Vec3d point = planeHit(player);
		if (point == null) {
			return false;
		}
		Zone zone = zoneAt(Rows.of(MinecraftClient.getInstance().textRenderer), localX(point), localY(point), totalsPages, orderPages);
		if (zone == Zone.NONE) {
			return deposit(player);
		}
		if (player.age != lastClickAge) {
			lastClickAge = player.age;
			turnPage(zone);
		}
		return true;
	}

	private static void turnPage(Zone zone) {
		int beforeTotals = totalsPage;
		int beforeOrder = orderPage;
		switch (zone) {
			case TOTALS_PREV -> totalsPage = Math.max(0, totalsPage - 1);
			case TOTALS_NEXT -> totalsPage = Math.min(totalsPages - 1, totalsPage + 1);
			case ORDER_PREV -> orderPage = Math.max(0, orderPage - 1);
			case ORDER_NEXT -> orderPage = Math.min(orderPages - 1, orderPage + 1);
			default -> {
			}
		}
		if (beforeTotals != totalsPage || beforeOrder != orderPage) {
			MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0F));
		}
	}

	private static boolean deposit(PlayerEntity player) {
		BlockPos pos = pinned != null ? pinned : DreamTableHologram.trackedTable();
		if (hitbox == null || pos == null) {
			return false;
		}
		if (player.age == lastClickAge) {
			return true;
		}
		lastClickAge = player.age;
		ClientPlayNetworking.send(new DepositMaterialPayload(pos));
		return true;
	}

	private static boolean hits(PlayerEntity player) {
		return planeHit(player) != null;
	}

	/** Where the player's look ray crosses the board, or null when it misses or the board is too far. */
	private static Vec3d planeHit(PlayerEntity player) {
		if (hitbox == null) {
			return null;
		}
		Vec3d start = player.getCameraPosVec(1.0F);
		Vec3d direction = player.getRotationVec(1.0F);
		double radians = Math.toRadians(yaw);
		double sine = Math.sin(radians);
		double cosine = Math.cos(radians);
		double denominator = direction.x * sine + direction.z * cosine;
		if (Math.abs(denominator) < 1.0E-4) {
			return null;
		}
		double distance = -((start.x - anchor.x) * sine + (start.z - anchor.z) * cosine) / denominator;
		if (distance < 0.05 || distance > REACH) {
			return null;
		}
		Vec3d hit = start.add(direction.multiply(distance));
		double x = localX(hit);
		double y = localY(hit);
		if (Math.abs(x) > HALF_W + 0.08 || y > HALF_H + 0.06 || y < -HALF_H - 0.02) {
			return null;
		}
		return hit;
	}

	private static boolean isCloserThanBlock(PlayerEntity player, Vec3d blockHit) {
		Vec3d hit = planeHit(player);
		if (hit == null || blockHit == null) {
			return false;
		}
		Vec3d start = player.getCameraPosVec(1.0F);
		return hit.squaredDistanceTo(start) < blockHit.squaredDistanceTo(start);
	}

	private static double localX(Vec3d hit) {
		double radians = Math.toRadians(yaw);
		return (hit.x - anchor.x) * Math.cos(radians) - (hit.z - anchor.z) * Math.sin(radians);
	}

	private static double localY(Vec3d hit) {
		return hit.y - anchor.y;
	}
}
