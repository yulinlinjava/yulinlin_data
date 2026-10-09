package com.yulinlin.data.cache.ehcache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties("yulinlin.cache.ehcache")
public class EhcacheProperties {

    private Path directory = Path.of("data", "cache");
    private long heapEntries = 10_000;
    private long diskSizeMb = 1024;

    public Path getDirectory() {
        return directory;
    }

    public void setDirectory(Path directory) {
        this.directory = directory;
    }

    public long getHeapEntries() {
        return heapEntries;
    }

    public void setHeapEntries(long heapEntries) {
        if (heapEntries <= 0) throw new IllegalArgumentException("heap-entries must be positive");
        this.heapEntries = heapEntries;
    }

    public long getDiskSizeMb() {
        return diskSizeMb;
    }

    public void setDiskSizeMb(long diskSizeMb) {
        if (diskSizeMb <= 0) throw new IllegalArgumentException("disk-size-mb must be positive");
        this.diskSizeMb = diskSizeMb;
    }
}
