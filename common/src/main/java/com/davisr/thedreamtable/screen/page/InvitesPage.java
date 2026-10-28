package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.client.LoadedFarmsView;
import com.davisr.thedreamtable.network.LoadedFarmsPayload;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/** Pending farm invitations addressed to the local player, with explicit Accept and Decline actions. */
public final class InvitesPage implements TablePage {
	private static final int ROW_HEIGHT = CARD_HEIGHT + (BUTTON_HEIGHT + BUTTON_GAP) * 2 + CARD_GAP;
	private final PageContext ui;

	public InvitesPage(PageContext ui) {
		this.ui = ui;
	}

	@Override
	public void draw(DrawContext context, int mouseX, int mouseY) {
		LoadedFarmsView.ensure();
		List<LoadedFarmsPayload.InviteEntry> invites = LoadedFarmsView.invites();
		if (invites.isEmpty()) {
			ui.flowNote(context, Text.translatable(LoadedFarmsView.received()
					? "gui.the_dream_table.invites_empty" : "gui.the_dream_table.loaded_waiting"), ui.view().cardY(0) + 8, HINT);
		}
		for (int index = 0; index < invites.size(); index++) {
			LoadedFarmsPayload.InviteEntry invite = invites.get(index);
			int y = rowY(index);
			Text detail = Text.translatable("gui.the_dream_table.invite_role",
					Text.translatable("gui.the_dream_table.role." + invite.role().name().toLowerCase(Locale.ROOT)));
			ui.cardAt("invite." + invite.inviteId(), context, new ItemStack(Items.WRITABLE_BOOK), Text.literal(invite.farmName()), detail,
					flowX(), y, mouseX, mouseY, false, true);
			ui.buttonAt("accept." + invite.inviteId(), context, Text.translatable("gui.the_dream_table.invite_accept"), flowX(),
					y + CARD_HEIGHT + BUTTON_GAP, mouseX, mouseY, true, true);
			ui.buttonAt("decline." + invite.inviteId(), context, Text.translatable("gui.the_dream_table.invite_decline"), flowX(),
					y + CARD_HEIGHT + BUTTON_GAP + BUTTON_HEIGHT + BUTTON_GAP, mouseX, mouseY, true, false);
		}
		ui.buttonAt("back", context, Text.translatable("gui.the_dream_table.back"), flowX(), backY(), mouseX, mouseY, true, false);
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		List<LoadedFarmsPayload.InviteEntry> invites = LoadedFarmsView.invites();
		for (int index = 0; index < invites.size(); index++) {
			LoadedFarmsPayload.InviteEntry invite = invites.get(index);
			int y = rowY(index) + CARD_HEIGHT + BUTTON_GAP;
			if (ui.buttonHit(mouseX, mouseY, flowX(), y)) {
				LoadedFarmsView.respond(invite.key(), invite.inviteId(), true);
				return true;
			}
			if (ui.buttonHit(mouseX, mouseY, flowX(), y + BUTTON_HEIGHT + BUTTON_GAP)) {
				LoadedFarmsView.respond(invite.key(), invite.inviteId(), false);
				return true;
			}
		}
		if (ui.buttonHit(mouseX, mouseY, flowX(), backY())) {
			ui.go(DreamTableScreenHandler.PAGE_LOADED, DreamTableScreenHandler.BUTTON_LOADED_FARMS);
			return true;
		}
		return false;
	}

	private int rowY(int index) {
		return ui.view().cardY(0) + index * ROW_HEIGHT;
	}

	private int backY() {
		return ui.view().cardY(0) + Math.max(1, LoadedFarmsView.invites().size()) * ROW_HEIGHT + 4;
	}

	private int flowX() {
		return ui.view().flowX();
	}

	@Override
	public int contentHeight() {
		return backY() - ui.view().cardY(0) + BUTTON_HEIGHT + PAD;
	}

	@Override
	public Text subtitle() {
		return Text.translatable("gui.the_dream_table.invites_title", LoadedFarmsView.invites().size());
	}
}
