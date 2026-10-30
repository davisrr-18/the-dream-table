package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.TheDreamTableMod;

import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Reads the schematic folders on one worker thread, so a table opening or a button click never waits for files to be
 * parsed. At most one read runs at a time; asking while one runs is refused, and the result is handed to the executor of
 * the caller's choice (the server thread), which swaps it in whole.
 */
final class ImportRefresher {
	private final Function<Path, ImportedStructures.Imports> loader;
	private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), task -> {
		Thread thread = new Thread(task, "The Dream Table schematics");
		thread.setDaemon(true);
		return thread;
	});
	private final AtomicBoolean running = new AtomicBoolean();

	ImportRefresher(Function<Path, ImportedStructures.Imports> loader) {
		this.loader = loader;
	}

	/**
	 * Starts reading {@code root} unless a read is already running. {@code publish} gets the result on {@code publishOn};
	 * {@code failed} gets the reason when the read threw, so the previous list stays in use.
	 *
	 * @return false when a read was already running
	 */
	boolean request(Path root, Executor publishOn, Consumer<ImportedStructures.Imports> publish, Consumer<Exception> failed) {
		if (!running.compareAndSet(false, true)) {
			return false;
		}
		try {
			worker.execute(() -> {
				try {
					ImportedStructures.Imports result = loader.apply(root);
					publishOn.execute(() -> publish.accept(result));
				} catch (Exception failure) {
					TheDreamTableMod.LOGGER.error("Reading the schematic folders failed; the previous list stays in use", failure);
					publishOn.execute(() -> failed.accept(failure));
				} finally {
					running.set(false);
				}
			});
		} catch (RejectedExecutionException busy) {
			running.set(false);
			return false;
		}
		return true;
	}
}
