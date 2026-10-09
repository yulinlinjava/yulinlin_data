package com.yulinlin.data.core.cache;

import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.JoinTableList;
import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.node.from.From;
import com.yulinlin.data.core.node.from.Join;
import com.yulinlin.data.core.node.from.Store;
import com.yulinlin.data.core.wrapper.impl.AbstractDeleteWrapper;
import com.yulinlin.data.core.wrapper.impl.AbstractGroupWrapper;
import com.yulinlin.data.core.wrapper.impl.AbstractInsertWrapper;
import com.yulinlin.data.core.wrapper.impl.AbstractSelectWrapper;
import com.yulinlin.data.core.wrapper.impl.AbstractUpdateWrapper;
import com.yulinlin.data.core.wrapper.ICountWrapper;
import com.yulinlin.data.lang.reflection.AnnotationUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Extracts table/index/collection dependencies from framework nodes and entity annotations. */
public final class CacheNamespaceResolver {

    private CacheNamespaceResolver() {
    }

    public static Set<CacheNamespace> resolve(Class<?> sessionType,
                                              String group,
                                              Class<?> fromClass,
                                              Collection<? extends INode> nodes,
                                              Collection<String> explicitResources) {
        LinkedHashSet<String> resources = new LinkedHashSet<>();
        if (nodes != null) for (INode node : nodes) collectNode(node, resources);
        collectAnnotations(fromClass, resources);
        if (explicitResources != null) {
            for (String resource : explicitResources) if (resource != null && !resource.isBlank()) resources.add(resource);
        }

        LinkedHashSet<CacheNamespace> namespaces = new LinkedHashSet<>();
        for (String resource : resources) namespaces.add(CacheNamespace.of(sessionType, group, resource));
        return Collections.unmodifiableSet(namespaces);
    }

    public static Set<CacheNamespace> resolve(Class<?> sessionType,
                                              String group,
                                              Class<?> fromClass,
                                              INode node,
                                              Collection<String> explicitResources) {
        return resolve(sessionType, group, fromClass,
                node == null ? java.util.List.of() : java.util.List.of(node), explicitResources);
    }

    private static void collectNode(INode node, Set<String> resources) {
        if (node instanceof ICountWrapper wrapper) collectNode(wrapper.getWrapper(), resources);
        else if (node instanceof AbstractSelectWrapper<?, ?, ?, ?> wrapper) collectFrom(wrapper.getFrom(), resources);
        else if (node instanceof AbstractGroupWrapper<?, ?, ?, ?, ?> wrapper) collectFrom(wrapper.getFrom(), resources);
        else if (node instanceof AbstractInsertWrapper<?, ?, ?> wrapper) collectFrom(wrapper.getFrom(), resources);
        else if (node instanceof AbstractUpdateWrapper<?, ?, ?, ?> wrapper) collectFrom(wrapper.getFrom(), resources);
        else if (node instanceof AbstractDeleteWrapper<?, ?, ?> wrapper) collectFrom(wrapper.getFrom(), resources);
        else if (node instanceof From from) collectFrom(from, resources);
    }

    private static void collectFrom(From from, Set<String> resources) {
        if (from instanceof Store store) {
            if (store.getName() != null && !store.getName().isBlank()) resources.add(store.getName());
        } else if (from instanceof Join join) {
            collectFrom(join.getLeft(), resources);
            collectFrom(join.getRight(), resources);
        }
    }

    private static void collectAnnotations(Class<?> type, Set<String> resources) {
        if (type == null || type == Object.class) return;
        JoinTableList list = AnnotationUtil.findAnnotation(type, JoinTableList.class);
        if (list != null) for (JoinTable table : list.value()) collectTable(table, resources);
        JoinTable table = AnnotationUtil.findAnnotation(type, JoinTable.class);
        if (table != null) collectTable(table, resources);
    }

    private static void collectTable(JoinTable table, Set<String> resources) {
        if (!table.value().isBlank()) resources.add(table.value());
        if (!table.left().isBlank()) resources.add(table.left());
        if (!table.right().isBlank()) resources.add(table.right());
    }
}
