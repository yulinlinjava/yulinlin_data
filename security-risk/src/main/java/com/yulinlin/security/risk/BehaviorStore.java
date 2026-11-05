package com.yulinlin.security.risk;

import com.yulinlin.security.risk.RiskModels.*;
import java.util.List;
import java.util.function.Consumer;

/** Called only by background workers, except explicit administrative history queries. */
public interface BehaviorStore {
    void initialize() throws Exception;
    /** Idempotent by Event.id. One batch must either commit entirely or roll back. */
    void writeEvents(List<Event> events) throws Exception;
    /** Monotonic per stream; replay of an older or equal version must not overwrite newer data. */
    void writeSnapshot(Snapshot snapshot) throws Exception;
    /** Latest complete stream per subject, newest subjects first; bounded by limit. */
    void restore(long cutoff, int limit, Consumer<Restored> consumer) throws Exception;
    List<Event> history(Subject subject, long from, long to, int offset, int limit) throws Exception;
    void cleanup(long cutoff, long now) throws Exception;
}

