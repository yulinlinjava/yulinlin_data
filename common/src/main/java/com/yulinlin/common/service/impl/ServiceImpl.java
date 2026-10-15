package com.yulinlin.common.service.impl;

import com.yulinlin.common.model.AbstractModel;
import com.yulinlin.common.service.ISuperService;

import java.util.List;
import java.util.Map;

/**
 * @deprecated Implement {@link ISuperService} directly. This class preserves the former extension API.
 */
@Deprecated(since = "3.0", forRemoval = false)
public abstract class ServiceImpl<E extends AbstractModel<E>> implements ISuperService<E> {

    /** @deprecated Override {@link #groupForSave(List)} in new implementations. */
    @Deprecated(since = "3.0", forRemoval = false)
    protected Map<Boolean, List<E>> dataGroup(List<E> coll) {
        return ISuperService.super.groupForSave(coll);
    }

    @Override
    public Map<Boolean, List<E>> groupForSave(List<E> coll) {
        return dataGroup(coll);
    }
}
