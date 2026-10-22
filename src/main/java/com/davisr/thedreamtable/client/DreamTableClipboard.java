package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.block.DreamTableBlock;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.farm.MaterialSpec;
import com.davisr.thedreamtable.network.DepositMaterialPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
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
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DreamTableClipboard {
	private static final int ROWS_PER_PAGE = 6;
	private static final float HALF_W = 0.95F;
	private static final float HALF_H = 1.28F;
	private static final float TEXT = 0.0125F;
	private static final float TITLE_TEXT = 0.017F;
	private static final float PAGE_TEXT = 0.015F;
	private static final float ROW_TOP = 0.70F;
	private static final float ROW_STEP = 0.195F;
	private static final float ICON = 0.16F;
	private static final float LEFT = -0.78F;
	private static final float RIGHT = 0.78F;
	private static final float TITLE_Y = 1.02F;
	private static final float BAR_H = 0.07F;
	private static final float BUTTON_H = 0.18F;
	private static final float PREV_X0 = -0.78F;
	private static final float PREV_X1 = -0.36F;
	private static final float NEXT_X0 = 0.36F;
	private static final float NEXT_X1 = 0.78F;

	private static BlockPos pinned;
	private static Box hitbox;
	private static Vec3d anchor = Vec3d.ZERO;
	private static float yaw;
	private static int page;
	private static int pageCount = 1;
	private static boolean useWasDown;
	private static int lastDepositAge = -1;
	private static float itemLabelY = -0.48F;
	private static float itemBarTop = -0.64F;
	private static float buildLabelY = -0.76F;
	private static float buildBarTop = -0.92F;
	private static float buttonY0 = -1.18F;
	private static float buttonY1 = -1.00F;

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

	public static void pin(BlockPos pos) {
		pinned = pos;
		page = 0;
	}

	public static void close() {
		pinned = null;
		hitbox = null;
		page = 0;
	}

	private static void render(WorldRenderContext context) {
		hitbox = null;
		if (context.world() == null || context.matrixStack() == null || context.camera() == null) {
			return;
		}
		BlockPos pos = pinned != null ? pinned : DreamTableHologram.trackedTable();
		if (pos == null || !(context.world().getBlockState(pos).getBlock() instanceof DreamTableBlock)) {
			return;
		}
		BlockEntity blockEntity = context.world().getBlockEntity(pos);
		if (!(blockEntity instanceof DreamTableBlockEntity table) || table.getSelectedFarm().isEmpty()) {
			return;
		}
		boolean show = table.isRequirementsBoard() || (pinned != null && pinned.equals(pos));
		if (table.isRequirementsBoard()) {
			pinned = pos;
		}
		if (!show) {
			return;
		}
		FarmTemplate template = FarmTemplates.get(table.getSelectedFarm());
		if (template == null) {
			return;
		}

		Direction facing = context.world().getBlockState(pos).get(Properties.HORIZONTAL_FACING);
		Direction side = facing.rotateYClockwise();
		anchor = Vec3d.ofCenter(pos).add(side.getOffsetX() * 3.4, 1.55, side.getOffsetZ() * 3.4);
		hitbox = new Box(anchor, anchor).expand(HALF_W + 0.15, HALF_H + 0.15, 0.45);
		yaw = -facing.asRotation();

		List<Line> lines = lines(table, template);
		pageCount = Math.max(1, (lines.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
		page = Math.min(page, pageCount - 1);
		int from = page * ROWS_PER_PAGE;
		int to = Math.min(lines.size(), from + ROWS_PER_PAGE);
		int itemHave = 0;
		int itemNeed = 0;
		for (Line line : lines) {
			itemHave += Math.min(line.have(), line.need());
			itemNeed += line.need();
		}

		Vec3d camera = context.camera().getPos();
		MatrixStack matrices = context.matrixStack();
		matrices.push();
		matrices.translate(anchor.x - camera.x, anchor.y - camera.y, anchor.z - camera.z);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
		layout();
		float itemProgress = itemNeed == 0 ? 0.0F : itemHave / (float) itemNeed;
		float buildProgress = table.totalSteps() == 0 ? 0.0F : table.getBuildIndex() / (float) table.totalSteps();
		drawBoard(matrices);
		drawSheet(matrices, lines.subList(from, to), itemHave, itemNeed, table.getBuildIndex(), table.totalSteps(), itemProgress, buildProgress);
		matrices.pop();
	}

	private static void layout() {
		TextRenderer text = MinecraftClient.getInstance().textRenderer;
		float body = text.fontHeight * TEXT;
		float lastRow = ROW_TOP - (ROWS_PER_PAGE - 1) * ROW_STEP;
		itemLabelY = lastRow - body - 0.12F;
		itemBarTop = itemLabelY - body - 0.04F;
		buildLabelY = itemBarTop - BAR_H - 0.05F;
		buildBarTop = buildLabelY - body - 0.04F;
		buttonY1 = buildBarTop - BAR_H - 0.055F;
		buttonY0 = buttonY1 - BUTTON_H;
	}

	private static void drawBoard(MatrixStack matrices) {
		BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		flat(buffer, matrices, -HALF_W - 0.08F, -HALF_H - 0.06F, HALF_W + 0.08F, HALF_H + 0.1F, -0.02F, 0.49F, 0.33F, 0.16F);
		flat(buffer, matrices, -HALF_W, -HALF_H, HALF_W, HALF_H - 0.02F, 0.0F, 0.93F, 0.86F, 0.68F);
		flat(buffer, matrices, -0.16F, HALF_H - 0.1F, 0.16F, HALF_H + 0.09F, 0.012F, 0.35F, 0.37F, 0.40F);
		flat(buffer, matrices, -0.1F, HALF_H - 0.02F, 0.1F, HALF_H + 0.06F, 0.02F, 0.75F, 0.76F, 0.78F);
		flushColor(buffer);
	}

	private static void drawSheet(MatrixStack matrices, List<Line> pageLines, int itemHave, int itemNeed, int built, int steps, float itemProgress, float buildProgress) {
		MinecraftClient client = MinecraftClient.getInstance();
		TextRenderer text = client.textRenderer;
		int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
		float body = text.fontHeight * TEXT;
		PlayerEntity player = client.player;
		Vec3d look = player == null ? null : planeHit(player);
		Zone hover = look == null ? Zone.NONE : zoneAt(localX(look), localY(look));
		int hoverRow = look == null ? -1 : rowIndexAt(localX(look), localY(look), pageLines.size());
		ItemStack held = player == null ? ItemStack.EMPTY : player.getMainHandStack();

		BufferBuilder chrome = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		float titleBottom = TITLE_Y - text.fontHeight * TITLE_TEXT;
		flat(chrome, matrices, LEFT, titleBottom - 0.045F, RIGHT, titleBottom - 0.030F, 0.012F, 0.45F, 0.32F, 0.18F);
		flat(chrome, matrices, LEFT, itemLabelY + 0.055F, RIGHT, itemLabelY + 0.070F, 0.012F, 0.45F, 0.32F, 0.18F);
		float row = ROW_TOP;
		for (int index = 0; index < pageLines.size(); index++) {
			Line line = pageLines.get(index);
			boolean matchesHeld = !held.isEmpty() && held.isOf(line.stack().getItem());
			if (matchesHeld || index == hoverRow) {
				float top = row + 0.03F;
				float bottom = row - Math.max(body, ICON) - 0.02F;
				if (matchesHeld) {
					flat(chrome, matrices, LEFT - 0.02F, bottom, RIGHT + 0.02F, top, 0.01F, 0.72F, 0.84F, 0.52F);
				} else {
					flat(chrome, matrices, LEFT - 0.02F, bottom, RIGHT + 0.02F, top, 0.01F, 0.86F, 0.78F, 0.58F);
				}
			}
			row -= ROW_STEP;
		}
		progress(chrome, matrices, itemBarTop, itemProgress, 0.36F, 0.64F, 0.24F);
		progress(chrome, matrices, buildBarTop, buildProgress, 0.72F, 0.44F, 0.16F);
		if (pageCount > 1) {
			plate(chrome, matrices, PREV_X0, buttonY0, PREV_X1, buttonY1, page > 0, hover == Zone.PREV);
			plate(chrome, matrices, NEXT_X0, buttonY0, NEXT_X1, buttonY1, page < pageCount - 1, hover == Zone.NEXT);
		}
		flushColor(chrome);

		if (pageCount > 1) {
			BufferBuilder arrows = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
			arrow(arrows, matrices, PREV_X0, buttonY0, PREV_X1, buttonY1, false, page > 0);
			arrow(arrows, matrices, NEXT_X0, buttonY0, NEXT_X1, buttonY1, true, page < pageCount - 1);
			flushColor(arrows);
		}

		List<Float> strikes = new ArrayList<>();
		List<Float> strikeFrom = new ArrayList<>();
		row = ROW_TOP;
		List<PlacedName> placed = new ArrayList<>();
		for (Line line : pageLines) {
			placed.add(placeName(text, line.stack().getName().getString(), line.have() + "/" + line.need()));
		}
		for (int index = 0; index < pageLines.size(); index++) {
			PlacedName name = placed.get(index);
			float iconY = row - name.height() * 0.5F - ICON * 0.5F;
			drawItemIcon(matrices, pageLines.get(index).stack(), LEFT, iconY, ICON);
			if (pageLines.get(index).done()) {
				float lineHeight = text.fontHeight * name.scale();
				float gap = name.lines().size() > 1 ? 0.006F : 0.0F;
				for (int line = 0; line < name.lines().size(); line++) {
					strikes.add(row - line * (lineHeight + gap) - lineHeight * 0.55F);
					strikeFrom.add(LEFT + ICON + 0.05F);
				}
			}
			row -= ROW_STEP;
		}
		DiffuseLighting.enableForLevel();

		label(matrices, text, light, LEFT, TITLE_Y, Text.translatable("gui.the_dream_table.requirements"), TITLE_TEXT);
		label(matrices, text, light, LEFT, itemLabelY, Text.translatable("gui.the_dream_table.items"), TEXT);
		labelRight(matrices, text, light, RIGHT, itemLabelY, Text.literal(itemHave + "/" + itemNeed), TEXT);
		label(matrices, text, light, LEFT, buildLabelY, Text.translatable("gui.the_dream_table.blocks"), TEXT);
		labelRight(matrices, text, light, RIGHT, buildLabelY, Text.literal(built + "/" + steps), TEXT);
		if (pageCount > 1) {
			float pageHeight = text.fontHeight * PAGE_TEXT;
			float pageY = buttonY1 - (BUTTON_H - pageHeight) * 0.5F;
			labelCenter(matrices, text, light, 0.0F, pageY, Text.translatable("gui.the_dream_table.count", page + 1, pageCount), PAGE_TEXT);
		}

		row = ROW_TOP;
		for (int index = 0; index < pageLines.size(); index++) {
			Line line = pageLines.get(index);
			PlacedName name = placed.get(index);
			Text count = Text.literal(line.have() + "/" + line.need());
			float nameX = LEFT + ICON + 0.06F;
			float lineHeight = text.fontHeight * name.scale();
			float gap = name.lines().size() > 1 ? 0.006F : 0.0F;
			for (int lineIndex = 0; lineIndex < name.lines().size(); lineIndex++) {
				label(matrices, text, light, nameX, row - lineIndex * (lineHeight + gap), Text.literal(name.lines().get(lineIndex)), name.scale());
			}
			labelRight(matrices, text, light, RIGHT, row, count, TEXT);
			row -= ROW_STEP;
		}

		if (!strikes.isEmpty()) {
			BufferBuilder strike = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
			for (int index = 0; index < strikes.size(); index++) {
				float mid = strikes.get(index);
				float thick = 0.007F;
				flat(strike, matrices, strikeFrom.get(index), mid - thick, RIGHT, mid + thick, 0.055F, 0.29F, 0.18F, 0.08F);
			}
			flushColor(strike);
		}
	}

	private static void drawItemIcon(MatrixStack matrices, ItemStack stack, float x, float y, float size) {
		if (stack.isEmpty()) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		BakedModel model = client.getItemRenderer().getModel(stack, client.world, null, 0);
		matrices.push();
		matrices.translate(x + size * 0.5F, y + size * 0.5F, size * 0.5F + 0.02F);
		matrices.scale(size, size, size);
		DiffuseLighting.disableGuiDepthLighting();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		client.getItemRenderer().renderItem(stack, ModelTransformationMode.NONE, false, matrices, client.getBufferBuilders().getEntityVertexConsumers(), LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, model);
		client.getBufferBuilders().getEntityVertexConsumers().draw();
		matrices.pop();
	}

	private static PlacedName placeName(TextRenderer text, String name, String count) {
		float nameX = LEFT + ICON + 0.06F;
		float countWorld = text.getWidth(count) * TEXT;
		float firstWorld = Math.max(0.25F, RIGHT - 0.04F - countWorld - nameX);
		float fullWorld = Math.max(firstWorld, RIGHT - 0.02F - nameX);
		float room = ROW_STEP - 0.05F;
		if (text.getWidth(name) * TEXT <= firstWorld) {
			return new PlacedName(List.of(name), TEXT, text.fontHeight * TEXT);
		}
		for (float scale = TEXT; scale >= 0.0075F; scale -= 0.0005F) {
			int first = Math.max(4, (int) (firstWorld / scale));
			int next = Math.max(first, (int) (fullWorld / scale));
			List<String> lines = wrapName(text, name, first, next);
			if (lines == null) {
				continue;
			}
			float gap = lines.size() > 1 ? 0.006F : 0.0F;
			float height = lines.size() * text.fontHeight * scale + (lines.size() - 1) * gap;
			if (height <= room) {
				return new PlacedName(lines, scale, height);
			}
		}
		int next = Math.max(8, (int) (fullWorld / 0.0075F));
		return new PlacedName(List.of(fit(text, name, next)), 0.0075F, text.fontHeight * 0.0075F);
	}

	private static List<String> wrapName(TextRenderer text, String name, int first, int next) {
		List<String> lines = new ArrayList<>();
		String rest = name;
		for (int line = 0; line < 2 && !rest.isEmpty(); line++) {
			int limit = line == 0 ? first : next;
			if (text.getWidth(rest) <= limit) {
				lines.add(rest);
				return lines;
			}
			String piece = text.trimToWidth(rest, limit);
			if (piece.isEmpty()) {
				return null;
			}
			int space = piece.lastIndexOf(' ');
			if (space >= 4) {
				piece = piece.substring(0, space);
			}
			lines.add(piece);
			rest = rest.substring(piece.length()).stripLeading();
		}
		return rest.isEmpty() ? lines : null;
	}

	private static void progress(BufferBuilder buffer, MatrixStack matrices, float top, float amount, float red, float green, float blue) {
		float bottom = top - BAR_H;
		flat(buffer, matrices, LEFT - 0.012F, bottom - 0.012F, RIGHT + 0.012F, top + 0.012F, 0.012F, 0.33F, 0.22F, 0.12F);
		flat(buffer, matrices, LEFT, bottom, RIGHT, top, 0.016F, 0.42F, 0.31F, 0.20F);
		float width = (RIGHT - LEFT) * Math.min(1.0F, Math.max(0.0F, amount));
		if (width > 0.004F) {
			flat(buffer, matrices, LEFT, bottom, LEFT + width, top, 0.02F, red, green, blue);
		}
	}

	private static void plate(BufferBuilder buffer, MatrixStack matrices, float x0, float y0, float x1, float y1, boolean enabled, boolean hover) {
		float red;
		float green;
		float blue;
		if (!enabled) {
			red = 0.68F;
			green = 0.60F;
			blue = 0.48F;
		} else if (hover) {
			red = 0.97F;
			green = 0.84F;
			blue = 0.52F;
		} else {
			red = 0.80F;
			green = 0.64F;
			blue = 0.38F;
		}
		flat(buffer, matrices, x0 - 0.014F, y0 - 0.014F, x1 + 0.014F, y1 + 0.014F, 0.014F, 0.34F, 0.22F, 0.11F);
		float inset = hover && enabled ? 0.012F : 0.0F;
		flat(buffer, matrices, x0 + inset, y0 + inset, x1 - inset, y1 - inset, 0.02F, red, green, blue);
		if (enabled) {
			flat(buffer, matrices, x0 + 0.02F, y1 - 0.028F, x1 - 0.02F, y1 - 0.012F, 0.026F, hover ? 1.0F : 0.90F, hover ? 0.92F : 0.76F, hover ? 0.64F : 0.50F);
		}
	}

	private static void arrow(BufferBuilder buffer, MatrixStack matrices, float x0, float y0, float x1, float y1, boolean right, boolean enabled) {
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		float centerX = (x0 + x1) * 0.5F;
		float centerY = (y0 + y1) * 0.5F;
		float direction = right ? 1.0F : -1.0F;
		float red = enabled ? 0.27F : 0.52F;
		float green = enabled ? 0.16F : 0.44F;
		float blue = enabled ? 0.07F : 0.32F;
		float z = 0.032F;
		buffer.vertex(matrix, centerX + direction * 0.075F, centerY, z).color(red, green, blue, 1.0F);
		buffer.vertex(matrix, centerX - direction * 0.045F, centerY + 0.055F, z).color(red, green, blue, 1.0F);
		buffer.vertex(matrix, centerX - direction * 0.045F, centerY - 0.055F, z).color(red, green, blue, 1.0F);
	}

	private static void playPage() {
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0F));
	}

	private static void label(MatrixStack matrices, TextRenderer text, int light, float x, float y, Text content, float scale) {
		matrices.push();
		matrices.translate(x, y, 0.04F);
		matrices.scale(scale, -scale, scale);
		text.draw(content, 0, 0, 0xFF3A2918, false, matrices.peek().getPositionMatrix(), MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers(), TextRenderer.TextLayerType.NORMAL, 0, light);
		matrices.pop();
		MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers().draw();
	}

	private static void labelRight(MatrixStack matrices, TextRenderer text, int light, float right, float y, Text content, float scale) {
		label(matrices, text, light, right - text.getWidth(content) * scale, y, content, scale);
	}

	private static void labelCenter(MatrixStack matrices, TextRenderer text, int light, float center, float y, Text content, float scale) {
		label(matrices, text, light, center - text.getWidth(content) * scale * 0.5F, y, content, scale);
	}

	private static String fit(TextRenderer text, String value, int maxPixels) {
		if (text.getWidth(value) <= maxPixels) {
			return value;
		}
		String ellipsis = "...";
		int limit = Math.max(0, maxPixels - text.getWidth(ellipsis));
		return text.trimToWidth(value, limit) + ellipsis;
	}

	private static void flat(BufferBuilder buffer, MatrixStack matrices, float x0, float y0, float x1, float y1, float z, float r, float g, float b) {
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		buffer.vertex(matrix, x0, y0, z).color(r, g, b, 1.0F);
		buffer.vertex(matrix, x0, y1, z).color(r, g, b, 1.0F);
		buffer.vertex(matrix, x1, y1, z).color(r, g, b, 1.0F);
		buffer.vertex(matrix, x1, y0, z).color(r, g, b, 1.0F);
	}

	private static void flushColor(BufferBuilder buffer) {
		BuiltBuffer built = buffer.endNullable();
		if (built == null) {
			return;
		}
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		BufferRenderer.drawWithGlobalProgram(built);
		RenderSystem.enableCull();
	}

	private static boolean interact(PlayerEntity player) {
		Vec3d point = planeHit(player);
		if (point == null) {
			return false;
		}
		layout();
		Zone zone = zoneAt(localX(point), localY(point));
		if (zone != Zone.NONE) {
			if (player.age != lastDepositAge) {
				lastDepositAge = player.age;
				if (zone == Zone.PREV && page > 0) {
					page--;
					playPage();
				} else if (zone == Zone.NEXT && page < pageCount - 1) {
					page++;
					playPage();
				}
			}
			return true;
		}
		return deposit(player);
	}

	private static boolean deposit(PlayerEntity player) {
		if (hitbox == null || (pinned == null && DreamTableHologram.trackedTable() == null)) {
			return false;
		}
		if (player.age == lastDepositAge) {
			return true;
		}
		BlockPos pos = pinned != null ? pinned : DreamTableHologram.trackedTable();
		if (pos == null || !hits(player)) {
			return false;
		}
		lastDepositAge = player.age;
		ClientPlayNetworking.send(new DepositMaterialPayload(pos));
		return true;
	}

	private static boolean hits(PlayerEntity player) {
		return planeHit(player) != null;
	}

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
		double offsetX = start.x - anchor.x;
		double offsetZ = start.z - anchor.z;
		double distance = -(offsetX * sine + offsetZ * cosine) / denominator;
		if (distance < 0.05 || distance > 7.0) {
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

	private static Zone zoneAt(double x, double y) {
		if (pageCount <= 1 || y > buttonY1 + 0.03 || y < buttonY0 - 0.03) {
			return Zone.NONE;
		}
		if (x >= PREV_X0 - 0.03 && x <= PREV_X1 + 0.03) {
			return Zone.PREV;
		}
		if (x >= NEXT_X0 - 0.03 && x <= NEXT_X1 + 0.03) {
			return Zone.NEXT;
		}
		return Zone.NONE;
	}

	private static int rowIndexAt(double x, double y, int rows) {
		if (rows <= 0 || x < LEFT - 0.02 || x > RIGHT + 0.02) {
			return -1;
		}
		float body = MinecraftClient.getInstance().textRenderer.fontHeight * TEXT;
		float band = Math.max(body, ICON) + 0.04F;
		for (int index = 0; index < rows; index++) {
			float top = ROW_TOP - index * ROW_STEP + 0.03F;
			if (y <= top && y >= top - band) {
				return index;
			}
		}
		return -1;
	}

	private static double localX(Vec3d hit) {
		double dx = hit.x - anchor.x;
		double dz = hit.z - anchor.z;
		double radians = Math.toRadians(yaw);
		return dx * Math.cos(radians) - dz * Math.sin(radians);
	}

	private static double localY(Vec3d hit) {
		return hit.y - anchor.y;
	}

	private static List<Line> lines(DreamTableBlockEntity table, FarmTemplate template) {
		Map<net.minecraft.item.Item, Integer> need = new LinkedHashMap<>();
		for (MaterialSpec spec : template.materials()) {
			need.merge(spec.item(), spec.count(), Integer::sum);
		}
		List<Line> lines = new ArrayList<>();
		for (Map.Entry<net.minecraft.item.Item, Integer> entry : need.entrySet()) {
			lines.add(new Line(entry.getKey().getDefaultStack(), table.stored(entry.getKey()), entry.getValue()));
		}
		return lines;
	}

	private record Line(ItemStack stack, int have, int need) {
		private boolean done() {
			return have >= need;
		}
	}

	private record PlacedName(List<String> lines, float scale, float height) {
	}

	private enum Zone {
		NONE,
		PREV,
		NEXT
	}

}
