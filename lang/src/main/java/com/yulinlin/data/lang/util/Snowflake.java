package com.yulinlin.data.lang.util;


import java.io.Serializable;
import java.util.Date;

public class Snowflake implements Serializable {
    private static final long serialVersionUID = 1L;
    private final long twepoch;
    private final long workerIdBits;
    private final long maxWorkerId;
    private final long dataCenterIdBits;
    private final long maxDataCenterId;
    private final long sequenceBits;
    private final long workerIdShift;
    private final long dataCenterIdShift;
    private final long timestampLeftShift;
    private final long sequenceMask;
    private final long workerId;
    private final long dataCenterId;
    private final boolean useSystemClock;
    private long sequence;
    private long lastTimestamp;

    public Snowflake(long workerId, long dataCenterId) {
        this(workerId, dataCenterId, false);
    }

    public Snowflake(long workerId, long dataCenterId, boolean isUseSystemClock) {
        this((Date)null, workerId, dataCenterId, isUseSystemClock);
    }

    public Snowflake(Date epochDate, long workerId, long dataCenterId, boolean isUseSystemClock) {
        this.workerIdBits = 5L;
        this.maxWorkerId = 31L;
        this.dataCenterIdBits = 5L;
        this.maxDataCenterId = 31L;
        this.sequenceBits = 12L;
        this.workerIdShift = 12L;
        this.dataCenterIdShift = 17L;
        this.timestampLeftShift = 22L;
        this.sequenceMask = 4095L;
        this.sequence = 0L;
        this.lastTimestamp = -1L;
        if (null != epochDate) {
            this.twepoch = epochDate.getTime();
        } else {
            this.twepoch = 1288834974657L;
        }

        if (workerId <= 31L && workerId >= 0L) {
            if (dataCenterId <= 31L && dataCenterId >= 0L) {
                this.workerId = workerId;
                this.dataCenterId = dataCenterId;
                this.useSystemClock = isUseSystemClock;
            } else {
                throw new IllegalArgumentException("datacenter Id can't be greater than 31 or less than 0");
            }
        } else {
            throw new IllegalArgumentException("worker Id can't be greater than 31 or less than 0");
        }
    }

    public long getWorkerId(long id) {
        return id >> 12 & 31L;
    }

    public long getDataCenterId(long id) {
        return id >> 17 & 31L;
    }

    public long getGenerateDateTime(long id) {
        return (id >> 22 & 2199023255551L) + this.twepoch;
    }

    public synchronized long nextId() {
        long timestamp = this.genTime();
        if (timestamp < this.lastTimestamp) {
            if (this.lastTimestamp - timestamp >= 2000L) {
                throw new IllegalStateException("Clock moved backwards. Refusing to generate id for "+ (this.lastTimestamp - timestamp)+"ms");
            }

            timestamp = this.lastTimestamp;
        }

        if (timestamp == this.lastTimestamp) {
            long sequence = this.sequence + 1L & 4095L;
            if (sequence == 0L) {
                timestamp = this.tilNextMillis(this.lastTimestamp);
            }

            this.sequence = sequence;
        } else {
            this.sequence = 0L;
        }

        this.lastTimestamp = timestamp;
        return timestamp - this.twepoch << 22 | this.dataCenterId << 17 | this.workerId << 12 | this.sequence;
    }

    public String nextIdStr() {
        return Long.toString(this.nextId());
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp;
        for(timestamp = this.genTime(); timestamp == lastTimestamp; timestamp = this.genTime()) {
            ;
        }

        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("Clock moved backwards. Refusing to generate id for"+(lastTimestamp - timestamp)+" ms");
        } else {
            return timestamp;
        }
    }

    private long genTime() {
        return System.currentTimeMillis();
    }
}
