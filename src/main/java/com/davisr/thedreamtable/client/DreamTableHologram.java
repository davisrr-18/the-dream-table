package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.block.DreamTableBlock;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.farm.FarmGeometry;
import com.davisr.thedreamtable.farm.FarmStep;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;

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
		if (tablePos == null || context.world() == null || context.matrixStack() == null) {
			return;
		}
		if (!tableStillThere(context.world())) {
			return;
		}
		String farm = farmId;
		int shownPlaced = placed;
		boolean shownBlocked = blocked;
		BlockEntity blockEntity = context.world().getBlockEntity(tablePos);
		if (blockEntity instanceof DreamTableBlockEntity table && !table.getSelectedFarm().isEmpty()) {
			farm = table.getSelectedFarm();
			shownPlaced = table.getBuildIndex();
			shownBlocked = DreamTableBlockEntity.PHASE_BLOCKED.equals(table.getPhase());
		}
		FarmTemplate template = FarmTemplates.get(farm);
		if (template == null) {
			return;
		}

		DreamTableBlockEntity table = blockEntity instanceof DreamTableBlockEntity dreamTable ? dreamTable : null;
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
			BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
			float mark = 0.35F + 0.2F * wave;
			WorldRenderer.renderFilledBox(matrices, buffer, cursor.getX() + 0.15, cursor.getY(), cursor.getZ() + 0.15, cursor.getX() + 0.85, cursor.getY() + 0.08, cursor.getZ() + 0.85, 0.95F, 0.86F, 0.45F, mark);
			drawColor(buffer);
		}
		if (placement != null) {
			drawGhosts(context, matrices, template, placement, shownPlaced, shownBlocked, wave);
		}
		matrices.pop();
	}

	private static void drawGhosts(WorldRenderContext context, MatrixStack matrices, FarmTemplate template, Placement placement, int shownPlaced, boolean blocked, float wave) {
		MinecraftClient client = MinecraftClient.getInstance();
		Random random = Random.create();
		BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
		boolean any = false;
		for (int index = shownPlaced; index < template.steps().size(); index++) {
			FarmStep step = template.steps().get(index);
			if (step.isEntity() || step.state() == null) {
				continue;
			}
			BlockPos pos = FarmGeometry.toAimed(placement.anchor(), placement.facing(), template, step);
			BlockState state = step.state().rotate(FarmGeometry.rotation(placement.facing()));
			boolean next = index == shownPlaced;
			float red = 0.62F;
			float green = 0.78F;
			float blue = 1.0F;
			int alpha = 185;
			if (next && blocked) {
				red = 1.0F;
				green = 0.48F;
				blue = 0.42F;
				alpha = 220;
			} else if (next) {
				red = 0.82F;
				green = 0.94F;
				blue = 1.0F;
				alpha = 210 + (int) (30 * wave);
			}
			matrices.push();
			matrices.translate(pos.getX() + 0.01, pos.getY() + 0.01, pos.getZ() + 0.01);
			matrices.scale(0.98F, 0.98F, 0.98F);
			BakedModel model = client.getBlockRenderManager().getModel(state);
			random.setSeed(pos.asLong());
			if (!ghostModel(buffer, matrices, context, state, pos, model, random, red, green, blue, alpha)) {
				ghostCube(buffer, matrices, model.getParticleSprite(), red, green, blue, alpha);
			}
			matrices.pop();
			any = true;
		}
		if (!any) {
			buffer.endNullable();
			return;
		}
		RenderSystem.setShaderTexture(0, PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
		RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		BuiltBuffer built = buffer.endNullable();
		if (built != null) {
			BufferRenderer.drawWithGlobalProgram(built);
		}
	}

	private static boolean ghostModel(BufferBuilder buffer, MatrixStack matrices, WorldRenderContext context, BlockState state, BlockPos pos, BakedModel model, Random random, float red, float green, float blue, int alpha) {
		boolean any = false;
		for (Direction face : Direction.values()) {
			for (BakedQuad quad : model.getQuads(state, face, random)) {
				ghostQuad(buffer, matrices, context, state, pos, quad, red, green, blue, alpha);
				any = true;
			}
		}
		for (BakedQuad quad : model.getQuads(state, null, random)) {
			ghostQuad(buffer, matrices, context, state, pos, quad, red, green, blue, alpha);
			any = true;
		}
		return any;
	}

	private static void ghostQuad(BufferBuilder buffer, MatrixStack matrices, WorldRenderContext context, BlockState state, BlockPos pos, BakedQuad quad, float red, float green, float blue, int alpha) {
		int[] data = quad.getVertexData();
		int stride = data.length / 4;
		if (stride < 6) {
			return;
		}
		float tintRed = 1.0F;
		float tintGreen = 1.0F;
		float tintBlue = 1.0F;
		if (quad.hasColor() && context.world() != null) {
			int tint = MinecraftClient.getInstance().getBlockColors().getColor(state, context.world(), pos, quad.getColorIndex());
			if (tint != -1) {
				tintRed = ((tint >> 16) & 255) / 255.0F;
				tintGreen = ((tint >> 8) & 255) / 255.0F;
				tintBlue = (tint & 255) / 255.0F;
			}
		}
		int colorRed = Math.min(255, (int) (tintRed * red * 255.0F));
		int colorGreen = Math.min(255, (int) (tintGreen * green * 255.0F));
		int colorBlue = Math.min(255, (int) (tintBlue * blue * 255.0F));
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		for (int vertex = 0; vertex < 4; vertex++) {
			int offset = vertex * stride;
			float x = Float.intBitsToFloat(data[offset]);
			float y = Float.intBitsToFloat(data[offset + 1]);
			float z = Float.intBitsToFloat(data[offset + 2]);
			float u = Float.intBitsToFloat(data[offset + 4]);
			float v = Float.intBitsToFloat(data[offset + 5]);
			buffer.vertex(matrix, x, y, z).texture(u, v).color(colorRed, colorGreen, colorBlue, alpha);
		}
	}

	private static void ghostCube(BufferBuilder buffer, MatrixStack matrices, Sprite sprite, float red, float green, float blue, int alpha) {
		if (sprite == null) {
			return;
		}
		int colorRed = Math.min(255, (int) (red * 255.0F));
		int colorGreen = Math.min(255, (int) (green * 255.0F));
		int colorBlue = Math.min(255, (int) (blue * 255.0F));
		float u0 = sprite.getMinU();
		float u1 = sprite.getMaxU();
		float v0 = sprite.getMinV();
		float v1 = sprite.getMaxV();
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		face(buffer, matrix, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1, u0, v0, u1, v1, colorRed, colorGreen, colorBlue, alpha);
		face(buffer, matrix, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0, u0, v0, u1, v1, colorRed, colorGreen, colorBlue, alpha);
		face(buffer, matrix, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0, u0, v0, u1, v1, colorRed, colorGreen, colorBlue, alpha);
		face(buffer, matrix, 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, u0, v0, u1, v1, colorRed, colorGreen, colorBlue, alpha);
		face(buffer, matrix, 1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, u0, v0, u1, v1, colorRed, colorGreen, colorBlue, alpha);
		face(buffer, matrix, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0, 0, 0, u0, v0, u1, v1, colorRed, colorGreen, colorBlue, alpha);
	}

	private static void face(BufferBuilder buffer, Matrix4f matrix, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float u0, float v0, float u1, float v1, int red, int green, int blue, int alpha) {
		buffer.vertex(matrix, x0, y0, z0).texture(u0, v1).color(red, green, blue, alpha);
		buffer.vertex(matrix, x1, y1, z1).texture(u0, v0).color(red, green, blue, alpha);
		buffer.vertex(matrix, x2, y2, z2).texture(u1, v0).color(red, green, blue, alpha);
		buffer.vertex(matrix, x3, y3, z3).texture(u1, v1).color(red, green, blue, alpha);
	}

	private static void drawColor(BufferBuilder buffer) {
		BuiltBuffer built = buffer.endNullable();
		if (built == null) {
			return;
		}
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		BufferRenderer.drawWithGlobalProgram(built);
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
	}

	private static boolean locked() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || tablePos == null) {
			return true;
		}
		if (!(client.world.getBlockEntity(tablePos) instanceof DreamTableBlockEntity table)) {
			return false;
		}
		return table.hasAnchor() && !DreamTableBlockEntity.PHASE_IDLE.equals(table.getPhase());
	}

	private static boolean tableStillThere(net.minecraft.world.World world) {
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

	private static Placement placement(DreamTableBlockEntity table) {
		if (table != null && table.hasAnchor() && !DreamTableBlockEntity.PHASE_IDLE.equals(table.getPhase())) {
			return new Placement(table.getFarmAnchor(), table.getFarmFacing());
		}
		if (!anchored || aimed == null) {
			return null;
		}
		return new Placement(aimed, previewFacing());
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

	private static BlockPos anchorFrom(BlockHitResult hit, net.minecraft.world.World world) {
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

	private record Placement(BlockPos anchor, Direction facing) {
	}
}
