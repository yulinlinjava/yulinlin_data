package com.yulinlin.common.service.impl;

import com.yulinlin.common.domain.TreeEntity;
import com.yulinlin.common.service.ITreeService;

/**
 * @deprecated Implement {@link ITreeService} directly. This class preserves the former extension API.
 */
@Deprecated(since = "3.0", forRemoval = false)
public abstract class TreeImpl<E extends TreeEntity<E>> extends ServiceImpl<E> implements ITreeService<E> {
}
