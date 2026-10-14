package com.yulinlin.data.core.log;

import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.lang.reflection.GenericUtil;

import java.util.List;

//日志打印对象
//E 是请求对象
public interface LogPrint<E> {

    //执行成功
    void success(long time,ParseResult request);

    /** Session-aware hook; existing log implementations keep working through the default delegate. */
    default void success(long time, ParseResult request, EntitySession session) {
        success(time, request);
    }

    /**
     * Batch-aware success hook. Implementations that want one summary log per physical batch can override it;
     * legacy implementations retain their per-request behaviour through this default delegate.
     */
    default void successBatch(long elapsedMs, List<ParseResult> results, int affected, EntitySession session) {
        if (results == null || results.isEmpty()) return;
        long count = results.stream().filter(java.util.Objects::nonNull).count();
        if (count == 0) return;
        long averageElapsedMs = elapsedMs / count;
        for (ParseResult result : results) {
            if (result != null) success(averageElapsedMs, result, session);
        }
    }

    //执行失败
    void error(Throwable e,  ParseResult request);

    /** Session-aware hook; existing log implementations keep working through the default delegate. */
    default void error(Throwable e, ParseResult request, EntitySession session) {
        error(e, request);
    }

    /** Session-aware failure hook that also reports the actual execution duration. */
    default void error(long elapsedMs, Throwable e, ParseResult request, EntitySession session) {
        error(e, request, session);
    }

    /**
     * Batch-aware failure hook. Implementations may override it to avoid producing one error log per row.
     */
    default void errorBatch(Throwable error, List<ParseResult> results, EntitySession session) {
        if (results == null || results.isEmpty()) return;
        for (ParseResult result : results) {
            if (result != null) error(error, result, session);
        }
    }

    /** Batch failure hook that also reports the actual execution duration. */
    default void errorBatch(long elapsedMs, Throwable error, List<ParseResult> results, EntitySession session) {
        errorBatch(error, results, session);
    }

     default boolean isHandle( ParseResult result){
         if (result == null || result.getRequest() == null) return false;
         Class<?> generic = GenericUtil.getGeneric(this.getClass(), LogPrint.class, 0);
         Class<?> aClass = result.getRequest().getClass();

         return generic != null && (generic == aClass || generic.isAssignableFrom(aClass));
    }


}
