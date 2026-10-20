package com.yulinlin.elasticsearch.log;

import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.session.SessionExecution;
import lombok.extern.slf4j.Slf4j;

import java.util.StringJoiner;

/** ElasticsearchSession 直接调用，不参与 Bean 注册或日志分发。 */
@Slf4j
public final class EsExecutionLog {
    private EsExecutionLog() { }

    public static void print(SessionExecution execution, boolean enabled) {
        if (execution.error() == null ? !enabled || !log.isInfoEnabled() : !log.isErrorEnabled()) return;
        ParseResult first = execution.requests().isEmpty() ? null : execution.requests().getFirst();
        StringJoiner requests = new StringJoiner(", ", "[", "]");
        int limit = Math.min(5, execution.requests().size());
        for (int i = 0; i < limit; i++) {
            String value = String.valueOf(execution.requests().get(i).getRequest()).replace('\n', ' ').replace('\r', ' ');
            requests.add(value.length() <= 1024 ? value : value.substring(0, 1024) + "...");
        }
        if (execution.requests().size() > limit) requests.add("...其余请求已省略");
        String message = "ES执行" + (execution.error() == null ? "成功" : "异常")
                + " txId=" + (first == null ? "-" : first.getTransactionId())
                + " group=" + execution.session().group() + " cluster=" + execution.session().cluster()
                + " type=" + (first == null ? "unknown" : first.getType())
                + " elapsedMs=" + execution.elapsedMillis() + " nodes=" + execution.requests().size()
                + (execution.affectedRows() == null ? "" : " affected=" + execution.affectedRows())
                + " requests=" + requests;
        if (execution.error() == null) log.info(message);
        else log.error(message, execution.error());
    }
}
