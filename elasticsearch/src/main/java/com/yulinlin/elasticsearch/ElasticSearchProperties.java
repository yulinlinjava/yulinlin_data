package com.yulinlin.elasticsearch;

import com.yulinlin.data.core.session.EntitySessionProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("yulinlin.elasticsearch")
public class ElasticSearchProperties extends EntitySessionProperties {
    private String url = "http://localhost:9200";

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        if (url == null || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            throw new IllegalArgumentException("yulinlin.elasticsearch.url must start with http:// or https://");
        }
        this.url = url;
    }
}
