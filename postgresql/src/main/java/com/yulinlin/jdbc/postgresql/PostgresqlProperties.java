package com.yulinlin.jdbc.postgresql;

import com.yulinlin.jdbc.JdbcSessionProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("yulinlin.postgresql")
public class PostgresqlProperties extends JdbcSessionProperties {
    private FullText fullText = new FullText();

    public FullText getFullText() { return fullText; }
    public void setFullText(FullText fullText) { this.fullText = fullText == null ? new FullText() : fullText; }

    public static class FullText {
        private String indexConfig = "jiebacfg";
        private String queryConfig = "jiebaqry";
        private int maxWords = 40;
        private int minWords = 15;

        public String getIndexConfig() { return indexConfig; }
        public void setIndexConfig(String indexConfig) { this.indexConfig = indexConfig; }
        public String getQueryConfig() { return queryConfig; }
        public void setQueryConfig(String queryConfig) { this.queryConfig = queryConfig; }
        public int getMaxWords() { return maxWords; }
        public void setMaxWords(int maxWords) { this.maxWords = maxWords; }
        public int getMinWords() { return minWords; }
        public void setMinWords(int minWords) { this.minWords = minWords; }
    }
}
