package com.yulinlin.data.core.session;

/** Instance-local, thread-confined nesting state; not shared between sessions. */
final class TransactionScope {
    private static final class State {
        int depth;
        boolean rollbackOnly;
    }

    private final ThreadLocal<State> local = new ThreadLocal<>();

    void start() {
        State state = local.get();
        if (state == null) { state = new State(); local.set(state); }
        state.depth++;
    }

    void finish(boolean rollback) {
        State state = local.get();
        if (state == null) return;
        state.rollbackOnly |= rollback;
        if (--state.depth == 0) local.remove();
    }

    boolean isOpen() { return local.get() != null; }
    int depth() { return local.get() == null ? 0 : local.get().depth; }
    Object identity() { return local.get(); }
    boolean isRollbackOnly() { return local.get() != null && local.get().rollbackOnly; }
    void setRollbackOnly() { if (local.get() != null) local.get().rollbackOnly = true; }
}
