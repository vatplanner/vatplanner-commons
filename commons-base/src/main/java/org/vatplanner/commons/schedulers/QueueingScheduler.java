package org.vatplanner.commons.schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vatplanner.commons.utils.ThrowingSupplier;

/**
 * A scheduler that runs multiple tasks in series. Tasks can be scheduled to repeat at an interval from last
 * termination. Other than default Java implementations, tasks can also be requested to be triggered "now". Any longer
 * pending entries will still be executed, however. Although separate threads are used, only at most one task is
 * being executed at a time.
 * <p>
 * {@link Task} needs to be extended by all tasks to be queued. All tasks are expected to take care of necessary
 * timeouts themselves and should check for requested cancellation at reasonable intervals through
 * {@link Task#isCancelled()}. Follow-up tasks can be scheduled through the reference returned by
 * {@link Task#getScheduler()}.
 * </p>
 * <p>
 * Basic tasks can be (re)scheduled and triggered by just referring to the {@link Task} class, which will be translated
 * to the implementation's class name and default constructor. In case that is not sufficient, tasks can alternatively
 * be specified by a specific name and instance supplier.
 * </p>
 * <p>
 * The scheduler needs to be explicitly {@link #start()}ed, which can be used to defer execution until the schedule
 * has been defined. {@link #shutdownAsync()} or {@link #shutdownAndWait(Duration)} should be called to stop processing,
 * e.g. when the surrounding application shuts down or gets restarted. Once shut down, the scheduler cannot be reused.
 * </p>
 */
