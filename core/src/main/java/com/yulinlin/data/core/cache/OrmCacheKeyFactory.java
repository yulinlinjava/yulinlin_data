package com.yulinlin.data.core.cache;

import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.parse.ParseType;

/** Builds database-independent logical cache keys from ORM request metadata. */
public final class OrmCacheKeyFactory {

    private static final String VERSION = "v2";

    private OrmCacheKeyFactory() {
    }

    public static CacheKey query(String group,
                                 Object cluster,
                                 Class<?> sessionType,
                                 Class<?> entityClass,
                                 Class<?> fromClass,
                                 ParseType parseType,
                                 INode node) {
        Object nodeMaterial = parseType == ParseType.count
                ? CountCacheKeyMaterial.from(node)
                : node;
        return CacheKey.fromMaterials(
                VERSION,
                group,
                cluster,
                sessionType,
                entityClass,
                fromClass,
                parseType,
                nodeMaterial);
    }
}
