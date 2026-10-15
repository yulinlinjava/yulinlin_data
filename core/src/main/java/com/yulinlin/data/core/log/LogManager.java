package com.yulinlin.data.core.log;


import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.session.EntitySession;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 日志管理器
 */
@Slf4j
public class LogManager {

    /**
     * An immutable registry snapshot gives dispatchers a consistent handler list while allowing registration to
     * atomically replace both the list and its request-class cache. An in-flight dispatch may finish on its snapshot;
     * every dispatch started after register returns observes the new snapshot.
     */
    private volatile Registry registry = new Registry(List.of());

    public synchronized LogManager register(List<? extends LogPrint<?>> prints){
        if (prints == null || prints.isEmpty()) return this;

        List<HandlerRegistration> handlers = new ArrayList<>(registry.handlers());
        for (LogPrint<?> print : prints) {
            if (print != null) handlers.add(HandlerRegistration.of(print));
        }
        if (handlers.size() != registry.handlers().size()) {
            registry = new Registry(List.copyOf(handlers));
        }
        return this;
    }

    public synchronized LogManager register(LogPrint<?> print){
        if (print == null) return this;

        List<HandlerRegistration> handlers = new ArrayList<>(registry.handlers());
        handlers.add(HandlerRegistration.of(print));
        // Replacing the registry also discards every request-class decision cached by the previous registry.
        registry = new Registry(List.copyOf(handlers));
        return this;
    }



    //执行成功
    public void success(long time,ParseResult request){
        success(time, request, null);
    }

    public void success(long time, ParseResult request, EntitySession session){
        Registry snapshot = registry;
        for (HandlerRegistration registration : handlersFor(snapshot, request)) {
            invoke(registration.handler(), "success",
                    () -> registration.handler().success(time, request, session));
        }
    }

    /** Dispatches one physical batch to each matching handler exactly once. */
    public void successBatch(long elapsedMs, List<ParseResult> results, int affected, EntitySession session) {
        Registry snapshot = registry;
        Map<HandlerRegistration, List<ParseResult>> batches = batchesFor(snapshot, results);
        for (HandlerRegistration registration : snapshot.handlers()) {
            List<ParseResult> batch = batches.get(registration);
            if (batch == null) continue;
            LogPrint<?> handler = registration.handler();
            invoke(handler, "successBatch",
                    () -> handler.successBatch(elapsedMs, batch, affected, session));
        }
    }

    //执行失败
    public void error(Throwable e,  ParseResult request){
        error(e, request, null);
    }

    public void error(Throwable e, ParseResult request, EntitySession session){
        error(-1, e, request, session);
    }

    public void error(long elapsedMs, Throwable e, ParseResult request, EntitySession session){
        Registry snapshot = registry;
        for (HandlerRegistration registration : handlersFor(snapshot, request)) {
            invoke(registration.handler(), "error",
                    () -> registration.handler().error(elapsedMs, e, request, session));
        }
    }

    /** Dispatches one physical batch failure to each matching handler exactly once. */
    public void errorBatch(Throwable error, List<ParseResult> results, EntitySession session) {
        errorBatch(-1, error, results, session);
    }

    public void errorBatch(long elapsedMs, Throwable error, List<ParseResult> results, EntitySession session) {
        Registry snapshot = registry;
        Map<HandlerRegistration, List<ParseResult>> batches = batchesFor(snapshot, results);
        for (HandlerRegistration registration : snapshot.handlers()) {
            List<ParseResult> batch = batches.get(registration);
            if (batch == null) continue;
            LogPrint<?> handler = registration.handler();
            invoke(handler, "errorBatch", () -> handler.errorBatch(elapsedMs, error, batch, session));
        }
    }

