package com.davisr.thedreamtable.farm;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Schematic files are parsed on a worker and the result is published on the caller's executor, never on the calling thread. */
class ImportRefresherTest {
	private static final Path ROOT = Path.of("world");

	/** Stands in for the server thread: runs what the worker queues, but only when the test drains it. */
	private static final class Queue implements Executor {
		private final java.util.concurrent.LinkedBlockingQueue<Runnable> tasks = new java.util.concurrent.LinkedBlockingQueue<>();

		@Override
		public void execute(Runnable task) {
			tasks.add(task);
		}

		void runNext() throws InterruptedException {
			Runnable task = tasks.poll(5, TimeUnit.SECONDS);
			assertNotNull(task, "the worker never published");
			task.run();
		}
	}

	@Test
	void parsingHappensOffTheCallingThreadAndPublishesOnTheGivenExecutor() throws InterruptedException {
		AtomicReference<Thread> parsedOn = new AtomicReference<>();
		ImportRefresher refresher = new ImportRefresher(root -> {
			parsedOn.set(Thread.currentThread());
			return ImportedStructures.Imports.EMPTY;
		});
		Queue server = new Queue();
		AtomicReference<ImportedStructures.Imports> published = new AtomicReference<>();
		AtomicReference<Thread> publishedOn = new AtomicReference<>();

		assertTrue(refresher.request(ROOT, server, imports -> {
			published.set(imports);
			publishedOn.set(Thread.currentThread());
		}, failure -> {
		}));
		assertEquals(null, published.get(), "nothing is published until the server thread runs it");
		server.runNext();

		assertEquals(ImportedStructures.Imports.EMPTY, published.get());
		assertNotEquals(Thread.currentThread(), parsedOn.get());
		assertEquals(Thread.currentThread(), publishedOn.get());
	}

	@Test
	void aSecondRequestIsRefusedWhileOneRuns() throws InterruptedException {
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ImportRefresher refresher = new ImportRefresher(root -> {
			started.countDown();
			try {
				release.await(5, TimeUnit.SECONDS);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
			}
			return ImportedStructures.Imports.EMPTY;
		});
		Queue server = new Queue();

		assertTrue(refresher.request(ROOT, server, imports -> {
		}, failure -> {
		}));
		assertTrue(started.await(5, TimeUnit.SECONDS));
		assertFalse(refresher.request(ROOT, server, imports -> {
		}, failure -> {
		}));
		release.countDown();
		server.runNext();
		assertAcceptedSoon(refresher, server);
	}

	private static void assertAcceptedSoon(ImportRefresher refresher, Queue server) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (!refresher.request(ROOT, server, imports -> {
		}, failure -> {
		})) {
			assertTrue(System.nanoTime() < deadline, "the worker never became free again");
			Thread.sleep(5);
		}
		server.runNext();
	}

	@Test
	void aFailedReadIsReportedAndTheNextRequestStillRuns() throws InterruptedException {
		ImportRefresher refresher = new ImportRefresher(root -> {
			throw new IllegalStateException("unreadable folder");
		});
		Queue server = new Queue();
		AtomicReference<Exception> failure = new AtomicReference<>();
		AtomicReference<ImportedStructures.Imports> published = new AtomicReference<>();

		assertTrue(refresher.request(ROOT, server, published::set, failure::set));
		server.runNext();

		assertEquals("unreadable folder", failure.get().getMessage());
		assertEquals(null, published.get());
		assertAcceptedSoonAfterFailure(refresher, server);
	}

	private static void assertAcceptedSoonAfterFailure(ImportRefresher refresher, Queue server) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (!refresher.request(ROOT, server, imports -> {
		}, failure -> {
		})) {
			assertTrue(System.nanoTime() < deadline, "the worker stayed busy after a failure");
			Thread.sleep(5);
		}
		server.runNext();
	}
}
