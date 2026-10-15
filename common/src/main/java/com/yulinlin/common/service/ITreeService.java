package com.yulinlin.common.service;

import com.yulinlin.common.domain.ISortValue;
import com.yulinlin.common.domain.TreeEntity;
import com.yulinlin.common.model.ModelDeleteWrapper;
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.common.model.ModelUpdateWrapper;
import com.yulinlin.common.util.TreeUtil;
import com.yulinlin.data.core.exception.NoticeException;
import com.yulinlin.data.lang.util.ListString;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Stateless tree CRUD mixin for {@link TreeEntity}. */
public interface ITreeService<E extends TreeEntity<E>> extends ISuperService<E> {

    @Override
    default int insertList(List<E> coll) {
        Objects.requireNonNull(coll, "coll");
        for (E entity : coll) normalizeParent(entity);
        return ISuperService.super.insertList(coll);
    }

    @Override
    default int saveList(List<E> coll) {
        Objects.requireNonNull(coll, "coll");
        for (E entity : coll) normalizeParent(entity);
        return ISuperService.super.saveList(coll);
    }

    @Override
    default int deleteByIdIn(List<String> ids) {
        Objects.requireNonNull(ids, "ids");
        if (ids.isEmpty()) return 0;
        for (E entity : findByIdIn(ids)) {
            ListString<String> descendantPath = copyPath(entity.getParentPath());
            descendantPath.add(entity.getId());
            ModelDeleteWrapper.newInstance(getEntityClass())
                    .likeRight("parentPath", descendantPath.encode())
                    .execute();
        }
        return ISuperService.super.deleteByIdIn(ids);
    }

    @Override
    default int updateOne(E obj) {
        Objects.requireNonNull(obj, "obj");
        if (obj.getId() == null || obj.getId().isEmpty()) {
            throw new NoticeException("树节点更新缺少主键");
        }
        E old = findById(obj.getId());
        if (old == null) throw new NoticeException("树节点不存在: " + obj.getId());

        normalizeParent(obj);
        int total = ISuperService.super.updateOne(obj);
        if (!Objects.equals(obj.getParentId(), old.getParentId())) {
            List<E> changed = new ArrayList<>();
            collectChangedChildren(obj, changed);
            if (!changed.isEmpty()) ModelUpdateWrapper.newInstance(changed).batch().execute();
        }
        return total;
    }

    /** Queries flat rows and assembles them into an in-memory tree. */
    default List<E> getTree(Object query) {
        List<E> tree = TreeUtil.buildTree(
                ModelSelectWrapper.newInstance(getEntityClass(), query).selectList());
        if (ISortValue.class.isAssignableFrom(getEntityClass())) {
            Comparator<Integer> comparator = Comparator.nullsLast(Integer::compareTo);
            TreeUtil.sort(tree, (left, right) -> comparator.compare(
                    ((ISortValue) left).getSortValue(), ((ISortValue) right).getSortValue()));
        }
        return tree;
    }

    private void normalizeParent(E entity) {
        Objects.requireNonNull(entity, "entity");
        ListString<String> path = entity.getParentPath();
        if (path == null || path.isEmpty()) {
            entity.setParentId(TreeEntity.root);
            entity.setParentPath(new ListString<>());
            return;
        }
        if (entity.getId() != null && path.contains(entity.getId())) {
            throw new NoticeException("树节点不能移动到自己或自己的后代节点: " + entity.getId());
        }
        entity.setParentId(path.get(path.size() - 1));
    }

    private void collectChangedChildren(E node, List<E> changed) {
        ListString<String> path = copyPath(node.getParentPath());
        path.add(node.getId());
        List<E> children = node.findChildren();
        for (E child : children) {
            child.setParentPath(new ListString<>(path));
            child.setParentId(node.getId());
            collectChangedChildren(child, changed);
        }
        changed.addAll(children);
    }

    private ListString<String> copyPath(ListString<String> source) {
        return source == null ? new ListString<>() : new ListString<>(source);
    }
}
