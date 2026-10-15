package com.yulinlin.common.service;

import com.yulinlin.common.domain.po.OrderItem;
import com.yulinlin.common.domain.po.PagePo;
import com.yulinlin.common.model.AbstractModel;
import com.yulinlin.common.model.ModelDeleteWrapper;
import com.yulinlin.common.model.ModelInsertWrapper;
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.common.model.ModelUpdateWrapper;
import com.yulinlin.data.lang.reflection.GenericUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.data.lang.util.Page;
import org.springframework.aop.support.AopUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Stateless CRUD mixin for common models. Transaction boundaries belong to the concrete service. */
public interface ISuperService<E extends AbstractModel<E>> {

    default E findById(Object id) {
        return ModelSelectWrapper.newInstance(getEntityClass()).eq(id).selectOne();
    }

    default List<E> findByIdIn(List<?> ids) {
        Objects.requireNonNull(ids, "ids");
        if (ids.isEmpty()) return List.of();
        return ModelSelectWrapper.newInstance(getEntityClass()).in(ids).selectList();
    }

    default int insertOne(E obj) {
        return insertList(List.of(Objects.requireNonNull(obj, "obj")));
    }

    default int insertList(List<E> coll) {
        Objects.requireNonNull(coll, "coll");
        if (coll.isEmpty()) return 0;
        return ModelInsertWrapper.newInstance(coll).execute();
    }

    default int deleteById(String id) {
        return deleteByIdIn(List.of(Objects.requireNonNull(id, "id")));
    }

    default int deleteByIdIn(List<String> ids) {
        Objects.requireNonNull(ids, "ids");
        if (ids.isEmpty()) return 0;
        return ModelDeleteWrapper.newInstance(getEntityClass()).in(ids).execute();
    }

    default int updateOne(E obj) {
        return updateList(List.of(Objects.requireNonNull(obj, "obj")));
    }

    default int updateList(List<E> list) {
        Objects.requireNonNull(list, "list");
        if (list.isEmpty()) return 0;
        return ModelUpdateWrapper.newInstance(list).execute();
    }

    default int save(E obj) {
        return saveList(List.of(Objects.requireNonNull(obj, "obj")));
    }

    default int saveList(List<E> coll) {
        Objects.requireNonNull(coll, "coll");
        if (coll.isEmpty()) return 0;
        int total = 0;
        for (Map.Entry<Boolean, List<E>> entry : groupForSave(coll).entrySet()) {
            total += entry.getKey() ? updateList(entry.getValue()) : insertList(entry.getValue());
        }
        return total;
    }

    /**
     * Splits models into existing (true) and new (false) primary keys for {@link #saveList(List)}.
     * Implementations may override this hook when existence is resolved by another strategy.
     */
    default Map<Boolean, List<E>> groupForSave(List<E> coll) {
        List<Object> ids = coll.stream()
                .map(row -> row.primaryKeyValue())
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) return Map.of(false, coll);

        Set<Object> existingIds = new HashSet<>();
        for (E row : findByIdIn(ids)) existingIds.add(row.primaryKeyValue());
        return coll.stream().collect(Collectors.groupingBy(
                row -> existingIds.contains(row.primaryKeyValue())));
    }

    default List<E> all() {
        return ModelSelectWrapper.newInstance(getEntityClass()).selectList();
    }

    default Page<E> page(PagePo po) {
        Objects.requireNonNull(po, "po");
        return ModelSelectWrapper.newInstance(getEntityClass(), po)
                .and(wrapper -> {
                    if (po.getAnd() != null) {
                        for (PagePo.Field field : po.getAnd()) {
                            wrapper.condition(field.getName(), field.getCondition(), field.getValue());
                        }
                    }
                })
                .or(wrapper -> {
                    if (po.getOr() != null) {
                        for (PagePo.Field field : po.getOr()) {
                            wrapper.condition(field.getName(), field.getCondition(), field.getValue());
                        }
                    }
                })
                .page(po.getPageNumber(), po.getPageSize())
                .orderBy(order -> {
                    if (po.getOrderList() != null) {
                        for (OrderItem item : po.getOrderList()) {
                            order.orderBy(item.getName(), item.isAsc());
                        }
                    }
                })
                .selectPage();
    }

    @SuppressWarnings("unchecked")
    default Class<E> getEntityClass() {
        Class<?> serviceType = AopUtils.getTargetClass(this);
        Class<?> type = GenericUtil.getGeneric(serviceType, ISuperService.class, 0);
        if (type == null) {
            throw new IllegalStateException("Cannot resolve service entity type: " + serviceType.getName());
        }
        return (Class<E>) type;
    }

    default E newInstance() {
        return ReflectionUtil.newInstance(getEntityClass());
    }
}
