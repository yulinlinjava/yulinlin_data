package com.yulinlin.sync;

/** 正常返回表示目标事务已提交；异常时不自动判断目标是否已提交，不自动重试。 */
@FunctionalInterface
public interface SyncWriter {
    void write(SyncTask task);
}
