package com.davisr.thedreamtable.client.map;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * The client-only entity the game's camera follows in map mode. It is never added to the world. Its eye height is the player's
 * own, so the game's camera does not ease to a different height after the mode opens.
 */
final class MapCameraEntity extends ArmorStandEntity {
	private final float eyeHeight;

	MapCameraEntity(ClientWorld world, float eyeHeight) {
		super(EntityType.ARMOR_STAND, world);
		this.eyeHeight = eyeHeight;
	}

	@Override
	public EntityDimensions getBaseDimensions(EntityPose pose) {
		return super.getBaseDimensions(pose).withEyeHeight(eyeHeight);
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
		prevYaw = yaw;
		prevPitch = pitch;
	}
}
