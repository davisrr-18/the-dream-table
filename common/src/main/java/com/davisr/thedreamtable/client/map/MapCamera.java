package com.davisr.thedreamtable.client.map;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.function.Predicate;

/**
 * The free camera of map mode: a position and a look direction in Minecraft's convention, where yaw 0 faces south (+Z) and
 * a positive pitch looks down. Pure math, so it can be tested without a client.
 */
public final class MapCamera {
	/** Short of straight down, where the view's up direction would be undefined. */
	private static final double MAX_PITCH = 89.0;
	/** The longest step along one axis. A solid block is a whole block, so a step shorter than that cannot skip over one. */
	private static final double MAX_STEP = 0.25;

	private Vec3d position;
	private double yaw;
	private double pitch;

	public MapCamera(Vec3d position, double yaw, double pitch) {
		this.position = position;
		this.yaw = MathHelper.wrapDegrees(yaw);
		this.pitch = clampPitch(pitch);
	}

	public Vec3d position() {
		return position;
	}

	public double yaw() {
		return yaw;
	}

	public double pitch() {
		return pitch;
	}

	/**
	 * Moves by the input axes: forward and strafe (positive to the right) follow the horizontal yaw, vertical is world Y. The
	 * axes are normalized together, so a diagonal is no faster than a straight move; {@code speed} is blocks per call.
	 */
	public void move(double forward, double strafe, double vertical, double speed, Predicate<Vec3d> blocked, double floorY) {
		double length = Math.sqrt(forward * forward + strafe * strafe + vertical * vertical);
		if (length == 0.0) {
			return;
		}
		double scale = speed / length;
		sweep(horizontalForward().multiply(forward * scale).add(right().multiply(strafe * scale)).add(0.0, vertical * scale, 0.0),
				blocked, floorY);
	}

	/** Turns the view. Pitch stops short of straight up and down; yaw wraps around. */
	public void look(double deltaYaw, double deltaPitch) {
		yaw = MathHelper.wrapDegrees(yaw + deltaYaw);
		pitch = clampPitch(pitch + deltaPitch);
	}

	/** Moves along the whole look direction, so looking down and scrolling forward goes down too, and blocked the same way as walking. */
	public void dolly(double blocks, Predicate<Vec3d> blocked, double floorY) {
		sweep(forward().multiply(blocks), blocked, floorY);
	}

	/**
	 * Keeps the camera inside a sphere around {@code center}: a position outside it is pulled back onto its surface.
	 * Returns whether it was pulled back.
	 */
	public boolean clampTo(Vec3d center, double radius) {
		Vec3d offset = position.subtract(center);
		double distance = offset.length();
		if (distance <= radius) {
			return false;
		}
		position = center.add(offset.multiply(radius / distance));
		return true;
	}

	/** Puts the camera back at a position it had been at, for a move that the game's rules have to undo. */
	public void moveTo(Vec3d point) {
		position = point;
	}

	/** The unit direction the camera looks in. */
	public Vec3d forward() {
		double pitchRadians = Math.toRadians(pitch);
		double yawRadians = Math.toRadians(yaw);
		return new Vec3d(-Math.sin(yawRadians) * Math.cos(pitchRadians), -Math.sin(pitchRadians),
				Math.cos(yawRadians) * Math.cos(pitchRadians));
	}

	/** The unit direction to the screen's right. It stays level, whatever the pitch. */
	public Vec3d right() {
		double yawRadians = Math.toRadians(yaw);
		return new Vec3d(-Math.cos(yawRadians), 0.0, -Math.sin(yawRadians));
	}

	/**
	 * Applies a displacement in small steps, one axis at a time (x, then y, then z). A step whose end point is blocked is dropped,
	 * so the camera slides along a wall instead of stopping dead. Below {@code floorY} a step stops at the floor, and is dropped
	 * when the floor point itself is blocked.
	 */
	private void sweep(Vec3d displacement, Predicate<Vec3d> blocked, double floorY) {
		double longest = Math.max(Math.abs(displacement.x), Math.max(Math.abs(displacement.y), Math.abs(displacement.z)));
		if (!(longest > 0.0) || !Double.isFinite(longest)) {
			return;
		}
		int steps = (int) Math.ceil(longest / MAX_STEP);
		Vec3d each = displacement.multiply(1.0 / steps);
		for (int step = 0; step < steps; step++) {
			slide(each.x, 0.0, 0.0, blocked, floorY);
			slide(0.0, each.y, 0.0, blocked, floorY);
			slide(0.0, 0.0, each.z, blocked, floorY);
		}
	}

	private void slide(double dx, double dy, double dz, Predicate<Vec3d> blocked, double floorY) {
		Vec3d target = position.add(dx, dy, dz);
		if (target.y < floorY) {
			target = new Vec3d(target.x, floorY, target.z);
		}
		if (!blocked.test(target)) {
			position = target;
		}
	}

	private Vec3d horizontalForward() {
		double yawRadians = Math.toRadians(yaw);
		return new Vec3d(-Math.sin(yawRadians), 0.0, Math.cos(yawRadians));
	}

	private static double clampPitch(double degrees) {
		return Math.clamp(degrees, -MAX_PITCH, MAX_PITCH);
	}
}
