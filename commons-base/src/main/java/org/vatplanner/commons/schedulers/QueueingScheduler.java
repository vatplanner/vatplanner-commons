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

public class QueueingScheduler implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueueingScheduler.class);

    private final Map<String, ThrowingSupplier<? extends Task, ?>> suppliers = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, Instant> schedule = new HashMap<>();
    private final Map<String, Duration> repeatIntervals = Collections.synchronizedMap(new HashMap<>());
    private final AtomicReference<TaskExecution> runningTask = new AtomicReference<>();

    private final AtomicReference<Thread> schedulerThread = new AtomicReference<>();
    private final AtomicBoolean shouldShutdown = new AtomicBoolean();

    private static final Duration FAILED_RETRY = Duration.ofMinutes(5);
    private static final Duration IDLE_CHECK_INTERVAL = Duration.ofSeconds(1);

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

    public abstract static class Task implements Runnable {
        private final AtomicBoolean isCancelled = new AtomicBoolean();
        private final AtomicReference<QueueingScheduler> scheduler = new AtomicReference<>();

        private void setScheduler(QueueingScheduler scheduler) {
            this.scheduler.set(scheduler);
        }

        public QueueingScheduler getScheduler() {
            QueueingScheduler res = scheduler.get();
            if (res == null) {
                throw new OutOfSequence("scheduler has not finished task instantiation yet");
            }
            return res;
        }

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
                    interval = FAILED_RETRY;
                }

                if (interval == null) {
                    LOGGER.info("task {} finished, not rescheduling (no interval configured)", taskName);
                } else {
                    Instant nextRun = Instant.now().plus(interval);
                    schedule.put(taskName, nextRun);
                    LOGGER.info("task {} finished, rescheduled for {}", taskName, nextRun);
                }

                runningTask.set(null);
                canStartNextTask = true;
            }

            synchronized (schedule) {
                Instant sleepUntil = Instant.now().plus(IDLE_CHECK_INTERVAL);

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
                                LOGGER.warn("postponing {} for {} due to failed construction", taskName, FAILED_RETRY);
                                schedule.put(taskName, Instant.now().plus(FAILED_RETRY));
                                continue; // check next entry
                            }

                            LOGGER.info("starting task {}", taskName);
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

    public void trigger(Class<? extends Task> clazz) {
        trigger(clazz.getCanonicalName());
    }

    public void trigger(String name) {
        synchronized (schedule) {
            if (!schedule.containsKey(name)) {
                throw new IllegalArgumentException("task has not been scheduled: " + name);
            }

            schedule.put(name, Instant.now());
            schedule.notifyAll();
        }
    }

    public void schedule(Class<? extends Task> clazz, Instant wantedStart, Duration repeatInterval) {
        try {
            scheduleInternally(clazz.getCanonicalName(), clazz.getDeclaredConstructor()::newInstance, wantedStart, repeatInterval);
        } catch (NoSuchMethodException ex) {
            throw new IllegalArgumentException("classes without a supplier must have a default constructor");
        }
    }

    public void schedule(String name, ThrowingSupplier<? extends Task, ?> supplier, Instant wantedStart, Duration repeatInterval) {
        scheduleInternally(name, supplier, wantedStart, repeatInterval);
    }

    private void scheduleInternally(String name, ThrowingSupplier<? extends Task, ?> supplier, Instant wantedStart, Duration repeatInterval) {
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

    public void shutdownAsync() {
        LOGGER.info("requesting shutdown");
        shouldShutdown.set(true);

        synchronized (schedule) {
            schedule.notifyAll();
        }
    }

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

    private static class OutOfSequence extends RuntimeException {
        OutOfSequence(String msg) {
            super(msg);
        }
    }
}
