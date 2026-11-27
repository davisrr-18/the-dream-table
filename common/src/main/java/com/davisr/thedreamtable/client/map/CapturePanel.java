package com.davisr.thedreamtable.client.map;

import com.davisr.thedreamtable.client.CaptureOutline;
import com.davisr.thedreamtable.client.DreamTableHologram;
import com.davisr.thedreamtable.farm.capture.AreaCapture;
import com.davisr.thedreamtable.farm.capture.CaptureAccess;
import com.davisr.thedreamtable.farm.capture.CaptureStructure;
import com.davisr.thedreamtable.farm.capture.CaptureBox;
import com.davisr.thedreamtable.farm.capture.CaptureName;
import com.davisr.thedreamtable.farm.capture.CaptureProblem;
import com.davisr.thedreamtable.farm.capture.CaptureScan;
import com.davisr.thedreamtable.farm.capture.CaptureSelection;
import com.davisr.thedreamtable.network.CaptureAreaPayload;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Capture inside map mode: the operator's or host's way to save the blocks of an area as a structure. The state of the
 * selection, what the screen says about it, and the name step that sends the request. The server checks everything again.
 */
final class CapturePanel {
	private static final int NAME_WIDTH = 200;
	private static final int BUTTON_WIDTH = 98;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int PROBLEM = 0xFFFF7777;
	private static final int FILL = 0x99000000;
	/** The fixed box must stay unchanged this many world ticks before its blocks are read. */
	private static final long SETTLE_TICKS = 5;
	/** A box that had an unloaded chunk is read again after this many world ticks. */
	private static final long RETRY_TICKS = 40;

	private final CaptureSelection selection = new CaptureSelection();
	private boolean active;
	private boolean naming;
	private String typed = "";
	private TextFieldWidget field;
	private ButtonWidget save;
	private CaptureBox box;
	private CaptureProblem problem;
	private CaptureBox scanned;
	private CaptureProblem scanProblem;
	private int scanBlocks;
	private long scannedAt;
	private CaptureBox seen;
	private long seenAt;
	/** The fixed box is still being moved, so its blocks have not been read yet. */
	private boolean pending;

	/** Whether this player may capture: the same rule the server applies, so the control is never shown to anyone it would refuse. */
	static boolean allowed(MinecraftClient client) {
		return client.player != null
				&& CaptureAccess.allowed(client.player.hasPermissionLevel(CaptureAccess.OPERATOR_LEVEL), client.isIntegratedServerRunning());
	}

	boolean active() {
		return active;
	}

	boolean naming() {
		return naming;
	}

	void toggle(MinecraftClient client) {
		if (allowed(client)) {
			active = !active;
			selection.clear();
			scanned = null;
			if (!active) {
				CaptureOutline.hide();
			}
		}
	}

	void click() {
		if (selection.fixed() && DreamTableHologram.mapAimed() != null) {
			// A new box is a new structure: its name starts empty again. After a save the old box and name stay, to rename or adjust.
			typed = "";
		}
		selection.click(DreamTableHologram.mapAimed());
	}

	/** The arrow keys move the top, Page Up and Page Down the bottom; true when the key was one of them. */
	boolean adjust(int keyCode) {
		switch (keyCode) {
			case GLFW.GLFW_KEY_UP -> selection.moveTop(1);
			case GLFW.GLFW_KEY_DOWN -> selection.moveTop(-1);
			case GLFW.GLFW_KEY_PAGE_UP -> selection.moveBottom(1);
			case GLFW.GLFW_KEY_PAGE_DOWN -> selection.moveBottom(-1);
			default -> {
				return false;
			}
		}
		return true;
	}

	/** Works out the box, what is wrong with it, and the outline, once per rendered frame. */
	void frame(MinecraftClient client) {
		box = selection.box(DreamTableHologram.mapAimed());
		problem = null;
		pending = false;
		ClientWorld world = client.world;
		if (box == null || world == null) {
			CaptureOutline.hide();
			return;
		}
		BlockPos table = DreamTableHologram.mapTable();
		problem = box.sizeProblem(world.getBottomY(), world.getTopY());
		if (problem == null && table != null && !box.inReachOf(table)) {
			problem = CaptureProblem.of(CaptureProblem.Kind.TOO_FAR);
		}
		if (problem == null && selection.fixed()) {
			problem = scan(world);
		}
		CaptureOutline.show(box.outline(), problem != null ? CaptureOutline.Status.REFUSED
				: selection.fixed() && !pending ? CaptureOutline.Status.READY : CaptureOutline.Status.AIMING);
	}

