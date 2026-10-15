package com.yulinlin.common.service.impl2;

import com.yulinlin.common.domain.TreeEntity;
import com.yulinlin.common.service.ITreeService;

/** @deprecated Implement {@link ITreeService} directly. */
@Deprecated(since = "3.0", forRemoval = false)
public abstract class TreeImpl<E extends TreeEntity<E>>
        extends ServiceImpl<E> implements ITreeService<E> {
}
