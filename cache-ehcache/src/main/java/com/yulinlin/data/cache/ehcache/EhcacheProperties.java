package com.yulinlin.data.cache.ehcache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties("yulinlin.cache.ehcache")
public class EhcacheProperties {

    private Path directory = Path.of("data", "cache");
    private long heapEntries = 10_000;
    /** Zero disables the off-heap tier. */
    private long offheapSizeMb;
    private long diskSizeMb = 1024;
    private boolean persistent = true;
    private ExpirationPolicy expirationPolicy = ExpirationPolicy.AFTER_WRITE;
    private int diskThreads = 2;
    private long maximumEntrySizeMb = 16;
    private boolean recordStatistics = true;

    public Path getDirectory() {
        return directory;
    }

    public void setDirectory(Path directory) {
        if (directory == null) throw new IllegalArgumentException("directory must not be null");
        this.directory = directory;
    }

    public long getHeapEntries() {
        return heapEntries;
    }

    public void setHeapEntries(long heapEntries) {
        if (heapEntries <= 0) throw new IllegalArgumentException("heap-entries must be positive");
        this.heapEntries = heapEntries;
    }

    public long getOffheapSizeMb() {
        return offheapSizeMb;
    }

    public void setOffheapSizeMb(long offheapSizeMb) {
        if (offheapSizeMb < 0) throw new IllegalArgumentException("offheap-size-mb must not be negative");
        this.offheapSizeMb = offheapSizeMb;
    }

    public long getDiskSizeMb() {
        return diskSizeMb;
    }

    public void setDiskSizeMb(long diskSizeMb) {
        if (diskSizeMb <= 0) throw new IllegalArgumentException("disk-size-mb must be positive");
        this.diskSizeMb = diskSizeMb;
    }

    public boolean isPersistent() {
        return persistent;
    }

    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    public ExpirationPolicy getExpirationPolicy() {
        return expirationPolicy;
    }

    public void setExpirationPolicy(ExpirationPolicy expirationPolicy) {
        if (expirationPolicy == null) throw new IllegalArgumentException("expiration-policy must not be null");
        this.expirationPolicy = expirationPolicy;
    }

    public int getDiskThreads() {
        return diskThreads;
    }

    public void setDiskThreads(int diskThreads) {
        if (diskThreads <= 0) throw new IllegalArgumentException("disk-threads must be positive");
        this.diskThreads = diskThreads;
    }

    public long getMaximumEntrySizeMb() {
        return maximumEntrySizeMb;
    }

    public void setMaximumEntrySizeMb(long maximumEntrySizeMb) {
        if (maximumEntrySizeMb <= 0) {
            throw new IllegalArgumentException("maximum-entry-size-mb must be positive");
        }
        this.maximumEntrySizeMb = maximumEntrySizeMb;
    }

    public boolean isRecordStatistics() {
        return recordStatistics;
    }

    public void setRecordStatistics(boolean recordStatistics) {
        this.recordStatistics = recordStatistics;
    }

    public enum ExpirationPolicy {
        /** TTL starts when an entry is written and reads never extend it. */
        AFTER_WRITE,
        /** Each successful read restarts the entry's request/default TTL. */
        AFTER_ACCESS
    }
}
