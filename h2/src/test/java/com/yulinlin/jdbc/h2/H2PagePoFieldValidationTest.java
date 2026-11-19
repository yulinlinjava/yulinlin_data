package com.yulinlin.jdbc.h2;

import com.yulinlin.common.domain.po.OrderItem;
import com.yulinlin.common.domain.po.PagePo;
import com.yulinlin.common.model.AbstractModel;
import com.yulinlin.common.service.ISuperService;
import com.yulinlin.data.core.anno.*;
import com.yulinlin.jdbc.h2.H2UseIndexTest.Fixture;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the public service entry with a disposable local database, never business connections. */
@ResourceLock("SessionUtil.route")
class H2PagePoFieldValidationTest {
    private final PageService service = new PageService();

    static Stream<Arguments> invalidNames() {
        return Stream.of("and", "or", "orderList").flatMap(source -> Stream.of(
                null, "", " ", "unknown", "ID", " id", "id ", "project_code", "minute_at",
                "r.id", "`id`", "\"id\"", "*", "${id}", "id + 1", "id desc, (select 1)",
                "id = 1 OR 1=1 --", "id; select 1", "CONSTANT", "getterOnly")
                .map(name -> Arguments.of(source, name)));
    }

    @ParameterizedTest @MethodSource("invalidNames")
    void rejectsNamesOutsideEntityFieldsBeforeExecutingAnySql(String source, String name) throws Exception {
        try (var f = new Fixture("")) {
            var po = withField(source, name);
            var error = assertThrows(IllegalArgumentException.class, () -> service.page(po));
            assertTrue(error.getMessage().contains(source + "[0].name"));
            assertTrue(f.events.isEmpty(), "invalid input must fail before page count or list SQL");
        }
    }

    @Test void inheritedPropertiesAndMappedJavaNamesKeepConditionsSortAndPagination() throws Exception {
        try (var f = new Fixture("")) {
            var po = new ProjectPo(); po.setProjectCode("123");
            po.and("id", ConditionEnum.gte, 2L).and("sourceTime", ConditionEnum.gte, LocalDateTime.of(2026, 10, 24, 11, 0));
            po.setOrderList(List.of(order("id", false))); po.setPageNumber(2); po.setPageSize(3);
            var page = service.page(po);
            assertEquals(7, page.getTotal());
            assertEquals(List.of(5L, 4L, 3L), page.getList().stream().map(PageEntity::getId).toList());
            assertTrue(f.lastSql().getSql().contains("\"minute_at\""));
            assertTrue(f.lastSql().getSql().contains("\"id\" desc"));
            assertTrue(f.lastSql().getList().contains("123"));
        }
    }

    @Test void validOrFieldsAndNullListsStillWork() throws Exception {
        try (var f = new Fixture("")) {
            var po = new PagePo();
            po.or("id", ConditionEnum.eq, 1L).or("id", ConditionEnum.eq, 6L);
            po.setOrderList(List.of(order("id", true)));
            assertEquals(List.of(1L, 6L), service.page(po).getList().stream().map(PageEntity::getId).toList());
            po.setAnd(null); po.setOr(null); po.setOrderList(null);
            assertEquals(10, service.page(po).getTotal());
        }
    }

    @Test void sqlLikeValuesRemainBoundParameters() throws Exception {
        try (var f = new Fixture("")) {
            String value = "123' OR 1=1 --";
            var po = new PagePo().and("projectCode", ConditionEnum.eq, value);
            assertEquals(0, service.page(po).getTotal());
            assertFalse(f.events.isEmpty());
            for (var event : f.events) {
                for (var request : event.requests()) {
                    var sql = (com.yulinlin.jdbc.session.SqlNode) request.getRequest();
                    assertFalse(sql.getSql().contains(value));
                    assertTrue(sql.getList().contains(value));
                }
            }
        }
    }

    @Test void nullEntriesAndMissingOperatorsFailBeforeSql() throws Exception {
        try (var f = new Fixture("")) {
            var and = new PagePo(); and.setAnd(Arrays.asList((PagePo.Field) null));
            var or = new PagePo(); or.setOr(Arrays.asList((PagePo.Field) null));
            var sort = new PagePo(); sort.setOrderList(Arrays.asList((OrderItem) null));
            var condition = new PagePo().and("id", null, 1);
            for (var po : List.of(and, or, sort, condition)) {
                assertThrows(IllegalArgumentException.class, () -> service.page(po));
            }
            assertTrue(f.events.isEmpty());
        }
    }

    private static PagePo withField(String source, String name) {
        var po = new PagePo();
        switch (source) {
            case "and" -> po.and(name, ConditionEnum.eq, 1);
            case "or" -> po.or(name, ConditionEnum.eq, 1);
            case "orderList" -> po.setOrderList(List.of(order(name, true)));
            default -> throw new AssertionError(source);
        }
        return po;
    }

    private static OrderItem order(String name, boolean asc) {
        var item = new OrderItem(); item.setName(name); item.setAsc(asc); return item;
    }

    @Data public static class BaseEntity {
        @JoinMeta(primaryKey = true) private Long id;
    }
    @Data @JoinTable("repro_minute") public static class PageEntity extends BaseEntity implements AbstractModel<PageEntity> {
        public static final String CONSTANT = "not a query field";
        private String projectCode;
        @JoinField(name = "minute_at") private LocalDateTime sourceTime;
        public String getGetterOnly() { return "not a field"; }
    }
    @Data public static class ProjectPo extends PagePo {
        @JoinWhere private String projectCode;
    }
    public static class PageService implements ISuperService<PageEntity> { }
}
