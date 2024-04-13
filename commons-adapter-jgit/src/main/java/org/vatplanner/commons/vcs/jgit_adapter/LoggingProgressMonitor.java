package org.vatplanner.commons.vcs.jgit_adapter;

import java.time.Duration;
import java.time.Instant;

import org.eclipse.jgit.lib.ProgressMonitor;
import org.slf4j.Logger;

class LoggingProgressMonitor implements ProgressMonitor {
    private final Logger logger;
    private final String logPrefix;
    private final Duration progressUpdateCooldown;

    private static final String DEFAULT_TITLE = "<unknown>";

    private String totalTaskLogPrefix = "";
    private int task = 0;
    private int totalTasks = 0;
    private int totalWork = 0;
    private int workCompleted = 0;
    private String title = null;

    private Instant nextProgressUpdateWanted = Instant.EPOCH;

    LoggingProgressMonitor(Logger logger, String logPrefix) {
        this(logger, logPrefix, Duration.ofSeconds(5));
    }

    LoggingProgressMonitor(Logger logger, String logPrefix, Duration progressUpdateCooldown) {
        this.logger = logger;
        this.logPrefix = logPrefix;
        this.progressUpdateCooldown = progressUpdateCooldown;
    }

    @Override
    public void start(int totalTasks) {
        this.totalTasks = totalTasks;
    }

    @Override
    public void beginTask(String title, int totalWork) {
        this.title = title;
        this.totalWork = totalWork;
        task++;
        workCompleted = 0;
        nextProgressUpdateWanted = Instant.EPOCH;

        if ((totalTasks <= 0) || (task > totalTasks)) {
            totalTaskLogPrefix = "";
        } else {
            totalTaskLogPrefix = String.format("[%d/%d] ", task, totalTasks);
        }

        logger.info("{}{}[{}] task started", logPrefix, totalTaskLogPrefix, title);
    }

    @Override
    public void update(int completed) {
        if (completed == 0) {
            return;
        }

        workCompleted += completed;

        if (!logger.isInfoEnabled()) {
            return;
        }

        // avoid spamming the log with too frequent updates
        if (Instant.now().isBefore(nextProgressUpdateWanted)) {
            return;
        }
        nextProgressUpdateWanted = Instant.now().plus(progressUpdateCooldown);

        if (totalWork <= 0) {
            logger.info("{}{}[{}] progress: {}", logPrefix, totalTaskLogPrefix, title, workCompleted);
        } else {
            logger.info("{}{}[{}] progress: {}", logPrefix, totalTaskLogPrefix, title, String.format("%.1f%%", ((double) workCompleted) / totalWork * 100.0));
        }
    }

    @Override
    public void endTask() {
        logger.info("{}{}[{}] task finished", logPrefix, totalTaskLogPrefix, title);

        nextProgressUpdateWanted = Instant.EPOCH;
        totalTaskLogPrefix = "";
        title = DEFAULT_TITLE;
        totalWork = 0;
        workCompleted = 0;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }
}
