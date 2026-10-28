package com.davisr.thedreamtable.screen.ui;

import com.davisr.thedreamtable.screen.ui.layout.UiRect;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlRegistryTest {
	private static final ControlId BUILD = new ControlId("tab.build");
	private static final ControlId FARMS = new ControlId("tab.farms");
	private static final ControlId SCHEMATICS = new ControlId("tab.schematics");
	private static final ControlId FIRST = new ControlId("farm.first");
	private static final ControlId SECOND = new ControlId("farm.second");
	private static final ControlId THIRD = new ControlId("farm.third");

	@Test
	void tabAndShiftTabWrapInRegistrationOrder() {
		ControlRegistry registry = registry(tab(BUILD, 0), tab(FARMS, 1), item(FIRST, 0));

		assertEquals(BUILD, registry.focusedId().orElseThrow());
		assertEquals(FARMS, registry.moveTab(false).orElseThrow());
		assertEquals(FIRST, registry.moveTab(false).orElseThrow());
		assertEquals(BUILD, registry.moveTab(false).orElseThrow());
		assertEquals(FIRST, registry.moveTab(true).orElseThrow());
	}

	@Test
	void horizontalMovementStaysWithinPrimaryTabs() {
		ControlRegistry registry = registry(tab(BUILD, 0), tab(FARMS, 1), tab(SCHEMATICS, 2), item(FIRST, 0));
		registry.focus(FARMS);

		assertEquals(SCHEMATICS, registry.moveHorizontal(1).orElseThrow());
		assertEquals(BUILD, registry.moveHorizontal(1).orElseThrow());
		assertEquals(SCHEMATICS, registry.moveHorizontal(-1).orElseThrow());
	}

	@Test
	void verticalMovementUsesRowsAndNearestColumnWithinAGroup() {
		ControlRegistry registry = registry(
				item(FIRST, 0, 0), item(SECOND, 0, 1), item(THIRD, 1, 1)
		);
		registry.focus(SECOND);

		assertEquals(THIRD, registry.moveVertical(1).orElseThrow());
		assertEquals(SECOND, registry.moveVertical(-1).orElseThrow());
	}

	@Test
	void reconcilePreservesTheFocusedStableIdAcrossReordering() {
		ControlRegistry registry = registry(item(FIRST, 0), item(SECOND, 1), item(THIRD, 2));
		registry.focus(SECOND);

		registry.reconcile(List.of(item(THIRD, 0), item(SECOND, 1), item(FIRST, 2)));

		assertEquals(SECOND, registry.focusedId().orElseThrow());
	}

	@Test
	void removingTheFocusedControlFallsBackAtItsFormerPosition() {
		ControlRegistry registry = registry(item(FIRST, 0), item(SECOND, 1), item(THIRD, 2));
		registry.focus(SECOND);

		registry.reconcile(List.of(item(FIRST, 0), item(THIRD, 1)));

		assertEquals(THIRD, registry.focusedId().orElseThrow());
	}

	@Test
	void disabledControlNarrationIncludesStateAndReason() {
		ControlSpec disabled = new ControlSpec(
				FIRST, ControlGroup.CONTENT, new UiRect(0, 0, 20, 20), Text.literal("Repair"),
				Text.literal("12 missing"), false, false, Text.literal("Table busy"), 0, 0
		);

		String narration = disabled.narration().getString();

		assertTrue(narration.contains("Repair"));
		assertTrue(narration.contains("12 missing"));
		assertTrue(narration.contains("Table busy"));
	}

	@Test
	void activationReturnsOnlyAnEnabledFocusedControl() {
		ControlSpec disabled = new ControlSpec(
				SECOND, ControlGroup.CONTENT, new UiRect(0, 24, 20, 20), Text.literal("Disabled"),
				Text.empty(), false, false, Text.literal("Unavailable"), 1, 0
		);
		ControlRegistry registry = registry(item(FIRST, 0), disabled);

		assertEquals(FIRST, registry.activateFocused().orElseThrow());
		registry.focus(SECOND);
		assertTrue(registry.activateFocused().isEmpty());
	}

	@Test
	void focusChangesRequestThatControlBeRevealed() {
		ControlRegistry registry = registry(item(FIRST, 0), item(SECOND, 1));
		registry.takeRevealRequest();

		registry.moveTab(false);

		assertEquals(new UiRect(0, 24, 20, 20), registry.takeRevealRequest().orElseThrow());
		assertTrue(registry.takeRevealRequest().isEmpty());
	}

	@Test
	void emptyReconciliationClearsFocusAndActivation() {
		ControlRegistry registry = registry(item(FIRST, 0));

		registry.reconcile(List.of());

		assertTrue(registry.focusedId().isEmpty());
		assertTrue(registry.activateFocused().isEmpty());
		assertFalse(registry.focus(FIRST));
	}

	private static ControlRegistry registry(ControlSpec... controls) {
		ControlRegistry registry = new ControlRegistry();
		registry.reconcile(List.of(controls));
		return registry;
	}

	private static ControlSpec tab(ControlId id, int column) {
		return new ControlSpec(id, ControlGroup.PRIMARY_TABS, new UiRect(column * 24, 0, 20, 20),
				Text.literal(id.value()), Text.empty(), true, false, Text.empty(), 0, column);
	}

	private static ControlSpec item(ControlId id, int row) {
		return item(id, row, 0);
	}

	private static ControlSpec item(ControlId id, int row, int column) {
		return new ControlSpec(id, ControlGroup.CONTENT, new UiRect(column * 24, row * 24, 20, 20),
				Text.literal(id.value()), Text.empty(), true, false, Text.empty(), row, column);
	}
}
