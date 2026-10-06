package com.yulinlin.elasticsearch;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.schema.SchemaMode;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.wrapper.impl.SelectWrapper;
import com.yulinlin.elasticsearch.coder.ElasticCoderManager;
import com.yulinlin.elasticsearch.parse.ElasticSearchParseManager;
import com.yulinlin.elasticsearch.session.ElasticsearchSession;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticsearchSchemaManagerTest {
    @Test void bindsCommonEntitySessionSettings() {
        var source = new MapConfigurationPropertySource(Map.of(
                "yulinlin.elasticsearch.log", true,
                "yulinlin.elasticsearch.url", "https://search.example.com:9200",
                "yulinlin.elasticsearch.map-underscore-to-camel-case", false,
                "yulinlin.elasticsearch.schema-mode", "validate",
                "yulinlin.elasticsearch.schema-packages[0]", "com.example.search",
                "yulinlin.elasticsearch.highlight.start-tag", "<mark>",
                "yulinlin.elasticsearch.highlight.end-tag", "</mark>",
                "yulinlin.elasticsearch.highlight.max-fragments", 3,
                "yulinlin.elasticsearch.highlight.fragment-delimiter", " | "));

        var properties = new Binder(source).bind("yulinlin.elasticsearch",
                        Bindable.of(ElasticSearchProperties.class))
                .orElseThrow(() -> new AssertionError("Elasticsearch properties were not bound"));

        assertThat(properties.isLog()).isTrue();
        assertThat(properties.getUrl()).isEqualTo("https://search.example.com:9200");
        assertThat(properties.isMapUnderscoreToCamelCase()).isFalse();
        assertThat(properties.getSchemaMode()).isEqualTo(SchemaMode.VALIDATE);
        assertThat(properties.getSchemaPackages()).containsExactly("com.example.search");
        assertThat(properties.getHighlight().getStartTag()).isEqualTo("<mark>");
        assertThat(properties.getHighlight().getEndTag()).isEqualTo("</mark>");
        assertThat(properties.getHighlight().getMaxFragments()).isEqualTo(3);
        assertThat(properties.getHighlight().getFragmentDelimiter()).isEqualTo(" | ");
    }

    @Test void rendersSharedHighlightSettingsWithTheElasticsearch95Client() {
        var properties = new ElasticSearchProperties();
        properties.getHighlight().setStartTag("<mark>");
        properties.getHighlight().setEndTag("</mark>");
        properties.getHighlight().setMaxFragments(3);
        var parser = new ElasticSearchParseManager(properties.getHighlight());
        var query = new SelectWrapper<Video>().table("video_search");
        query.fields().field("title", "title").field("content", "content");
        query.highlight("title").highlight("content");
        query.where().match("title", "Java 性能");
        var context = new SimpParamsContext(RequestType.select, Map.of(),
                new ElasticCoderManager().createEncoderBuffer(), Video.class, true);

        var parsed = (ParseResult) parser.parse(query, context);
        var request = (SearchRequest) parsed.getRequest();

        assertThat(parsed.getType()).isEqualTo(com.yulinlin.data.core.parse.ParseType.select);
        assertThat(request.toString())
                .contains("\"pre_tags\":[\"<mark>\"]")
                .contains("\"post_tags\":[\"</mark>\"]")
                .contains("\"number_of_fragments\":3")
                .contains("\"content\"")
                .contains("\"match\":{\"video_title\"");
    }

    @Test void previewsIndexMappingAndHonorsSharedNamingSetting() {
        var session = new ElasticsearchSession(null);
        var properties = new ElasticSearchProperties();
        properties.setSchemaMode(SchemaMode.NONE);
        session.setSessionProperties(properties);

        assertThat(session.createTableSql(Video.class)).singleElement().asString()
                .contains("PUT /video_search", "\"id\":{\"type\":\"keyword\"}",
                        "\"video_title\":{\"type\":\"text\"}",
                        "\"view_count\":{\"type\":\"long\"}");
        session.initializeSchema(List.of(Video.class)); // NONE must not access the null client.
    }

    @JoinTable(value = "video_search", autoSchema = true)
    static class Video {
        @JoinMeta(primaryKey = true)
        private String id;
        @JoinField(name = "video_title", fullText = true)
        private String title;
        @JoinField(fullText = true)
        private String content;
        private long viewCount;
    }
}
