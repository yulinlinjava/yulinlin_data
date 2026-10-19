package com.yulinlin.data.core.transaction;

public interface TransactionListener {

    /** 最外层框架事务开启时通知一次；嵌套层只更新共享上下文深度。 */
    void startTransaction();

    /** 最外层提交前处理；监听结束后由管理器统一刷新代理修改，此时数据库尚未提交。 */
    void commitTransaction();

    /** 最外层回滚前处理；内层回滚只标记共享事务 rollback-only。 */
    void rollbackTransaction();

    /** 最外层实际提交成功、旧框架事务上下文清理后通知一次。 */
    default void afterCommit() { }

    /** 最外层业务事务结束时通知（包括失败）；在 afterCommit 通知前执行。 */
    default void afterCompletion() { }
}
