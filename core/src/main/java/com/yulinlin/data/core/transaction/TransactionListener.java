package com.yulinlin.data.core.transaction;

public interface TransactionListener {

    /** 最外层 ORM 事务开启时通知一次；保存点不触发物理事务监听器。 */
    void startTransaction();

    /** 最外层提交前处理；监听结束后由 RouteSession 刷新代理修改，此时数据库尚未提交。 */
    void commitTransaction();

    /** 最外层回滚前处理；内层失败只回滚自己的保存点。 */
    void rollbackTransaction();

    /** 最外层实际提交成功、旧框架事务上下文清理后通知一次。 */
    default void afterCommit() { }

    /** 最外层业务事务结束时通知（包括失败）；在 afterCommit 通知前执行。 */
    default void afterCompletion() { }
}
