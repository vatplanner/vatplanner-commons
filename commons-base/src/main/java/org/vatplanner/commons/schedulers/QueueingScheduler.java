package org.vatplanner.commons.schedulers;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueueingScheduler implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueueingScheduler.class);

    private final Map<Class<? extends Task>, Instant> schedule = new HashMap<>();
    private final Map<Class<? extends Task>, Duration> repeatIntervals = new HashMap<>();
    private final AtomicReference<TaskExecution> runningTask = new AtomicReference<>();

    private final AtomicReference<Thread> schedulerThread = new AtomicReference<>();
    private final AtomicBoolean shouldShutdown = new AtomicBoolean();

    private static final Duration FAILED_RETRY = Duration.ofMinutes(5);
    private static final Duration IDLE_CHECK_INTERVAL = Duration.ofSeconds(1);

    private static class TaskExecution implements Runnable {
        private final Thread thread;
        private final Task task;
        private final Object notificationObject;
        private final AtomicReference<Exception> exception = new AtomicReference<>();
        private final AtomicBoolean done = new AtomicBoolean();

        TaskExecution(Task task, Object notificationObject) {
            this.notificationObject = notificationObject;
            this.task = task;
            this.thread = new Thread(this);
            thread.setName("Task " + task.getClass().getSimpleName());
            thread.start();
        }

        Class<? extends Task> getTaskClass() {
            return task.getClass();
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
                LOGGER.warn("task {} failed", task.getClass(), ex);
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
                Class<? extends Task> clazz = previousTask.getTaskClass();
                Duration interval = repeatIntervals.get(clazz);

                boolean failed = (previousTask.exception.get() != null);
                if (failed) {
                    LOGGER.warn("task {} has failed, applying retry interval", clazz);
                    interval = FAILED_RETRY;
                }

                if (interval == null) {
                    LOGGER.info("task {} finished, not rescheduling (no interval configured)", clazz);
                } else {
                    Instant nextRun = Instant.now().plus(interval);
                    schedule.put(clazz, nextRun);
                    LOGGER.info("task {} finished, rescheduled for {}", clazz, nextRun);
                }

                runningTask.set(null);
                canStartNextTask = true;
            }

            synchronized (schedule) {
                Instant sleepUntil = Instant.now().plus(IDLE_CHECK_INTERVAL);

                if (canStartNextTask) {
                    Map.Entry<Class<? extends Task>, Instant> nextTask = schedule.entrySet()
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

                            Class<? extends Task> clazz = nextTask.getKey();
                            Task task = instantiateTask(clazz);
                            if (task == null) {
                                LOGGER.warn("postponing {} for {} due to failed construction", clazz, FAILED_RETRY);
                                schedule.put(clazz, Instant.now().plus(FAILED_RETRY));
                                continue; // check next entry
                            }

                            LOGGER.info("starting task {}", clazz);
                            runningTask.set(new TaskExecution(task, schedule));
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
        synchronized (schedule) {
            if (!schedule.containsKey(clazz)) {
                throw new IllegalArgumentException("class has not been scheduled: " + clazz.getCanonicalName());
            }

            schedule.put(clazz, Instant.now());
            schedule.notifyAll();
        }
    }

    public void schedule(Class<? extends Task> clazz, Instant wantedStart, Duration repeatInterval) {
        synchronized (repeatIntervals) {
            repeatIntervals.put(clazz, repeatInterval);
        }

        synchronized (schedule) {
            schedule.put(clazz, wantedStart);
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

    private Task instantiateTask(Class<? extends Task> clazz) {
        try {
            Task task = clazz.getDeclaredConstructor().newInstance();
            task.setScheduler(this);
            return task;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException ex) {
            LOGGER.warn("{} failed construction", clazz, ex);
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
