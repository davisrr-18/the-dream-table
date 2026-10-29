package com.davisr.thedreamtable.client;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoticeShapeTest {
	private static Set<Long> pixels(List<NoticeShape.Rect> rects) {
		Set<Long> set = new HashSet<>();
		for (NoticeShape.Rect rect : rects) {
			for (int px = rect.x1(); px < rect.x2(); px++) {
				for (int py = rect.y1(); py < rect.y2(); py++) {
					assertTrue(set.add(pack(px, py)), "pixel drawn twice at " + px + "," + py);
				}
			}
		}
		return set;
	}

	@Test
	void fillAndOutlineTogetherCoverTheRectangleWithoutTheCornerPixelsAndNeverOverlap() {
		for (int[] size : new int[][] {{12, 20}, {40, 20}, {300, 42}, {7, 6}}) {
			int x = 17;
			int y = 6;
			List<NoticeShape.Rect> all = new ArrayList<>(NoticeShape.fill(x, y, size[0], size[1]));
			all.addAll(NoticeShape.outline(x, y, size[0], size[1]));
			Set<Long> covered = pixels(all);
			// Three pixels are cut from each corner.
			assertEquals(size[0] * size[1] - 12, covered.size(), "area of " + size[0] + "x" + size[1]);
			int[][] cut = {{0, 0}, {1, 0}, {0, 1}};
			for (int[] c : cut) {
				assertFalse(covered.contains(pack(x + c[0], y + c[1])));
				assertFalse(covered.contains(pack(x + size[0] - 1 - c[0], y + c[1])));
				assertFalse(covered.contains(pack(x + c[0], y + size[1] - 1 - c[1])));
				assertFalse(covered.contains(pack(x + size[0] - 1 - c[0], y + size[1] - 1 - c[1])));
			}
			for (long p : covered) {
				int px = (int) (p >> 32);
				int py = (int) p;
				assertTrue(px >= x && px < x + size[0] && py >= y && py < y + size[1]);
			}
		}
	}

	@Test
	void theOutlineSitsOnTheEdgeAndTheFillStaysInside() {
		Set<Long> fill = pixels(NoticeShape.fill(0, 0, 30, 20));
		assertFalse(fill.contains(pack(0, 10)));
		assertFalse(fill.contains(pack(29, 10)));
		assertFalse(fill.contains(pack(15, 0)));
		assertFalse(fill.contains(pack(15, 19)));
		assertTrue(fill.contains(pack(1, 10)));
		assertTrue(fill.contains(pack(2, 1)));
		Set<Long> outline = pixels(NoticeShape.outline(0, 0, 30, 20));
		assertTrue(outline.contains(pack(0, 10)));
		assertTrue(outline.contains(pack(1, 1)));
		assertTrue(outline.contains(pack(28, 18)));
	}

	@Test
	void tooSmallPanelsAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> NoticeShape.fill(0, 0, 5, 20));
		assertThrows(IllegalArgumentException.class, () -> NoticeShape.outline(0, 0, 20, 5));
	}

	@Test
	void theBackgroundIsClearlyTranslucentAndTheOutlineSofterThanOpaque() {
		int fill = NoticeShape.withAlpha(0x101820, 255, NoticeShape.FILL_OPACITY);
		int alpha = fill >>> 24;
		assertTrue(alpha >= 0.5F * 255 && alpha <= 0.65F * 255, "fill alpha " + alpha);
		assertEquals(0x101820, fill & 0xFFFFFF);
		assertTrue((NoticeShape.withAlpha(0x55FF55, 255, NoticeShape.EDGE_OPACITY) >>> 24) < 255);
		assertEquals(0, NoticeShape.withAlpha(0x101820, 0, NoticeShape.FILL_OPACITY) >>> 24);
		assertTrue((NoticeShape.withAlpha(0x101820, 128, NoticeShape.FILL_OPACITY) >>> 24) < alpha);
	}

	private static long pack(int x, int y) {
		return ((long) x << 32) | (y & 0xFFFFFFFFL);
	}
}
