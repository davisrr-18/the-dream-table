package com.davisr.thedreamtable.screen.ui.layout;

public record UiRect(int x, int y, int width, int height) {
	public UiRect {
		if (width < 0 || height < 0) {
			throw new IllegalArgumentException("Rectangle dimensions cannot be negative");
		}
	}

	public int right() {
		return x + width;
	}

	public int bottom() {
		return y + height;
	}

	public boolean contains(double pointX, double pointY) {
		return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
	}

	public boolean contains(UiRect rectangle) {
		return rectangle.x >= x && rectangle.y >= y
				&& rectangle.right() <= right() && rectangle.bottom() <= bottom();
	}
}
