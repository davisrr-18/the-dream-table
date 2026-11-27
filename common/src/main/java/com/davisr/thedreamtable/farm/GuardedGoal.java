package com.davisr.thedreamtable.farm;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.ai.goal.PrioritizedGoal;

import java.util.EnumSet;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Wraps a goal so it can start only while {@code allowed} says so; everything else goes to the original. Forge 61 has
 * no event for an enderman's pickup goal, so Forge swaps that goal for this one when the enderman joins a world.
 */
public final class GuardedGoal extends Goal {
	private final Goal delegate;
	private final BooleanSupplier allowed;

	private GuardedGoal(Goal delegate, BooleanSupplier allowed) {
		this.delegate = delegate;
		this.allowed = allowed;
	}

	/**
	 * Replaces the first goal {@code target} accepts with a {@code GuardedGoal} at the same priority. Returns false when
	 * none matches. A guarded goal is never wrapped again.
	 */
	public static boolean guard(GoalSelector selector, Predicate<Goal> target, BooleanSupplier allowed) {
		// A copy, because removing and adding change the selector's own set.
		for (PrioritizedGoal entry : List.copyOf(selector.getGoals())) {
			Goal goal = entry.getGoal();
			if (!(goal instanceof GuardedGoal) && target.test(goal)) {
				selector.remove(goal);
				selector.add(entry.getPriority(), new GuardedGoal(goal, allowed));
				return true;
			}
		}
		return false;
	}

	/** Only after the game itself would start the goal, so {@code allowed} runs for the rare pickup, not every tick. */
	@Override
	public boolean canStart() {
		return delegate.canStart() && allowed.getAsBoolean();
	}

	@Override
	public boolean shouldContinue() {
		return delegate.shouldContinue();
	}

	@Override
	public boolean canStop() {
		return delegate.canStop();
	}

	@Override
	public void start() {
		delegate.start();
	}

	@Override
	public void stop() {
		delegate.stop();
	}

	@Override
	public boolean shouldRunEveryTick() {
		return delegate.shouldRunEveryTick();
	}

	@Override
	public void tick() {
		delegate.tick();
	}

	@Override
	public EnumSet<Control> getControls() {
		return delegate.getControls();
	}

	@Override
	public String toString() {
		return "Guarded " + delegate;
	}
}
