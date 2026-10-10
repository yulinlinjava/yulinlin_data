package com.yulinlin.data.core.cache;

import com.yulinlin.data.core.exception.NoticeException;

/** Raised when CACHE_ONLY cannot find the requested value. */
public class CacheMissException extends NoticeException {
    public CacheMissException(String message) {
        super(message);
    }
}
