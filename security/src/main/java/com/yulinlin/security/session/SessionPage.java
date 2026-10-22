package com.yulinlin.security.session;

import java.util.List;

/** 实时分页，不保证跨页快照一致；total=null 表示没有精确总数。 */
public record SessionPage(List<LoginSessionView> items, String nextCursor, boolean hasMore, Long total) {
    public SessionPage { items = List.copyOf(items); }
}