public class QueueingScheduler implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueueingScheduler.class);

    private final Map<String, ThrowingSupplier<? extends Task, ?>> suppliers = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, Instant> schedule = new HashMap<>();
    private final Map<String, Duration> repeatIntervals = Collections.synchronizedMap(new HashMap<>());
    private final AtomicReference<TaskExecution> runningTask = new AtomicReference<>();

    private final AtomicReference<Thread> schedulerThread = new AtomicReference<>();
    private final AtomicBoolean shouldShutdown = new AtomicBoolean();

    private final AtomicReference<Duration> failedRetryInterval = new AtomicReference<>(Duration.ofMinutes(5));
    private final AtomicReference<Duration> idleCheckInterval = new AtomicReference<>(Duration.ofSeconds(30));

    /**
     * Controls the {@link Thread}ed execution of a {@link Task}.
     */
    private static class TaskExecution implements Runnable {
        private final Thread thread;
        private final String taskName;
        private final Task task;
        private final Object notificationObject;
        private final AtomicReference<Exception> exception = new AtomicReference<>();
        private final AtomicBoolean done = new AtomicBoolean();

        TaskExecution(String taskName, Task task, Object notificationObject) {
            this.notificationObject = notificationObject;
            this.taskName = taskName;
            this.task = task;
            this.thread = new Thread(this);
            thread.setName("Task " + task.getClass().getSimpleName());
            thread.start();
        }

        String getTaskName() {
            return taskName;
        }

        void cancelAsync() {
            task.cancel();
        }

        boolean cancelAndWait(Duration timeout) throws InterruptedException {
            cancelAsync();
            thread.join(timeout.toMillis());
            return !thread.isAlive();
        }

        boolean isDone() {
            return done.get();
        }

        @Override
        public void run() {
            try {
                task.run();
            } catch (Exception ex) {
                LOGGER.warn("task {} failed", taskName, ex);
                exception.set(ex);
            }

            done.set(true);

            synchronized (notificationObject) {
                notificationObject.notifyAll();
            }
        }
    }

    /**
     * A task to be run by a {@link QueueingScheduler}. {@link #run()} will be executed in a separate thread.
     * <p>
     * A new instance is created for each planned execution. Implementations must expect to only run at most once and
     * may in fact not be run at all.
     * </p>
     * <p>
     * Longer running operations should ensure a reasonable timeout and check {@link #isCancelled()} at reasonable
     * intervals.
     * </p>
     */
    public abstract static class Task implements Runnable {
        private final AtomicBoolean isCancelled = new AtomicBoolean();
        private final AtomicReference<QueueingScheduler> scheduler = new AtomicReference<>();

        private void setScheduler(QueueingScheduler scheduler) {
            this.scheduler.set(scheduler);
        }

        /**
         * Returns the {@link QueueingScheduler}. This is useful to e.g. trigger follow-up tasks.
         * <p>
         * Must only be called during execution ({@link #run()}), not at construction time.
         * </p>
         *
         * @return the scheduler managing this task
         */
        public QueueingScheduler getScheduler() {
            QueueingScheduler res = scheduler.get();
            if (res == null) {
                throw new OutOfSequence("scheduler has not finished task instantiation yet");
            }
            return res;
        }

        /**
         * Indicates whether the task has been requested to be cancelled.
         *
         * @return {@code true} if the task should be cancelled, {@code false} if it should continue
         */
        protected boolean isCancelled() {
            return isCancelled.get();
        }

        private void cancel() {
            isCancelled.set(true);
        }
    }

    @Override
    public void run() {
        LOGGER.info("scheduler started");

        while (!shouldShutdown.get()) {
            boolean canStartNextTask = false;

            TaskExecution previousTask = runningTask.get();
            if (previousTask == null) {
                canStartNextTask = true;
            } else if (previousTask.isDone()) {
                String taskName = previousTask.getTaskName();
                Duration interval = repeatIntervals.get(taskName);

                boolean failed = (previousTask.exception.get() != null);
                if (failed) {
                    LOGGER.warn("task {} has failed, applying retry interval", taskName);
                    interval = failedRetryInterval.get();
                }

                if (interval == null) {
                    LOGGER.info("task {} finished, not rescheduling (no interval configured)", taskName);
                } else {
                    Instant nextRun = rescheduleIfEarlier(taskName, Instant.now().plus(interval));
                    LOGGER.info("task {} finished, next execution scheduled for {}", taskName, nextRun);
                }

                runningTask.set(null);
                canStartNextTask = true;
            }

            synchronized (schedule) {
                Instant sleepUntil = Instant.now().plus(idleCheckInterval.get());

                if (canStartNextTask) {
                    Map.Entry<String, Instant> nextTask = schedule.entrySet()
                                                                  .stream()
                                                                  .min(Map.Entry.comparingByValue())
                                                                  .orElse(null);

                    LOGGER.debug("next task: {}", nextTask);

                    if (nextTask == null) {
                        LOGGER.info("no tasks scheduled");
                    } else {
                        Duration timeUntilStart = Duration.between(Instant.now(), nextTask.getValue());
                        boolean isDue = (timeUntilStart.compareTo(Duration.ZERO) <= 0);
                        if (!isDue) {
                            LOGGER.debug("next task {} is not due yet, time until start: {}", nextTask, timeUntilStart);
                            sleepUntil = nextTask.getValue();
                        } else {
                            LOGGER.debug("next task {} is due (time until start: {})", nextTask, timeUntilStart);

                            String taskName = nextTask.getKey();
                            Task task = instantiateTask(taskName);
                            if (task == null) {
                                Instant retryTime = Instant.now().plus(failedRetryInterval.get());
                                LOGGER.warn("postponing {} until {} due to failed construction", taskName, retryTime);
                                schedule.put(taskName, retryTime);
                                continue; // check next entry
                            }

                            LOGGER.info("starting task {}", taskName);
                            schedule.remove(taskName); // expired; will be re-added via rescheduleIfEarlier when finished
                            runningTask.set(new TaskExecution(taskName, task, schedule));
                        }
                    }
                }

                Duration sleepDuration = Duration.between(Instant.now(), sleepUntil);
                if (sleepDuration.compareTo(Duration.ZERO) > 0) {
                    try {
                        LOGGER.debug("sleeping for {}", sleepDuration);
                        schedule.wait(Math.max(1, sleepDuration.toMillis()));
                    } catch (InterruptedException ex) {
                        LOGGER.warn("scheduler got interrupted, shutting down", ex);
                        break;
                    }
                }
            }
        }

        abortRunningTask();

        LOGGER.info("scheduler terminated");
    }

    /**
     * Reschedules the specified task to be run at the end of currently due executions.
     * The task will not be rescheduled if next execution is already due.
     *
     * @param clazz task to be rescheduled
     */
    public void trigger(Class<? extends Task> clazz) {
        trigger(clazz.getCanonicalName());
    }

    /**
     * Reschedules the specified task to be run at the end of currently due executions.
     * The task will not be rescheduled if next execution is already due.
     *
     * @param name task to be rescheduled
     */
    public void trigger(String name) {
        rescheduleIfEarlier(name, Instant.now());
    }

    /**
     * Reschedules the given task to be executed at the given time, if earlier than previously scheduled.
     *
     * @param clazz task to be rescheduled
     * @param time  time to schedule execution for, only applied if earlier than already scheduled time
     * @return the scheduled time of next execution
     */
    public Instant rescheduleIfEarlier(Class<? extends Task> clazz, Instant time) {
        return rescheduleIfEarlier(clazz.getCanonicalName(), time);
    }

    /**
     * Reschedules the given task to be executed at the given time, if earlier than previously scheduled.
     *
     * @param name task to be rescheduled
     * @param time time to schedule execution for, only applied if earlier than already scheduled time
     * @return the scheduled time of next execution
     */
    public Instant rescheduleIfEarlier(String name, Instant time) {
        synchronized (schedule) {
            if (!suppliers.containsKey(name)) {
                throw new IllegalArgumentException("task has not been registered: " + name);
            }

            Instant previousTime = schedule.get(name);
            if ((previousTime != null) && previousTime.isBefore(time)) {
                // do not reschedule as it would delay execution
                return previousTime;
            }

            schedule.put(name, time);
            schedule.notifyAll();
        }

        return time;
    }

    /**
     * Schedules the specified task to be run according to given parameters.
     *
     * @param clazz          task to be scheduled; must implement default constructor
     * @param wantedStart    time of first execution to be scheduled
     * @param repeatInterval interval to repeat the task at, measured from time of last finished execution
     * @see #schedule(String, ThrowingSupplier, Instant, Duration)
     */
    public void schedule(Class<? extends Task> clazz, Instant wantedStart, Duration repeatInterval) {
        try {
            schedule(clazz.getCanonicalName(), clazz.getDeclaredConstructor()::newInstance, wantedStart, repeatInterval);
        } catch (NoSuchMethodException ex) {
            throw new IllegalArgumentException("classes without a supplier must have a default constructor");
        }
    }

    /**
     * Schedules the specified task to be run according to given parameters.
     *
     * @param name           unique identification of the task to be scheduled
     * @param supplier       supplier creating a new instance of the {@link Task}
     * @param wantedStart    time of first execution to be scheduled
     * @param repeatInterval interval to repeat the task at, measured from time of last finished execution
     */
    public void schedule(String name, ThrowingSupplier<? extends Task, ?> supplier, Instant wantedStart, Duration repeatInterval) {
        synchronized (schedule) {
            if (schedule.containsKey(name)) {
                throw new IllegalArgumentException("task " + name + " is already scheduled");
            }

            suppliers.put(name, supplier);
            repeatIntervals.put(name, repeatInterval);

            schedule.put(name, wantedStart);
            schedule.notifyAll();
        }
    }

    private void abortRunningTask() {
        TaskExecution execution = runningTask.get();
        if ((execution == null) || execution.isDone()) {
            return;
        }

        LOGGER.warn("abort running task");
        execution.cancelAsync();
    }

    private Task instantiateTask(String name) {
        ThrowingSupplier<? extends Task, ?> supplier = suppliers.get(name);
        if (supplier == null) {
            LOGGER.warn("{} has no supplier", name);
            return null;
        }

        try {
            Task task = supplier.get();
            task.setScheduler(this);
            return task;
        } catch (Exception ex) {
            LOGGER.warn("{} failed instantiation", name, ex);
            return null;
        }
    }

    /**
     * Starts the scheduler.
     */
    public void start() {
        if (!schedulerThread.compareAndSet(null, new Thread(this))) {
            // already started
            LOGGER.warn("scheduler was attempted to be started twice");
            return;
        }

        if (shouldShutdown.get()) {
            LOGGER.warn("scheduler was shutdown before start");
            return;
        }

        Thread thread = schedulerThread.get();
        thread.setName(getClass().getSimpleName());
        thread.start();
    }

    /**
     * Shuts the scheduler down without blocking. Any running task will be cancelled.
     */
    public void shutdownAsync() {
        LOGGER.info("requesting shutdown");
        shouldShutdown.set(true);

        synchronized (schedule) {
            // suppliers may hold references that should be lost ASAP, e.g. if a surrounding application controller
            // shuts down or restarts
            suppliers.clear();

            // we don't want to start anything new, so just for good measure we can clear all other info as well
            repeatIntervals.clear();
            schedule.clear();

            schedule.notifyAll();
        }
    }

    /**
     * Shuts the scheduler down and waits until complete or the given timeout expires.
     * If a task is currently being executed, termination cancels the task and waits for it to finish.
     *
     * @param timeout maximum time to wait for shutdown to complete
     * @return {@code true} if shutdown completed within the timeout, {@code false} if the scheduler and/or a final task may still be running
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean shutdownAndWait(Duration timeout) throws InterruptedException {
        Instant startOfShutdown = Instant.now();

        shutdownAsync();

        Thread thread = schedulerThread.get();
        if (thread == null) {
            LOGGER.debug("scheduler was never started");
            return true;
        }

        LOGGER.info("waiting for scheduler to shut down (at most {})", timeout);
        thread.join(timeout.toMillis());
        if (thread.isAlive()) {
            LOGGER.warn("scheduler is still running after {}", timeout);
            return false;
        }

        Duration timeoutRemaining = timeout.minus(Duration.between(startOfShutdown, Instant.now()));
        TaskExecution execution = runningTask.get();
        if ((execution != null) && !execution.isDone()) {
            LOGGER.info("waiting for task to shut down (at most remaining {})", timeoutRemaining);
            if (!execution.cancelAndWait(timeoutRemaining)) {
                LOGGER.warn("task is still running after total timeout of {}", timeout);
                return false;
            }
        }

        return true;
    }

    /**
     * Sets the interval used to retry execution if a task failed.
     *
     * @param interval interval to retry failed tasks at
     * @return same instance for method-chaining
     */
    public QueueingScheduler setFailedRetryInterval(Duration interval) {
        if (interval.compareTo(Duration.ZERO) <= 0) {
            throw new IllegalArgumentException("intervals must be greater than 0");
        }

        failedRetryInterval.set(interval);

        return this;
    }

    /**
     * Sets the regular interval at which the {@link QueueingScheduler} should wake up to re-evaluate execution and
     * check for shutdown.
     *
     * @param interval interval to wake up scheduler for re-evaluation
     * @return same instance for method-chaining
     */
    public QueueingScheduler setIdleCheckInterval(Duration interval) {
        if (interval.compareTo(Duration.ZERO) <= 0) {
            throw new IllegalArgumentException("intervals must be greater than 0");
        }

        idleCheckInterval.set(interval);

        return this;
    }

    /**
     * Indicates that a call happened out of specified sequence.
     */
    private static class OutOfSequence extends RuntimeException {
        OutOfSequence(String msg) {
            super(msg);
        }
    }
}
