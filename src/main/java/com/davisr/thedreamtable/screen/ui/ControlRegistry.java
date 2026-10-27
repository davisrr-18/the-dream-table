package com.davisr.thedreamtable.screen.ui;

import com.davisr.thedreamtable.screen.ui.layout.UiRect;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Pure focus state for the screen's current set of stable controls. */
public final class ControlRegistry {
	private List<ControlSpec> controls = List.of();
	private ControlId focused;
	private UiRect revealRequest;

	public void reconcile(List<ControlSpec> nextControls) {
		List<ControlSpec> next = List.copyOf(nextControls);
		int formerIndex = indexOf(focused);
		controls = next;
		if (controls.isEmpty()) {
			focused = null;
			revealRequest = null;
			return;
		}
		if (spec(focused).isPresent()) {
			return;
		}
		int fallback = formerIndex < 0 ? 0 : Math.min(formerIndex, controls.size() - 1);
		setFocus(controls.get(fallback));
	}

	public List<ControlSpec> controls() {
		return controls;
	}

	public Optional<ControlId> focusedId() {
		return Optional.ofNullable(focused);
	}

	public Optional<ControlSpec> focusedSpec() {
		return spec(focused);
	}

	public boolean focus(ControlId id) {
		Optional<ControlSpec> target = spec(id);
		target.ifPresent(this::setFocus);
		return target.isPresent();
	}

	public Optional<ControlId> moveTab(boolean reverse) {
		if (controls.isEmpty()) {
			return Optional.empty();
		}
		int current = indexOf(focused);
		int step = reverse ? -1 : 1;
		int target = current < 0 ? (reverse ? controls.size() - 1 : 0) : Math.floorMod(current + step, controls.size());
		return focusAt(target);
	}

	public Optional<ControlId> moveHorizontal(int direction) {
		if (direction == 0) {
			return focusedId();
		}
		List<ControlSpec> tabs = controls.stream().filter(control -> control.group() == ControlGroup.PRIMARY_TABS).toList();
		int current = indexOf(tabs, focused);
		if (current < 0 || tabs.isEmpty()) {
			return Optional.empty();
		}
		ControlSpec target = tabs.get(Math.floorMod(current + Integer.signum(direction), tabs.size()));
		setFocus(target);
		return Optional.of(target.id());
	}

	public Optional<ControlId> moveVertical(int direction) {
		if (direction == 0) {
			return focusedId();
		}
		Optional<ControlSpec> current = focusedSpec();
		if (current.isEmpty() || current.get().group() == ControlGroup.PRIMARY_TABS) {
			return Optional.empty();
		}
		int sign = Integer.signum(direction);
		ControlSpec origin = current.get();
		Optional<ControlSpec> target = controls.stream()
				.filter(control -> control.group() == origin.group())
				.filter(control -> sign > 0 ? control.row() > origin.row() : control.row() < origin.row())
				.min(Comparator
						.comparingInt((ControlSpec control) -> Math.abs(control.row() - origin.row()))
						.thenComparingInt(control -> Math.abs(control.column() - origin.column()))
						.thenComparingInt(controls::indexOf));
		target.ifPresent(this::setFocus);
		return target.map(ControlSpec::id);
	}

	public Optional<ControlId> activateFocused() {
		return focusedSpec().filter(ControlSpec::enabled).map(ControlSpec::id);
	}

	public Optional<UiRect> takeRevealRequest() {
		UiRect request = revealRequest;
		revealRequest = null;
		return Optional.ofNullable(request);
	}

	private Optional<ControlId> focusAt(int index) {
		ControlSpec target = controls.get(index);
		setFocus(target);
		return Optional.of(target.id());
	}

	private void setFocus(ControlSpec target) {
		focused = target.id();
		revealRequest = target.bounds();
	}

	private Optional<ControlSpec> spec(ControlId id) {
		return id == null ? Optional.empty() : controls.stream().filter(control -> control.id().equals(id)).findFirst();
	}

	private int indexOf(ControlId id) {
		return indexOf(controls, id);
	}

	private static int indexOf(List<ControlSpec> list, ControlId id) {
		if (id == null) {
			return -1;
		}
		for (int index = 0; index < list.size(); index++) {
			if (list.get(index).id().equals(id)) {
				return index;
			}
		}
		return -1;
	}
}
