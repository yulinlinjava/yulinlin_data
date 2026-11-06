package com.yulinlin.data.core.exception;

/** ORM 执行或事务失败；原始数据库错误保留在原因链中。 */
public class OrmException extends RuntimeException {
    public OrmException(String message) { super(message); }
    public OrmException(String message, Throwable cause) { super(message, cause); }
}
