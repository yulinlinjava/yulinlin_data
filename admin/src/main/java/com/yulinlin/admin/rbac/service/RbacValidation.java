package com.yulinlin.admin.rbac.service;

import com.yulinlin.common.domain.po.PagePo;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** HTTP 案例只开放声明的筛选字段；通用 Service CRUD 仍保留给服务端业务使用。 */
final class RbacValidation {
    private RbacValidation() { }

    static void validate(PagePo query, Set<String> sortable) {
        if (query == null || query.getPageNumber() < 1 || query.getPageNumber() > 1000000
                || query.getPageSize() < 1 || query.getPageSize() > 100) {
            throw new IllegalArgumentException("页码必须为正数，每页最多查询100条");
        }
        if ((query.getAnd() != null && !query.getAnd().isEmpty())
                || (query.getOr() != null && !query.getOr().isEmpty())) {
            throw new IllegalArgumentException("请使用查询对象中声明的筛选字段，不开放任意字段条件");
        }
        if (query.getOrderList() != null) {
            for (var order : query.getOrderList()) {
                if (order == null || order.getName() == null || !sortable.contains(order.getName())) {
                    throw new IllegalArgumentException("不支持此排序字段");
                }
            }
        }
    }

    static void validateBatch(List<?> rows) {
        if (rows == null || rows.isEmpty() || rows.size() > 100 || rows.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("一次批量操作必须包含1到100条非空记录");
        }
    }
}
