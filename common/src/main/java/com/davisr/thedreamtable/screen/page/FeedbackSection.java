package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.client.ClientSettings;
import com.davisr.thedreamtable.client.feedback.FeedbackCategory;
import com.davisr.thedreamtable.client.feedback.FeedbackEndpoint;
import com.davisr.thedreamtable.client.feedback.FeedbackJson;
import com.davisr.thedreamtable.client.feedback.FeedbackRules;
import com.davisr.thedreamtable.client.feedback.FeedbackRules.Block;
import com.davisr.thedreamtable.client.feedback.FeedbackSender;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import com.davisr.thedreamtable.screen.ui.ControlId;
import com.davisr.thedreamtable.screen.ui.FeedbackForm;
import com.davisr.thedreamtable.screen.ui.layout.UiRect;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.Optional;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/**
 * The feedback card on Settings: a switch, what is sent, and while the switch is on, the text box, the category row and
 * Send. Send names why it is off on its second line; clicking it while feedback is not available says so as well.
 */
public final class FeedbackSection {
	public static final ControlId CARD = ControlId.of("page." + DreamTableScreenHandler.PAGE_OPTIONS, "feedback");
	public static final ControlId FIELD = ControlId.of("page." + DreamTableScreenHandler.PAGE_OPTIONS, "feedback.text");
	private static final String CARD_KEY = "feedback";
	private static final String FIELD_KEY = "feedback.text";
	private static final String SEND_KEY = "feedback.send";
	private static final String ON = "gui.the_dream_table.feedback_on";
	private static final String OFF = "gui.the_dream_table.feedback_off";
	private static final String EXPLAIN = "gui.the_dream_table.feedback.explain";
	private static final int CATEGORIES = FeedbackCategory.values().length;

	private final PageContext ui;
	private final FeedbackForm form;

	public FeedbackSection(PageContext ui, FeedbackForm form) {
		this.ui = ui;
		this.form = form;
	}

	/** The height from the section's top edge to its bottom. */
	public int height() {
		return layout(0).bottom();
	}

	public void draw(DrawContext context, int mouseX, int mouseY, int top) {
		boolean on = ClientSettings.feedbackEnabled();
		FeedbackLayout rows = layout(top);
		int x = ui.view().flowX();
		int width = ui.view().flowWidth();
		ui.cardAt(CARD_KEY, context, new ItemStack(Items.WRITABLE_BOOK), Text.translatable(on ? ON : OFF),
				x, rows.card(), width, mouseX, mouseY, on, true);
		ui.flowNote(context, Text.translatable(EXPLAIN), rows.explanation(), HINT);
		if (on) {
			drawForm(context, mouseX, mouseY, rows, x, width);
		}
	}

	/** Handles a click on the section; false leaves the click to the text box or the rest of the page. */
	public boolean click(double mouseX, double mouseY, int top) {
		FeedbackLayout rows = layout(top);
		int x = ui.view().flowX();
		int width = ui.view().flowWidth();
		if (ui.cardHit(mouseX, mouseY, x, rows.card(), width)) {
			ClientSettings.setFeedbackEnabled(!ClientSettings.feedbackEnabled());
			ui.actions().click();
			return true;
		}
		if (!ClientSettings.feedbackEnabled()) {
			return false;
		}
		for (FeedbackCategory category : FeedbackCategory.values()) {
			if (ui.view().hit(mouseX, mouseY, segmentX(x, width, category), rows.categories(), segmentWidth(width, category), BUTTON_HEIGHT)) {
				form.choose(category);
				ui.actions().click();
				return true;
			}
		}
		if (ui.buttonHit(mouseX, mouseY, x, rows.send())) {
			send();
			return true;
		}
		return false;
	}

	private FeedbackLayout layout(int top) {
		return FeedbackLayout.of(top, ui.painter().text().wrapLines(Text.translatable(EXPLAIN), ui.view().flowWidth()).size(),
				ClientSettings.feedbackEnabled());
	}

	private void drawForm(DrawContext context, int mouseX, int mouseY, FeedbackLayout rows, int x, int width) {
		UiRect box = new UiRect(x, rows.field(), width, FeedbackLayout.FIELD_HEIGHT);
		form.place(box.x(), box.y(), box.width(), box.height(), ui.view().visible(box));
		ui.fieldAt(FIELD_KEY, Text.translatable("gui.the_dream_table.feedback.field"), x, rows.field(), width, FeedbackLayout.FIELD_HEIGHT, true);
		form.draw(context, mouseX, mouseY);
		drawCounter(context, x, width, rows.counter());
		for (FeedbackCategory category : FeedbackCategory.values()) {
			ui.segmentAt("feedback." + category.wire(), context,
					Text.translatable("gui.the_dream_table.feedback.category." + category.wire()),
					segmentX(x, width, category), rows.categories(), segmentWidth(width, category), mouseX, mouseY,
					category == form.category(), true);
		}
		Optional<Block> blocked = blocked();
		ui.buttonAt(SEND_KEY, context, Text.translatable("gui.the_dream_table.feedback.send"),
				blocked.map(FeedbackSection::reason).orElse(Text.empty()), x, rows.send(), mouseX, mouseY, blocked.isEmpty(), true);
	}

	private void drawCounter(DrawContext context, int x, int width, int y) {
		TextRenderer text = ui.painter().text();
		OrderedText count = Text.translatable("gui.the_dream_table.feedback.count",
				FeedbackJson.cleanText(form.text()).length(), FeedbackJson.MAX_LENGTH).asOrderedText();
		context.drawText(text, count, x + width - text.getWidth(count), y, HINT, false);
	}

	private void send() {
		Optional<Block> blocked = blocked();
		if (blocked.isEmpty()) {
			FeedbackSender.send(form.text(), form.category(), form::clear);
			ui.actions().click();
		} else if (blocked.get() == Block.NOT_AVAILABLE) {
			ui.message("gui.the_dream_table.feedback.not_available");
		}
	}

	private Optional<Block> blocked() {
		return FeedbackRules.blocked(form.text(), FeedbackEndpoint.available(), FeedbackSender.sending(),
				Util.getMeasuringTimeMs(), FeedbackSender.nextAllowed());
	}

	private static Text reason(Block block) {
		return Text.translatable("gui.the_dream_table.feedback.reason." + switch (block) {
			case NOT_AVAILABLE -> "not_available";
			case BUSY -> "busy";
			case COOLDOWN -> "cooldown";
			case TOO_SHORT -> "too_short";
			case TOO_LONG -> "too_long";
		});
	}

	/** The three category buttons share the row; the last one takes the rounding remainder. */
	private static int segmentWidth(int width, FeedbackCategory category) {
		int segment = (width - BUTTON_GAP * (CATEGORIES - 1)) / CATEGORIES;
		return category.ordinal() == CATEGORIES - 1 ? width - (segment + BUTTON_GAP) * (CATEGORIES - 1) : segment;
	}

	private static int segmentX(int x, int width, FeedbackCategory category) {
		int segment = (width - BUTTON_GAP * (CATEGORIES - 1)) / CATEGORIES;
		return x + category.ordinal() * (segment + BUTTON_GAP);
	}
}
