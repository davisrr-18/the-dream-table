package com.davisr.thedreamtable.farm;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.ai.goal.PrioritizedGoal;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The goal Forge puts in place of an enderman's pickup goal refuses when told to and otherwise behaves as the original. */
class GuardedGoalTest {
	/** A goal that records every call it gets and starts only while {@code wantsToStart} is set. */
	private static final class Recording extends Goal {
		final List<String> calls = new ArrayList<>();
		boolean wantsToStart = true;

		Recording() {
			setControls(EnumSet.of(Control.MOVE));
		}

		@Override
		public boolean canStart() {
			calls.add("canStart");
			return wantsToStart;
		}

		@Override
		public boolean shouldContinue() {
			calls.add("shouldContinue");
			return false;
		}

		@Override
		public boolean canStop() {
			calls.add("canStop");
			return false;
		}

		@Override
		public void start() {
			calls.add("start");
		}

		@Override
		public void stop() {
			calls.add("stop");
		}

		@Override
		public boolean shouldRunEveryTick() {
			calls.add("shouldRunEveryTick");
			return true;
		}

		@Override
		public void tick() {
			calls.add("tick");
		}
	}

	/** The wrapper installed over {@code original} at {@code priority}, with the other goals left where they were. */
	private static Goal guarded(GoalSelector selector, Goal original, AtomicBoolean allowed) {
		assertTrue(GuardedGoal.guard(selector, goal -> goal == original, allowed::get));
		return selector.getGoals().stream().map(PrioritizedGoal::getGoal).filter(goal -> goal instanceof GuardedGoal).findFirst().orElseThrow();
	}

	@Test
	void theWrapperTakesTheOriginalsPlaceAtTheSamePriority() {
		GoalSelector selector = new GoalSelector();
		Recording other = new Recording();
		Recording original = new Recording();
		selector.add(2, other);
		selector.add(11, original);
		Goal wrapper = guarded(selector, original, new AtomicBoolean(true));
		assertEquals(2, selector.getGoals().size());
		for (PrioritizedGoal entry : selector.getGoals()) {
			assertFalse(entry.getGoal() == original);
			assertEquals(entry.getGoal() == other ? 2 : 11, entry.getPriority());
		}
		assertTrue(selector.getGoals().stream().anyMatch(entry -> entry.getGoal() == wrapper && entry.getPriority() == 11));
	}

	@Test
	void aRefusalStopsTheGoalOnlyWhenTheGameWouldHaveStartedIt() {
		GoalSelector selector = new GoalSelector();
		Recording original = new Recording();
		selector.add(11, original);
		AtomicBoolean allowed = new AtomicBoolean(false);
		Goal wrapper = guarded(selector, original, allowed);
		assertFalse(wrapper.canStart());
		allowed.set(true);
		assertTrue(wrapper.canStart());
		original.wantsToStart = false;
		assertFalse(wrapper.canStart());
		assertEquals(List.of("canStart", "canStart", "canStart"), original.calls);
	}

	@Test
	void theCheckIsNotAskedWhenTheGameWouldNotStartTheGoal() {
		GoalSelector selector = new GoalSelector();
		Recording original = new Recording();
		original.wantsToStart = false;
		selector.add(11, original);
		AtomicBoolean asked = new AtomicBoolean(false);
		assertTrue(GuardedGoal.guard(selector, goal -> goal == original, () -> asked.getAndSet(true)));
		selector.getGoals().iterator().next().getGoal().canStart();
		assertFalse(asked.get());
	}

	@Test
	void everythingElseGoesToTheOriginal() {
		GoalSelector selector = new GoalSelector();
		Recording original = new Recording();
		selector.add(11, original);
		Goal wrapper = guarded(selector, original, new AtomicBoolean(false));
		wrapper.start();
		wrapper.tick();
		wrapper.stop();
		assertFalse(wrapper.shouldContinue());
		assertFalse(wrapper.canStop());
		assertTrue(wrapper.shouldRunEveryTick());
		assertEquals(List.of("start", "tick", "stop", "shouldContinue", "canStop", "shouldRunEveryTick"), original.calls);
		assertSame(original.getControls(), wrapper.getControls());
		assertEquals(EnumSet.of(Goal.Control.MOVE), wrapper.getControls());
	}

	@Test
	void aGuardedGoalIsNeverWrappedAgainAndNoMatchChangesNothing() {
		GoalSelector selector = new GoalSelector();
		Recording original = new Recording();
		selector.add(11, original);
		AtomicBoolean allowed = new AtomicBoolean(true);
		Goal wrapper = guarded(selector, original, allowed);
		assertFalse(GuardedGoal.guard(selector, goal -> true, allowed::get));
		assertEquals(1, selector.getGoals().size());
		assertSame(wrapper, selector.getGoals().iterator().next().getGoal());
		assertFalse(GuardedGoal.guard(new GoalSelector(), goal -> true, allowed::get));
	}
}
