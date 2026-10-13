package com.yulinlin.data.core.session;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Framework-wide ORM behavior shared by every registered data source. */
@ConfigurationProperties("yulinlin.data")
public class DataProperties {

    /**
     * Makes entity query results transaction-managed. Managed setters are flushed before commit.
     */
    private boolean autoUpdate;

    private final QueueProperties queue = new QueueProperties();

    public boolean isAutoUpdate() {
        return autoUpdate;
    }

    public void setAutoUpdate(boolean autoUpdate) {
        this.autoUpdate = autoUpdate;
    }

    public QueueProperties getQueue() {
        return queue;
    }

    /** Global delayed-write queue settings under {@code yulinlin.data.queue}. */
    public static class QueueProperties {

        private Duration flushInterval = Duration.ofMinutes(1);

        /** Maximum number of pending nodes, rather than the number of wrapper requests. */
        private int capacity = 100_000;

        /** Maximum number of data-source sessions flushed concurrently. */
        private int concurrency = 4;

        /** A group reaching this many nodes triggers an early flush; it does not split the transaction. */
        private int flushSize = 10_000;

        /** Number of later flush attempts after the first database execution fails. */
        private int retryCount = 3;

        public Duration getFlushInterval() {
            return flushInterval;
        }

        public void setFlushInterval(Duration flushInterval) {
            if (flushInterval == null || flushInterval.isNegative() || flushInterval.isZero()
                    || flushInterval.toMillis() == 0) {
                throw new IllegalArgumentException("yulinlin.data.queue.flush-interval must be at least 1ms");
            }
            this.flushInterval = flushInterval;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("yulinlin.data.queue.capacity must be positive");
            }
            this.capacity = capacity;
        }

        public int getRetryCount() {
            return retryCount;
        }

        public void setRetryCount(int retryCount) {
            if (retryCount < 0) {
                throw new IllegalArgumentException("yulinlin.data.queue.retry-count must not be negative");
            }
            this.retryCount = retryCount;
        }

        public int getConcurrency() {
            return concurrency;
        }

        public void setConcurrency(int concurrency) {
            if (concurrency <= 0) {
                throw new IllegalArgumentException("yulinlin.data.queue.concurrency must be positive");
            }
            this.concurrency = concurrency;
        }

        public int getFlushSize() {
            return flushSize;
        }

        public void setFlushSize(int flushSize) {
            if (flushSize <= 0) {
                throw new IllegalArgumentException("yulinlin.data.queue.flush-size must be positive");
            }
            this.flushSize = flushSize;
        }
    }
}