	/**
	 * Reads the fixed box once it has stayed the same for {@link #SETTLE_TICKS} (so holding a key to move a big box reads nothing in
	 * between), and again only to retry a box that had an unloaded chunk. The importer's verdict is asked on the result, as the
	 * server will ask it. While the box is still moving the answer is "none yet" and {@link #pending} is set.
	 */
	private CaptureProblem scan(ClientWorld world) {
		long now = world.getTime();
		if (!box.equals(seen) || now < seenAt) {
			seen = box;
			seenAt = now;
		}
		pending = false;
		boolean changed = !box.equals(scanned);
		if (changed && now - seenAt < SETTLE_TICKS) {
			pending = true;
			return null;
		}
		boolean retry = !changed && scanProblem != null && scanProblem.kind() == CaptureProblem.Kind.UNLOADED && now - scannedAt >= RETRY_TICKS;
		if (changed || retry) {
			// The client's chunk manager answers null for a chunk it does not hold, unlike ClientWorld.isChunkLoaded, which always says yes.
			CaptureScan.Result result = CaptureScan.read(box, (chunkX, chunkZ) -> {
				WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkX, chunkZ);
				return chunk == null ? null : chunk::getBlockState;
			});
			scanned = box;
			scanProblem = result.problem() != null ? result.problem()
					: AreaCapture.importProblem(CaptureStructure.toNbt(box, result.blocks(), 0));
			scanBlocks = result.blocks().size();
			scannedAt = now;
		}
		return scanProblem;
	}

	void hide() {
		CaptureOutline.hide();
	}

	/** The size and what to do next, above the controls panel whose top edge is {@code panelTop}. */
	void drawStatus(DrawContext context, TextRenderer text, int width, int panelTop) {
		int y = panelTop - 14;
		if (problem != null) {
			line(context, text, problem.text(), PROBLEM, width, y);
			y -= 14;
		}
		Text status;
		if (!selection.started()) {
			status = Text.translatable("gui.the_dream_table.capture_first");
		} else if (!selection.fixed()) {
			status = Text.translatable("gui.the_dream_table.capture_second", box.sizeX(), box.sizeY(), box.sizeZ());
		} else if (pending) {
			status = Text.translatable("gui.the_dream_table.capture_counting", box.sizeX(), box.sizeY(), box.sizeZ());
		} else if (problem == null) {
			status = Text.translatable("gui.the_dream_table.capture_ready", box.sizeX(), box.sizeY(), box.sizeZ(), scanBlocks);
		} else {
			status = Text.translatable("gui.the_dream_table.capture_size", box.sizeX(), box.sizeY(), box.sizeZ());
		}
		line(context, text, status, TEXT, width, y);
	}

	private static void line(DrawContext context, TextRenderer text, Text message, int color, int width, int y) {
		int lineWidth = text.getWidth(message);
		int x = (width - lineWidth) / 2;
		context.fill(x - 4, y - 2, x + lineWidth + 4, y + 11, FILL);
		context.drawTextWithShadow(text, message, x, y, color);
	}

	/** Starts the name step when the box is fixed and can be saved. */
	boolean beginNaming() {
		if (selection.fixed() && box != null && problem == null && !pending) {
			naming = true;
			return true;
		}
		return false;
	}

	void endNaming() {
		naming = false;
	}

	/** The name field and the Save and Cancel buttons, centred; the typed name is kept when the screen is laid out again. */
	List<ClickableWidget> widgets(TextRenderer text, int width, int height, Runnable onSaved, Runnable onCancel) {
		int left = (width - NAME_WIDTH) / 2;
		int top = height / 2 - 6;
		field = new TextFieldWidget(text, left, top, NAME_WIDTH, 18, Text.translatable("gui.the_dream_table.capture_name"));
		field.setMaxLength(CaptureName.MAX_LENGTH);
		field.setPlaceholder(Text.translatable("gui.the_dream_table.capture_name"));
		field.setText(typed);
		field.setChangedListener(value -> typed = value);
		save = ButtonWidget.builder(Text.translatable("gui.the_dream_table.capture_save"), button -> {
			if (submit()) {
				onSaved.run();
			}
		}).dimensions(left, top + 42, BUTTON_WIDTH, 20).build();
		ButtonWidget cancel = ButtonWidget.builder(Text.translatable("gui.the_dream_table.capture_cancel"), button -> onCancel.run())
				.dimensions(left + NAME_WIDTH - BUTTON_WIDTH, top + 42, BUTTON_WIDTH, 20).build();
		return List.of(field, save, cancel);
	}

	TextFieldWidget field() {
		return field;
	}

	/** Enter in the name field. */
	boolean submit() {
		String name = CaptureName.sanitize(typed);
		BlockPos table = DreamTableHologram.mapTable();
		if (name == null || table == null || box == null || problem != null || pending || !selection.fixed()) {
			return false;
		}
		// The box and the typed name stay: the client can't tell a saved capture from a refused one, so a refusal (the notice says
		// why) leaves everything to rename or adjust, and a new box starts with the next click.
		Platform.clientNetwork().send(new CaptureAreaPayload(table, box.min(), box.max(), typed));
		naming = false;
		return true;
	}

	void drawNaming(DrawContext context, TextRenderer text, int width, int height) {
		int left = (width - NAME_WIDTH) / 2;
		int top = height / 2 - 6;
		String name = CaptureName.sanitize(typed);
		save.active = name != null;
		context.fill(left - 10, top - 30, left + NAME_WIDTH + 10, top + 72, 0xCC000000);
		context.drawTextWithShadow(text, Text.translatable("gui.the_dream_table.capture_name_title"), left, top - 22, TEXT);
		context.drawTextWithShadow(text, name == null ? Text.translatable("gui.the_dream_table.capture_name_empty")
				: Text.translatable("gui.the_dream_table.capture_saves_as", name + ".nbt"), left, top + 24, name == null ? PROBLEM : TEXT);
	}
}
