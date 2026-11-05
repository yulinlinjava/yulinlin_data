package com.yulinlin.security.risk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yulinlin.common.model.ModelDeleteWrapper;
import com.yulinlin.common.model.ModelInsertWrapper;
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.common.model.ModelUpdateWrapper;
import com.yulinlin.data.core.anno.JoinCluster;
import com.yulinlin.data.core.session.RouteSession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.core.wrapper.impl.ConditionManager;
import com.yulinlin.security.risk.RiskModels.*;
import com.yulinlin.security.risk.RiskTables.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/** Ordinary ORM CRUD only. Live decisions never call this store. */
public final class OrmBehaviorStore implements BehaviorStore {
    private final RiskProperties properties;
    private final ObjectMapper json = new ObjectMapper();
    private RouteSession route;
    private String group;

    public OrmBehaviorStore(RiskProperties properties) {
        this.properties = properties;
    }

    @Override public void initialize() {
        route = Objects.requireNonNull(SessionUtil.route(), "请先配置 ORM 数据源");
        var session = route.session(properties.getStorage().getGroup());
        group = session.group();
        // Use the datasource's existing CREATE / VALIDATE / NONE policy.
        session.initializeSchema(List.of(EventRow.class, MinuteRow.class, StateRow.class));
        for (Class<?> type : List.of(EventRow.class, MinuteRow.class, StateRow.class)) {
            select(type).page(1, 1).selectList(); // Fail at startup if required tables are unavailable.
        }
    }

    @Override public void writeEvents(List<Event> events) {
        if (events.isEmpty()) return;
        route.transaction(() -> {
            // One IN query per batch handles background retries, not one query per event.
            Map<String, Event> unique = new LinkedHashMap<>();
            events.forEach(event -> unique.putIfAbsent(event.id(), event));
            var existing = select(EventRow.class).in("id", unique.keySet()).selectList();
            existing.forEach(row -> unique.remove(row.id));
            List<EventRow> rows = new ArrayList<>();
            for (Event event : unique.values()) {
                var row = new EventRow();
                row.id = event.id(); row.subjectKey = subjectKey(event.subject());
                row.userId = event.subject().userId(); row.behaviorType = event.subject().behaviorType();
                row.resourceId = event.resourceId(); row.occurredAt = event.occurredAt();
                row.payload = json.writeValueAsString(event);
                rows.add(row);
            }
            ModelInsertWrapper.newInstance(group, rows).execute();
            return null;
        });
    }

    @Override public synchronized void writeSnapshot(Snapshot snapshot) {
        // Each process owns its stream IDs; its single writer saves snapshots in order.
        route.transaction(() -> {
            State state = snapshot.state();
            var previous = select(StateRow.class).eq("id", state.streamId()).selectOne();
            if (previous != null && previous.version >= state.version()) return null;

            List<MinuteRow> minutes = new ArrayList<>();
            for (Minute minute : snapshot.minutes()) {
                var row = new MinuteRow();
                row.id = DefaultRiskService.hash(state.streamId() + ":" + minute.start());
                row.streamId = state.streamId(); row.minuteStart = minute.start();
                row.version = minute.version(); row.payload = json.writeValueAsString(minute);
                minutes.add(row);
            }
            if (!minutes.isEmpty()) {
                Set<String> existing = new HashSet<>();
                select(MinuteRow.class).in("id", minutes.stream().map(row -> row.id).toList())
                        .selectList().forEach(row -> existing.add(row.id));
                ModelInsertWrapper.newInstance(group, minutes.stream().filter(row -> !existing.contains(row.id)).toList()).execute();
                ModelUpdateWrapper.newInstance(group, minutes.stream().filter(row -> existing.contains(row.id)).toList()).execute();
            }

            var row = new StateRow();
            row.id = state.streamId(); row.subjectKey = subjectKey(state.subject()); row.version = state.version();
            row.updatedAt = state.updatedAt(); row.retainUntil = Math.max(state.blockedUntil(), state.exemptUntil());
            row.payload = json.writeValueAsString(state);
            if (previous == null) ModelInsertWrapper.newInstance(group, row).execute();
            else ModelUpdateWrapper.newInstance(group, row).execute();
            return null;
        });
    }

