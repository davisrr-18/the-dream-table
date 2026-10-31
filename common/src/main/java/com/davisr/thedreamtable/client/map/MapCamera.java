package com.davisr.thedreamtable.client.map;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * The free camera of map mode: a position and a look direction in Minecraft's convention, where yaw 0 faces south (+Z) and
 * a positive pitch looks down. Pure math, so it can be tested without a client.
 */
public final class MapCamera {
	/** Short of straight down, where the view's up direction would be undefined. */
	private static final double MAX_PITCH = 89.0;

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
	public void move(double forward, double strafe, double vertical, double speed) {
		double length = Math.sqrt(forward * forward + strafe * strafe + vertical * vertical);
		if (length == 0.0) {
			return;
		}
		double scale = speed / length;
		position = position.add(horizontalForward().multiply(forward * scale))
				.add(right().multiply(strafe * scale))
				.add(0.0, vertical * scale, 0.0);
	}

	/** Turns the view. Pitch stops short of straight up and down; yaw wraps around. */
	public void look(double deltaYaw, double deltaPitch) {
		yaw = MathHelper.wrapDegrees(yaw + deltaYaw);
		pitch = clampPitch(pitch + deltaPitch);
	}

	/** Moves along the whole look direction, so looking down and scrolling forward goes down too. */
	public void dolly(double blocks) {
		position = position.add(forward().multiply(blocks));
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

	/** The unit direction to the top of the screen, perpendicular to {@link #forward()} and {@link #right()}. */
	public Vec3d up() {
		double pitchRadians = Math.toRadians(pitch);
		double yawRadians = Math.toRadians(yaw);
		return new Vec3d(-Math.sin(yawRadians) * Math.sin(pitchRadians), Math.cos(pitchRadians),
				Math.cos(yawRadians) * Math.sin(pitchRadians));
	}

	/**
	 * The unit world direction of the ray through a screen point. {@code ndcX} runs -1 (left) to 1 (right) and {@code ndcY} -1
	 * (bottom) to 1 (top); {@code tanHalfFovY} is the tangent of half the vertical field of view.
	 */
	public Vec3d rayThrough(double ndcX, double ndcY, double tanHalfFovY, double aspect) {
		return forward()
				.add(right().multiply(ndcX * tanHalfFovY * aspect))
				.add(up().multiply(ndcY * tanHalfFovY))
				.normalize();
	}

	private Vec3d horizontalForward() {
		double yawRadians = Math.toRadians(yaw);
		return new Vec3d(-Math.sin(yawRadians), 0.0, Math.cos(yawRadians));
	}

	private static double clampPitch(double degrees) {
		return Math.clamp(degrees, -MAX_PITCH, MAX_PITCH);
	}
}
