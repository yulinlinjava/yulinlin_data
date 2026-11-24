package com.yulinlin.elasticsearch.parse;

import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import java.util.List;

/**
 * 解析后的 Elasticsearch 原生查询命令，由 {@link EsCommandParse} 产生、ElasticsearchSession 执行。
 *
 * @param kind    search：返回命中文档；aggregate：返回聚合结果；count：返回文档数；mapping：返回索引的字段映射
 * @param index   索引（可以是逗号分隔的多个索引或通配符）
 * @param indices 拆分后的索引列表
 * @param search  kind 为 search / aggregate 时的请求
 * @param count   kind 为 count 时的请求
 */
public record EsCommand(Kind kind, String index, List<String> indices, SearchRequest search, CountRequest count) {
    public enum Kind { SEARCH, AGGREGATE, COUNT, MAPPING }
}
