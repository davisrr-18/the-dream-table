package com.davisr.thedreamtable.client.map;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * The client-only entity the game's camera follows in map mode. It is never added to the world. It is a plain entity on
 * purpose: the camera reads {@code getYaw(tickDelta)}, which a living entity answers with its head yaw instead of its yaw,
 * so a living camera never turns. Its eye height is the player's own, so the camera does not ease to another height.
 */
final class MapCameraEntity extends Entity {
	private final float eyeHeight;

	MapCameraEntity(ClientWorld world, float eyeHeight) {
		super(EntityType.MARKER, world);
		this.eyeHeight = eyeHeight;
		// The constructor measured the entity before the eye height was set.
		calculateDimensions();
	}

	@Override
	public EntityDimensions getDimensions(EntityPose pose) {
		return EntityDimensions.fixed(0.0F, 0.0F).withEyeHeight(eyeHeight);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
	}

	/** The camera is client-only and never in a server world, so nothing damages it. */
	@Override
	public boolean damage(ServerWorld world, DamageSource source, float amount) {
		return false;
	}

	@Override
	protected void readCustomData(ReadView view) {
	}

	@Override
	protected void writeCustomData(WriteView view) {
	}

	/**
	 * Gives the camera the view's pose with the previous pose equal to it, so the game's camera reads this pose at any tick
	 * fraction and the view moves with each frame. The yaw is unwrapped to the side of the previous yaw, so turning across
	 * south does not spin the camera the long way round.
	 */
	void place(MapCamera view) {
		float previousYaw = getYaw();
		float yaw = (float) (previousYaw + MathHelper.wrapDegrees(view.yaw() - previousYaw));
		float pitch = (float) view.pitch();
		Vec3d eye = view.position();
		refreshPositionAndAngles(eye.x, eye.y - getStandingEyeHeight(), eye.z, yaw, pitch);
		lastYaw = yaw;
		lastPitch = pitch;
	}
}
