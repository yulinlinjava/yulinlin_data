package com.yulinlin.data.core.exception;

/** 主键或唯一约束冲突，不包含外键、非空等其他约束错误。 */
public final class DuplicateDataException extends OrmException {
    public DuplicateDataException(String message, Throwable cause) { super(message, cause); }
}
