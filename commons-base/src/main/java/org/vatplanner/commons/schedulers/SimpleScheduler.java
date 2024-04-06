package org.vatplanner.commons.schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An easy-to-use scheduler that checks for trigger events at fixed intervals and reschedules tasks depending on whether
 * execution has succeeded or failed.
 */
public class SimpleScheduler extends Thread {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleScheduler.class);

    private final String name;
    private final AtomicLong checkIntervalMillis = new AtomicLong(Duration.ofSeconds(30).toMillis());
    private final AtomicReference<Duration> repeatInterval = new AtomicReference<>(null);
    private final AtomicReference<Duration> retryInterval = new AtomicReference<>(null);

    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicReference<Instant> nextTrigger = new AtomicReference<>(Instant.MAX);

    private final AtomicReference<Runnable> triggerAction = new AtomicReference<>();
    private final AtomicReference<Consumer<Exception>> exceptionHandler = new AtomicReference<>();

    private final Object checkTrigger = new Object();

    /**
     * Creates a scheduler identifying in logs by the given name.
     *
     * @param name name to identify instance with
     */
    public SimpleScheduler(String name) {
        this.name = name;
        setName(name);
    }

    @Override
    public void run() {
        LOGGER.debug("Scheduler thread for spawned for \"{}\"", name);

        while (!shutdown.get()) {
            LOGGER.trace("Scheduler for \"{}\" woke up", name);

            if (!enabled.get()) {
                LOGGER.debug("Scheduler for \"{}\" is disabled, not triggering", name);
            } else if (Instant.now().isAfter(nextTrigger.get())) {
                LOGGER.debug("Triggering \"{}\"", name);

                nextTrigger.set(Instant.MAX);
                Duration delay = null;

                Runnable action = triggerAction.get();
                if (action == null) {
                    LOGGER.warn("No action set for scheduler \"{}\"", name);
                } else {
                    try {
                        action.run();
                    } catch (Exception ex) {
                        LOGGER.warn("Trigger for \"{}\" failed", name, ex);
                        Consumer<Exception> handler = exceptionHandler.get();
                        if (handler != null) {
                            try {
                                handler.accept(ex);
                            } catch (Exception ex2) {
                                LOGGER.warn("Exception handler for \"{}\" failed", name, ex2);
                            }
                        }

                        delay = retryInterval.get();
                    }
                }

                delay = min(delay, repeatInterval.get()).orElse(null);
                if (delay != null) {
                    setNextTriggerIfEarlier(delay);
                }
            }

            try {
                synchronized (checkTrigger) {
                    if (!shutdown.get()) {
                        checkTrigger.wait(checkIntervalMillis.get());
                    }
                }
            } catch (InterruptedException ex) {
                LOGGER.warn("thread got interrupted, exiting", ex);
                shutdown.set(true);
                return;
            }
        }
    }

    /**
     * Sets the interval at which the scheduler wakes up to check for due tasks.
     *
     * @param checkInterval new interval at which to check for tasks
     * @return same instance for method-chaining
     */
    public SimpleScheduler setCheckIntervalMillis(Duration checkInterval) {
        LOGGER.debug("Setting \"{}\" check interval to {}", name, checkInterval);
        checkIntervalMillis.set(checkInterval.toMillis());
        return this;
    }

    /**
     * Sets the next trigger time, overriding the previously determined/set time.
     *
     * @param nextTrigger time to trigger at
     * @return same instance for method-chaining
     */
    public SimpleScheduler setNextTrigger(Instant nextTrigger) {
        LOGGER.debug("Setting \"{}\" trigger time to {}", name, nextTrigger);
        this.nextTrigger.set(nextTrigger);
        return this;
    }

    /**
     * Sets the regular interval at which the task should be retriggered.
     *
     * @param repeatInterval interval at which to retrigger if previous execution succeeded
     * @return same instance for method-chaining
     */
    public SimpleScheduler setRepeatInterval(Duration repeatInterval) {
        LOGGER.debug("Setting \"{}\" repeat interval to {}", name, repeatInterval);
        this.repeatInterval.set(repeatInterval);
        return this;
    }

    /**
     * Disables regular repetition after successful execution.
     *
     * @return same instance for method-chaining
     */
    public SimpleScheduler disableRepetition() {
        LOGGER.debug("Disabling \"{}\" repetition", name);
        this.repeatInterval.set(null);
        return this;
    }

    /**
     * Sets the interval at which the task should be reattempted after execution failed.
     *
     * @param retryInterval interval at which to retry execution
     * @return same instance for method-chaining
     */
    public SimpleScheduler setRetryInterval(Duration retryInterval) {
        LOGGER.debug("Setting \"{}\" retry interval to {}", name, retryInterval);
        this.retryInterval.set(retryInterval);
        return this;
    }

    /**
     * Disables reattempts after failed execution.
     *
     * @return same instance for method-chaining
     */
    public SimpleScheduler disableRetries() {
        LOGGER.debug("Disabling \"{}\" retry interval", name);
        this.retryInterval.set(null);
        return this;
    }

    /**
     * Sets the action to be triggered.
     *
     * @param action to be triggered
     * @return same instance for method-chaining
     */
    public SimpleScheduler onTrigger(Runnable action) {
        this.triggerAction.set(action);
        return this;
    }

    /**
     * Sets a callback to receive exceptions thrown by failed executions.
     *
     * @param exceptionHandler receives exceptions thrown by failed executions
     * @return same instance for method-chaining
     */
    public SimpleScheduler onException(Consumer<Exception> exceptionHandler) {
        this.exceptionHandler.set(exceptionHandler);
        return this;
    }

    /**
     * Schedules next trigger for the given or existing time, whichever would be earlier.
     *
     * @param nextTrigger next time to trigger (if earlier than already set trigger)
     * @return {@code true} if the given timestamp will be the next trigger time, {@code false} if there already is an earlier trigger pending
     */
    public boolean setNextTriggerIfEarlier(Instant nextTrigger) {
        LOGGER.debug("Setting \"{}\" trigger time to {} if earlier (conditional)", name, nextTrigger);

        Instant effectiveTrigger = this.nextTrigger.updateAndGet(oldTrigger -> nextTrigger.isBefore(oldTrigger) ? nextTrigger : oldTrigger);
        LOGGER.debug("Trigger time for \"{}\" is {}", name, effectiveTrigger);

        return effectiveTrigger.equals(nextTrigger);
    }

    /**
     * Schedules next trigger after given interval from now or existing time, whichever would be earlier.
     *
     * @param delay delay until next time to trigger (if earlier than already set trigger)
     * @return {@code true} if the given delay will be used for next trigger time, {@code false} if there already is an earlier trigger pending
     */
    public boolean setNextTriggerIfEarlier(Duration delay) {
        return setNextTriggerIfEarlier(Instant.now().plus(delay));
    }

    /**
     * Marks the scheduler thread for shutdown and attempts to join it within the specified timeout.
     *
     * @param timeout maximum time to block waiting for the thread to be joined
     * @return {@code true} if the thread was joined within timeout, {@code false} if not
     */
    public boolean shutdownAndJoin(Duration timeout) {
        LOGGER.debug("Shutting down \"{}\"", name);
        shutdown.set(true);

        synchronized (checkTrigger) {
            checkTrigger.notifyAll();
        }

        if (isAlive()) {
            try {
                join(timeout.toMillis());
            } catch (InterruptedException ex) {
                LOGGER.warn("join operation got interrupted", ex);
                throw new ShutdownFailed("join operation got interrupted", ex);
            }
        }

        return !isAlive();
    }

    /**
     * Marks the scheduler as enabled or disabled.
     *
     * @param enabled {@code true} enables the scheduler; {@code false} disables it
     * @return same instance for method-chaining
     */
    public SimpleScheduler setEnabled(boolean enabled) {
        this.enabled.set(enabled);

        LOGGER.info("Scheduler \"{}\" is now {}", name, enabled ? "enabled" : "disabled");

        return this;
    }

    private Optional<Duration> min(Duration... durations) {
        return Arrays.stream(durations)
                     .filter(Objects::nonNull)
                     .min(Duration::compareTo);
    }

    private static class ShutdownFailed extends RuntimeException {
        ShutdownFailed(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}
