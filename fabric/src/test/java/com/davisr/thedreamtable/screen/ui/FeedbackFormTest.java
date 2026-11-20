package com.davisr.thedreamtable.screen.ui;

import com.davisr.thedreamtable.client.feedback.FeedbackJson;
import net.minecraft.client.font.FontStorage;
import net.minecraft.client.font.Glyph;
import net.minecraft.client.font.GlyphRenderer;
import net.minecraft.client.font.RenderableGlyph;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.EditBox;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The feedback box must wrap its text at the card width and keep its text, focus and limit when that width changes. */
class FeedbackFormTest {
	private static final String TEXT = "word ".repeat(60);

	@Test
	void aResizedBoxWrapsLikeABoxBuiltAtThatWidth() throws ReflectiveOperationException {
		TextRenderer font = font();
		FeedbackForm form = new FeedbackForm(font, Text.empty());
		form.place(0, 0, 200, 40, true);
		form.widget().setText(TEXT);
		form.place(0, 0, 120, 40, true);

		EditBoxWidget reference = new EditBoxWidget(font, 0, 0, 120, 40, Text.empty(), Text.empty());
		reference.setText(TEXT);
		assertTrue(lines(reference) > 1);
		assertEquals(lines(reference), lines(form.widget()));
	}

	/** Why place() rebuilds: a width setter alone leaves the wrap where the box was built. */
	@Test
	void setWidthAloneDoesNotReflowTheText() throws ReflectiveOperationException {
		EditBoxWidget box = new EditBoxWidget(font(), 0, 0, 120, 40, Text.empty(), Text.empty());
		box.setText(TEXT);
		int before = lines(box);
		box.setWidth(200);
		assertEquals(before, lines(box));
	}

	@Test
	void theTextAndTheLengthLimitSurviveAResize() {
		FeedbackForm form = new FeedbackForm(font(), Text.empty());
		form.place(0, 0, 200, 40, true);
		form.widget().setText("x".repeat(FeedbackJson.MAX_LENGTH + 50));
		String before = form.text();
		assertEquals(FeedbackJson.MAX_LENGTH, before.length());

		form.place(0, 0, 120, 40, true);
		assertEquals(before, form.text());
		form.widget().setText("y".repeat(FeedbackJson.MAX_LENGTH + 50));
		assertEquals(FeedbackJson.MAX_LENGTH, form.text().length());
	}

	@Test
	void theFocusFollowsTheNewBox() {
		FeedbackForm form = new FeedbackForm(font(), Text.empty());
		form.place(0, 0, 200, 40, true);
		form.widget().setFocused(true);
		EditBoxWidget old = form.widget();

		form.place(0, 0, 120, 40, true);
		assertNotSame(old, form.widget());
		assertTrue(form.focused());
	}

	@Test
	void anUnchangedPlacementKeepsTheBoxAndSetsItsPosition() {
		FeedbackForm form = new FeedbackForm(font(), Text.empty());
		form.place(10, 20, 200, 40, true);
		EditBoxWidget first = form.widget();
		form.place(30, 50, 200, 40, false);

		assertSame(first, form.widget());
		assertEquals(30, first.getX());
		assertEquals(50, first.getY());
		assertEquals(200, first.getWidth());
		assertEquals(40, first.getHeight());
		assertFalse(first.active);
		assertTrue(form.shown());
	}

	/**
	 * Every glyph is 6 pixels wide, so the wrap is exact and needs no game assets. The storage answers the glyph directly:
	 * setting fonts up would bake the built-in glyph, which needs the render thread.
	 */
	private static TextRenderer font() {
		FontStorage storage = new FontStorage(null, new Identifier("the_dream_table", "test")) {
			@Override
			public Glyph getGlyph(int codePoint, boolean bold) {
				return FIXED_GLYPH;
			}
		};
		return new TextRenderer(id -> storage, false);
	}

	/** The line count of the box's EditBox; EditBoxWidget keeps that field private and has no getter. */
	private static int lines(EditBoxWidget widget) throws ReflectiveOperationException {
		Field field = EditBoxWidget.class.getDeclaredField("editBox");
		field.setAccessible(true);
		return ((EditBox) field.get(widget)).getLineCount();
	}

	private static final Glyph FIXED_GLYPH = new Glyph() {
		@Override
		public float getAdvance() {
			return 6F;
		}

		@Override
		public GlyphRenderer bake(Function<RenderableGlyph, GlyphRenderer> baker) {
			return null;
		}
	};
}
