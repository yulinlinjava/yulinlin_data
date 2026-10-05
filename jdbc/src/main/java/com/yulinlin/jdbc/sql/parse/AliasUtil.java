package com.yulinlin.jdbc.sql.parse;

import com.yulinlin.data.core.node.AbstractMetaNode;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.jdbc.sql.SqlParamsContext;
import java.util.ArrayDeque;
import java.util.Deque;

public class AliasUtil {
    private static final ThreadLocal<Deque<Boolean>> local = ThreadLocal.withInitial(ArrayDeque::new);

    public static void push(boolean open) { local.get().addLast(open); }
    public static void pop() {
        Deque<Boolean> stack = local.get();
        stack.removeLast();
        if (stack.isEmpty()) local.remove();
    }
    public static boolean ok() { return local.get().isEmpty() || local.get().getFirst(); }

    public static boolean supportAlias(IParamsContext params) {
        return params.getRequestType() == RequestType.select
                || params.getRequestType() == RequestType.count
                || params.getRequestType() == RequestType.group;
    }

    public static String parse(String name, IParamsContext params) {
        return SqlParamsContext.nameParse(params).parse(name, params);
    }

    public static String parse(AbstractMetaNode node, IParamsContext params) {
        return SqlParamsContext.nameParse(params).parse(node, params, SqlParamsContext.parseManager(params));
    }
}
