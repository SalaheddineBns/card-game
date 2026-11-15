package com.cardgame.parity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

/**
 * Deterministic {@link TaskScheduler}: tasks only run when a test calls {@link #runDueTasks()}, using the
 * shared {@link MutableClock}. Only {@code schedule(Runnable, Instant)} is supported (service contract).
 */
final class FakeTaskScheduler implements TaskScheduler {

    final class Task implements ScheduledFuture<Object> {
        final Instant at;
        final Runnable runnable;
        boolean cancelled;
        boolean done;

        Task(Instant at, Runnable runnable) {
            this.at = at;
            this.runnable = runnable;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (done) {
                return false;
            }
            cancelled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return done || cancelled;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(Duration.between(clock.instant(), at));
        }

        @Override
        public int compareTo(Delayed o) {
            return Long.compare(getDelay(TimeUnit.NANOSECONDS), o.getDelay(TimeUnit.NANOSECONDS));
        }
    }

    private final MutableClock clock;
    private final List<Task> tasks = new ArrayList<>();

    FakeTaskScheduler(MutableClock clock) {
        this.clock = clock;
    }

    @Override
    public Clock getClock() {
        return clock;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
        Task t = new Task(startTime, task);
        tasks.add(t);
        return t;
    }

    /** Runs every non-cancelled task due at the current clock time, earliest first (tasks may schedule more). */
    void runDueTasks() {
        while (true) {
            Task next = tasks.stream()
                    .filter(t -> !t.isDone() && !t.at.isAfter(clock.instant()))
                    .min(Comparator.comparing(t -> t.at))
                    .orElse(null);
            if (next == null) {
                return;
            }
            next.done = true;
            next.runnable.run();
        }
    }

    /** Moves the clock and runs what became due. */
    void advanceAndRun(Duration duration) {
        clock.advance(duration);
        runDueTasks();
    }

    /** Scheduled, not run, not cancelled. */
    List<Instant> pendingTaskTimes() {
        return tasks.stream().filter(t -> !t.isDone()).map(t -> t.at).sorted().toList();
    }

    long cancelledCount() {
        return tasks.stream().filter(t -> t.cancelled).count();
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
        throw new UnsupportedOperationException("contract: use schedule(Runnable, Instant)");
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
        throw new UnsupportedOperationException("contract: use schedule(Runnable, Instant)");
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
        throw new UnsupportedOperationException("contract: use schedule(Runnable, Instant)");
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
        throw new UnsupportedOperationException("contract: use schedule(Runnable, Instant)");
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
        throw new UnsupportedOperationException("contract: use schedule(Runnable, Instant)");
    }
}
