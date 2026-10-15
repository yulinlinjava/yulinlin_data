package com.yulinlin.common.service.impl2;

import com.yulinlin.common.model.AbstractModel;

/** @deprecated Implement {@link com.yulinlin.common.service.ISuperService} directly. */
@Deprecated(since = "3.0", forRemoval = false)
public abstract class ServiceImpl<E extends AbstractModel<E>>
        extends com.yulinlin.common.service.impl.ServiceImpl<E> {
}
