package com.davisr.thedreamtable.network.codec;

import java.util.function.Function;

/** Writes and reads one value on a buffer. */
public interface PacketCodec<B, V> {
	V decode(B buf);

	void encode(B buf, V value);

	default <R> R collect(Function<? super PacketCodec<B, V>, ? extends R> function) {
		return function.apply(this);
	}

	@FunctionalInterface
	interface ValueFirstEncoder<B, V> {
		void encode(V value, B buf);
	}

	@FunctionalInterface
	interface Decoder<B, V> {
		V decode(B buf);
	}

	static <B, V> PacketCodec<B, V> of(ValueFirstEncoder<B, V> encoder, Decoder<B, V> decoder) {
		return new PacketCodec<>() {
			@Override
			public V decode(B buf) {
				return decoder.decode(buf);
			}

			@Override
			public void encode(B buf, V value) {
				encoder.encode(value, buf);
			}
		};
	}

	static <B, C, T1> PacketCodec<B, C> tuple(PacketCodec<? super B, T1> c1, Function<C, T1> f1, Function<T1, C> to) {
		return of((value, buf) -> c1.encode(buf, f1.apply(value)), buf -> to.apply(c1.decode(buf)));
	}

	static <B, C, T1, T2> PacketCodec<B, C> tuple(PacketCodec<? super B, T1> c1, Function<C, T1> f1,
			PacketCodec<? super B, T2> c2, Function<C, T2> f2, Function2<T1, T2, C> to) {
		return of((value, buf) -> {
			c1.encode(buf, f1.apply(value));
			c2.encode(buf, f2.apply(value));
		}, buf -> {
			T1 v1 = c1.decode(buf);
			T2 v2 = c2.decode(buf);
			return to.apply(v1, v2);
		});
	}

	static <B, C, T1, T2, T3> PacketCodec<B, C> tuple(PacketCodec<? super B, T1> c1, Function<C, T1> f1,
			PacketCodec<? super B, T2> c2, Function<C, T2> f2, PacketCodec<? super B, T3> c3, Function<C, T3> f3,
			Function3<T1, T2, T3, C> to) {
		return of((value, buf) -> {
			c1.encode(buf, f1.apply(value));
			c2.encode(buf, f2.apply(value));
			c3.encode(buf, f3.apply(value));
		}, buf -> {
			T1 v1 = c1.decode(buf);
			T2 v2 = c2.decode(buf);
			T3 v3 = c3.decode(buf);
			return to.apply(v1, v2, v3);
		});
	}

	static <B, C, T1, T2, T3, T4> PacketCodec<B, C> tuple(PacketCodec<? super B, T1> c1, Function<C, T1> f1,
			PacketCodec<? super B, T2> c2, Function<C, T2> f2, PacketCodec<? super B, T3> c3, Function<C, T3> f3,
			PacketCodec<? super B, T4> c4, Function<C, T4> f4, Function4<T1, T2, T3, T4, C> to) {
		return of((value, buf) -> {
			c1.encode(buf, f1.apply(value));
			c2.encode(buf, f2.apply(value));
			c3.encode(buf, f3.apply(value));
			c4.encode(buf, f4.apply(value));
		}, buf -> {
			T1 v1 = c1.decode(buf);
			T2 v2 = c2.decode(buf);
			T3 v3 = c3.decode(buf);
			T4 v4 = c4.decode(buf);
			return to.apply(v1, v2, v3, v4);
		});
	}

	static <B, C, T1, T2, T3, T4, T5> PacketCodec<B, C> tuple(PacketCodec<? super B, T1> c1, Function<C, T1> f1,
			PacketCodec<? super B, T2> c2, Function<C, T2> f2, PacketCodec<? super B, T3> c3, Function<C, T3> f3,
			PacketCodec<? super B, T4> c4, Function<C, T4> f4, PacketCodec<? super B, T5> c5, Function<C, T5> f5,
			Function5<T1, T2, T3, T4, T5, C> to) {
		return of((value, buf) -> {
			c1.encode(buf, f1.apply(value));
			c2.encode(buf, f2.apply(value));
			c3.encode(buf, f3.apply(value));
			c4.encode(buf, f4.apply(value));
			c5.encode(buf, f5.apply(value));
		}, buf -> {
			T1 v1 = c1.decode(buf);
			T2 v2 = c2.decode(buf);
			T3 v3 = c3.decode(buf);
			T4 v4 = c4.decode(buf);
			T5 v5 = c5.decode(buf);
			return to.apply(v1, v2, v3, v4, v5);
		});
	}

	static <B, C, T1, T2, T3, T4, T5, T6> PacketCodec<B, C> tuple(PacketCodec<? super B, T1> c1, Function<C, T1> f1,
			PacketCodec<? super B, T2> c2, Function<C, T2> f2, PacketCodec<? super B, T3> c3, Function<C, T3> f3,
			PacketCodec<? super B, T4> c4, Function<C, T4> f4, PacketCodec<? super B, T5> c5, Function<C, T5> f5,
			PacketCodec<? super B, T6> c6, Function<C, T6> f6, Function6<T1, T2, T3, T4, T5, T6, C> to) {
		return of((value, buf) -> {
			c1.encode(buf, f1.apply(value));
			c2.encode(buf, f2.apply(value));
			c3.encode(buf, f3.apply(value));
			c4.encode(buf, f4.apply(value));
			c5.encode(buf, f5.apply(value));
			c6.encode(buf, f6.apply(value));
		}, buf -> {
			T1 v1 = c1.decode(buf);
			T2 v2 = c2.decode(buf);
			T3 v3 = c3.decode(buf);
			T4 v4 = c4.decode(buf);
			T5 v5 = c5.decode(buf);
			T6 v6 = c6.decode(buf);
			return to.apply(v1, v2, v3, v4, v5, v6);
		});
	}

	interface Function2<A, B, R> {
		R apply(A a, B b);
	}

	interface Function3<A, B, C, R> {
		R apply(A a, B b, C c);
	}

	interface Function4<A, B, C, D, R> {
		R apply(A a, B b, C c, D d);
	}

	interface Function5<A, B, C, D, E, R> {
		R apply(A a, B b, C c, D d, E e);
	}

	interface Function6<A, B, C, D, E, F, R> {
		R apply(A a, B b, C c, D d, E e, F f);
	}
}
