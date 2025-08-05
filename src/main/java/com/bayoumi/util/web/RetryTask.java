package com.bayoumi.util.web;


import com.bayoumi.util.LoggerWrapper;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A configurable retry task using exponential backoff and optional jitter.
 */
public class RetryTask {
    private final Supplier<Boolean> action;
    private final int maxRetries;
    private final long initialBackoffMs;
    private final boolean jitter;
    private final String threadName;
    private static final Logger LOGGER = LoggerWrapper.loggerFactory(RetryTask.class);


    private RetryTask(Builder builder) {
        this.action = builder.action;
        this.maxRetries = builder.maxRetries;
        this.initialBackoffMs = builder.initialBackoffMs;
        this.jitter = builder.jitter;
        this.threadName = builder.threadName;
    }

    /**
     * Executes the action synchronously with retries.
     *
     * @return true if action eventually succeeds, false otherwise
     */
    public boolean execute() {
        long backoff = initialBackoffMs;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            LOGGER.info(String.format("[RetryTask-%s] Attempt %d/%d...", threadName, attempt, maxRetries));
            try {
                if (action.get()) {
                    return true;
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, String.format("[RetryTask-%s] Exception on attempt %d", threadName, attempt), e);
            }

            if (attempt == maxRetries) {
                break;
            }

            long sleepMs = backoff;
            if (jitter) {
                long delta = backoff / 5;
                sleepMs += ThreadLocalRandom.current().nextLong(-delta, delta);
            }

            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }

            backoff *= 2;
        }

        LOGGER.log(Level.SEVERE, String.format("[RetryTask-%s] All retry attempts failed: %s", threadName, action.get()));
        return false;
    }

    /**
     * Executes the action asynchronously in a daemon thread.
     */
    public void executeAsync() {
        Thread t = new Thread(this::execute, threadName);
        t.setDaemon(true);
        t.setUncaughtExceptionHandler((thread, ex) ->
                LOGGER.log(Level.SEVERE, "Uncaught in " + threadName, ex)
        );
        t.start();
    }

    /**
     * Creates a new Builder for a RetryTask. Action is mandatory.
     */
    public static Builder builder(Supplier<Boolean> action) {
        return new Builder(action);
    }

    public static class Builder {
        private final Supplier<Boolean> action;
        private int maxRetries = 3;
        private long initialBackoffMs = 5_000;
        private boolean jitter = false;
        private String threadName = "SendRequest-Thread";

        public Builder(Supplier<Boolean> action) {
            if (action == null) {
                throw new IllegalArgumentException("Action must not be null");
            }
            this.action = action;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder initialBackoffMs(long initialBackoffMs) {
            this.initialBackoffMs = initialBackoffMs;
            return this;
        }

        public Builder enableJitter(boolean jitter) {
            this.jitter = jitter;
            return this;
        }

        public Builder threadName(String threadName) {
            this.threadName = threadName;
            return this;
        }

        /**
         * Builds the RetryTask instance.
         */
        public RetryTask build() {
            return new RetryTask(this);
        }

        /**
         * Convenience: builds and executes sync retry.
         */
        public boolean execute() {
            return build().execute();
        }

        /**
         * Convenience: builds and executes async retry.
         */
        public void executeAsync() {
            build().executeAsync();
        }
    }
}