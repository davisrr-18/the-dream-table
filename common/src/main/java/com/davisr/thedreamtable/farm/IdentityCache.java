package com.davisr.thedreamtable.farm;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Remembers a value computed from an object, per object instance. Records such as {@link FarmTemplate} hash all of their
 * contents, so a plain {@code WeakHashMap} would rescan a large farm on every lookup; here the key is compared and hashed
 * by identity, and an entry goes away once its key is garbage collected. A value must not refer back to its key.
 * Safe to call from several threads; a value is computed once, under the lock.
 */
final class IdentityCache<K, V> {
	private final Map<Ref<K>, V> entries = new HashMap<>();
	private final ReferenceQueue<K> collected = new ReferenceQueue<>();

	synchronized V get(K key, Function<K, V> compute) {
		purge();
		V cached = entries.get(new Ref<>(key, null));
		if (cached != null) {
			return cached;
		}
		V computed = compute.apply(key);
		entries.put(new Ref<>(key, collected), computed);
		return computed;
	}

	/** How many keys are remembered, for tests. */
	synchronized int size() {
		purge();
		return entries.size();
	}

	private void purge() {
		for (Object gone = collected.poll(); gone != null; gone = collected.poll()) {
			entries.remove(gone);
		}
	}

	/** A weak reference that is equal to another only when both point at the same live object. */
	private static final class Ref<K> extends WeakReference<K> {
		private final int hash;

		Ref(K key, ReferenceQueue<K> queue) {
			super(key, queue);
			hash = System.identityHashCode(key);
		}

		@Override
		public int hashCode() {
			return hash;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof Ref<?> ref)) {
				return false;
			}
			K mine = get();
			return mine != null && mine == ref.get();
		}
	}
}
