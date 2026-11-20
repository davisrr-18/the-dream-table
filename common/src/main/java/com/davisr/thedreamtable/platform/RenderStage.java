package com.davisr.thedreamtable.platform;

/** The points of a world render a listener can draw at. */
public enum RenderStage {
	/** After entities and block entities are drawn, before translucent terrain (Fabric {@code AFTER_ENTITIES}). */
	AFTER_ENTITIES,
	/**
	 * The end of the main pass: after entities and translucent terrain, before particles, clouds, weather, the hand and the GUI
	 * (Fabric {@code END_MAIN}). 1.21.11 has no "after translucent" event; this is the nearest. Listeners draw straight into the
	 * main framebuffer with the model-view and projection the world render has set, so a loader must call it with the camera
	 * rotation already in {@code RenderSystem.getModelViewMatrix()} and a fresh, camera-relative matrix stack.
	 */
	END_MAIN
}