    @Override public void restore(long cutoff, int limit, Consumer<Restored> consumer) throws Exception {
        Set<String> seen = new HashSet<>();
        long now = cutoff + properties.getMemory().getRetention().toMillis();
        for (int page = 1; seen.size() < limit; page++) {
            var states = select(StateRow.class)
                    .and(where -> where.or(or -> or.gte("updatedAt", cutoff).gt("retainUntil", now)))
                    .orderBy("updatedAt", false).orderBy("id", false).page(page, 256).selectList();
            if (states.isEmpty()) break;
            for (StateRow row : states) {
                if (!seen.add(row.subjectKey)) continue;
                State state = json.readValue(row.payload, State.class);
                var rows = select(MinuteRow.class).eq("streamId", state.streamId())
                        .gte("minuteStart", Math.max(cutoff, state.resetAt())).lte("version", state.version())
                        .orderBy("minuteStart", true).selectList();
                List<Minute> minutes = new ArrayList<>();
                for (MinuteRow minute : rows) minutes.add(json.readValue(minute.payload, Minute.class));
                consumer.accept(new Restored(state, minutes));
                if (seen.size() >= limit) break;
            }
        }
    }

    @Override public List<Event> history(Subject subject, long from, long to, int offset, int limit) throws Exception {
        DefaultRiskService.page(offset, limit);
        var query = select(EventRow.class).eq("subjectKey", subjectKey(subject))
                .gte("occurredAt", from).lt("occurredAt", to).orderBy("occurredAt", true).orderBy("id", true);
        int page = offset / limit + 1, skip = offset % limit;
        List<EventRow> rows = new ArrayList<>(query.page(page, limit).selectList());
        // The public offset may fall inside an ORM page; at most two pages are needed.
        if (skip > 0 && rows.size() == limit) rows.addAll(query.page(page + 1, limit).selectList());
        List<Event> result = new ArrayList<>();
        for (int i = skip; i < rows.size() && result.size() < limit; i++) {
            result.add(json.readValue(rows.get(i).payload, Event.class));
        }
        return List.copyOf(result);
    }

    @Override public void cleanup(long cutoff, long now) {
        cleanup(EventRow.class, "occurredAt", cutoff, now, row -> row.id);
        cleanup(MinuteRow.class, "minuteStart", cutoff, now, row -> row.id);
        cleanup(StateRow.class, "updatedAt", cutoff, now, row -> row.id);
    }

    private <T> void cleanup(Class<T> type, String timeField, long cutoff, long now, Function<T, String> id) {
        while (!Thread.currentThread().isInterrupted()) {
            var query = select(type).lt(timeField, cutoff).orderBy(timeField, true).page(1, 1000);
            if (type == StateRow.class) query.lte("retainUntil", now);
            List<T> rows = query.selectList();
            if (rows.isEmpty()) return;
            var delete = ModelDeleteWrapper.newInstance(group, type)
                    .in("id", rows.stream().map(id).toList()).lt(timeField, cutoff);
            if (type == StateRow.class) delete.lte("retainUntil", now);
            delete.execute();
            if (rows.size() < 1000) return;
        }
    }

    private <T> ModelSelectWrapper<T, ConditionManager<T>> select(Class<T> type) {
        var query = ModelSelectWrapper.<T, ConditionManager<T>>newInstance(group, type).cache(false).autoUpdate(false);
        query.getRequest().setCluster(JoinCluster.master);
        return query;
    }

    private static String subjectKey(Subject subject) {
        return DefaultRiskService.hash(subject.userId() + "\0" + subject.behaviorType());
    }
}
