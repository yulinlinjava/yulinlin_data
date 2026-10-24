package com.yulinlin.elasticsearch.parse.wrapper;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.CountRequest;
import com.yulinlin.data.core.exception.ParseException;
import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.parse.IParamsContext;
import com.yulinlin.data.core.parse.IParse;
import com.yulinlin.data.core.parse.IParseManager;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.wrapper.impl.CountWrapper;
import com.yulinlin.data.core.wrapper.impl.GroupWrapper;
import com.yulinlin.data.core.wrapper.impl.SelectWrapper;

/** Document totals are independent of result fields, ordering and pagination. */
public class EsCountWrapperParse implements IParse<CountWrapper> {
    @Override
    public ParseResult parse(CountWrapper count, IParamsContext params, IParseManager manager) {
        INode node = count.getWrapper();
        while (node instanceof CountWrapper wrapper) node = wrapper.getWrapper();
        if (node instanceof GroupWrapper<?>) {
            throw new UnsupportedOperationException("Elasticsearch 暂不支持分组结果计数；"
                    + "文档总数不等于分组数，请使用原生聚合查询");
        }
        if (!(node instanceof SelectWrapper<?> select)) {
            throw new ParseException("Elasticsearch count 仅支持普通 SelectWrapper："
                    + (node == null ? "null" : node.getClass().getName()));
        }
        String index = (String) manager.parse(select.getFrom(), params);
        if (index == null || index.isBlank()) {
            throw new ParseException("Elasticsearch count 必须指定索引，不能隐式统计全部索引");
        }
        Query query = (Query) manager.parse(select.where(), params);
        CountRequest request = new CountRequest.Builder().index(index).query(query).build();
        return new ParseResult(ParseType.count, request, params);
    }
}