    private List<HandlerRegistration> handlersFor(Registry snapshot, ParseResult result) {
        if (result == null || result.getRequest() == null || snapshot.handlers().isEmpty()) return List.of();
        Class<?> requestClass = result.getRequest().getClass();
        List<HandlerRegistration> stableMatches = snapshot.handlersByRequestClass().computeIfAbsent(requestClass,
                ignored -> resolveHandlers(snapshot.stableHandlers(), result));
        if (snapshot.dynamicHandlers().isEmpty()) return stableMatches;

        List<HandlerRegistration> matches = new ArrayList<>(stableMatches.size() + snapshot.dynamicHandlers().size());
        for (HandlerRegistration registration : snapshot.handlers()) {
            if (registration.stableSelection()) {
                if (containsIdentity(stableMatches, registration)) matches.add(registration);
                continue;
            }
            LogPrint<?> handler = registration.handler();
            try {
                if (handler.isHandle(result)) matches.add(registration);
            } catch (RuntimeException exception) {
                warn(handler, "isHandle", exception);
            }
        }
        return List.copyOf(matches);
    }

    private static boolean containsIdentity(List<HandlerRegistration> handlers, HandlerRegistration candidate) {
        for (HandlerRegistration handler : handlers) if (handler == candidate) return true;
        return false;
    }

    private List<HandlerRegistration> resolveHandlers(List<HandlerRegistration> handlers, ParseResult result) {
        List<HandlerRegistration> matches = new ArrayList<>();
        for (HandlerRegistration registration : handlers) {
            LogPrint<?> handler = registration.handler();
            try {
                if (handler.isHandle(result)) matches.add(registration);
            } catch (RuntimeException exception) {
                warn(handler, "isHandle", exception);
            }
        }
        return List.copyOf(matches);
    }

    private Map<HandlerRegistration, List<ParseResult>> batchesFor(Registry snapshot, List<ParseResult> results) {
        if (results == null || results.isEmpty() || snapshot.handlers().isEmpty()) return Map.of();

        Map<HandlerRegistration, List<ParseResult>> batches = new IdentityHashMap<>();
        for (ParseResult result : results) {
            if (result == null || result.getRequest() == null) continue;
            for (HandlerRegistration registration : handlersFor(snapshot, result)) {
                batches.computeIfAbsent(registration, ignored -> new ArrayList<>()).add(result);
            }
        }
        return batches;
    }

    private void invoke(LogPrint<?> handler, String operation, Runnable invocation) {
        try {
            invocation.run();
        } catch (RuntimeException exception) {
            warn(handler, operation, exception);
        }
    }

    private void warn(LogPrint<?> handler, String operation, RuntimeException exception) {
        String handlerName = handler == null ? "null" : handler.getClass().getName();
        try {
            log.warn("日志处理器执行失败，已与业务执行隔离 handler={} operation={}",
                    handlerName, operation, exception);
        } catch (RuntimeException ignored) {
            // Even a broken logging backend must never alter the business result or replace the original failure.
        }
    }

    private record HandlerRegistration(LogPrint<?> handler, boolean stableSelection) {
        private static HandlerRegistration of(LogPrint<?> handler) {
            boolean usesDefaultSelection;
            try {
                usesDefaultSelection = handler.getClass()
                        .getMethod("isHandle", ParseResult.class)
                        .getDeclaringClass() == LogPrint.class;
            } catch (ReflectiveOperationException | SecurityException ignored) {
                // A custom or inaccessible selector may depend on the complete ParseResult, so never cache it.
                usesDefaultSelection = false;
            }
            return new HandlerRegistration(handler, usesDefaultSelection);
        }
    }

    private record Registry(List<HandlerRegistration> handlers,
                            List<HandlerRegistration> stableHandlers,
                            List<HandlerRegistration> dynamicHandlers,
                            ConcurrentMap<Class<?>, List<HandlerRegistration>> handlersByRequestClass) {
        private Registry(List<HandlerRegistration> handlers) {
            this(handlers,
                    handlers.stream().filter(HandlerRegistration::stableSelection).toList(),
                    handlers.stream().filter(handler -> !handler.stableSelection()).toList(),
                    new ConcurrentHashMap<>());
        }
    }

}
