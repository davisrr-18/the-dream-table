package com.davisr.dreamtablepoc.client;

import com.davisr.dreamtablepoc.CounterMenu;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

public final class CounterScreen extends HandledScreen<CounterMenu> {
	public CounterScreen(CounterMenu handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		backgroundWidth = 176;
		backgroundHeight = 80;
	}

	@Override
	protected void init() {
		super.init();
		addDrawableChild(ButtonWidget.builder(Text.literal("Increment"),
						button -> PocClientHooks.requestIncrement(handler.pos()))
				.dimensions(x + 38, y + 44, 100, 20)
				.build());
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xFFC6C6C6);
		context.drawText(textRenderer, Text.literal("Counter: " + handler.counter()), x + 8, y + 24, 0x404040, false);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		context.drawText(textRenderer, title, titleX, titleY, 0x404040, false);
	}
}
