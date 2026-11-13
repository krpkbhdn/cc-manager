package com.github.krpkbhdn.ccmanager.claude.hook;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** One thread and a bounded queue: a stalled consumer makes the newest work fail with RejectedExecutionException instead of growing memory. */
final class OrderedWorker {

    private static final Duration CRITICAL_ENQUEUE_WAIT = Duration.ofSeconds(5);
    private static final Duration CRITICAL_ENQUEUE_POLL = Duration.ofMillis(10);
    private static final Duration EXECUTOR_STOP_WAIT = Duration.ofSeconds(2);

    private final ThreadPoolExecutor executor;
    private final ReentrantLock enqueueOrder = new ReentrantLock(true);

    OrderedWorker(String name, int maxQueued) {
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(maxQueued),
                Thread.ofPlatform().name(name).daemon().factory());
    }

    void execute(Runnable task) {
        enqueueOrder.lock();
        try {
            executor.execute(task);
        } finally {
            enqueueOrder.unlock();
        }
    }

    /**
     * Queues the task, waiting for room when the queue is full. The fair lock is held while waiting, so every later task
     * (of any session) queues behind this one and order is kept.
     * @return false when it could not be queued (stopped or no room within the wait)
     */
    boolean executeWaitingForRoom(Runnable task) {
        enqueueOrder.lock();
        try {
            executor.execute(task);
            return true;
        } catch (RejectedExecutionException full) {
            return waitForRoom(task);
        } finally {
            enqueueOrder.unlock();
        }
    }

    private boolean waitForRoom(Runnable task) {
        var deadline = System.nanoTime() + CRITICAL_ENQUEUE_WAIT.toNanos();
        try {
            while (!executor.isShutdown() && System.nanoTime() < deadline) {
                try {
                    executor.execute(task);
                    return true;
                } catch (RejectedExecutionException full) {
                    Thread.sleep(CRITICAL_ENQUEUE_POLL.toMillis());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return false;
    }

    void awaitTermination() {
        awaitTermination(executor);
    }

    static void awaitTermination(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(EXECUTOR_STOP_WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
