package com.yulinlin.jdbc.postgresql;

import java.util.Objects;
import java.util.regex.Pattern;

import com.yulinlin.data.core.session.HighlightProperties;

/** Validated immutable PostgreSQL full-text settings shared by schema and query generation. */
public record PostgresqlFullTextOptions(
        String indexConfig,
        String queryConfig,
        String highlightStart,
        String highlightEnd,
        int highlightMaxWords,
        int highlightMinWords,
        int highlightMaxFragments,
        String highlightFragmentDelimiter) {

    private static final Pattern CONFIG = Pattern.compile(
            "[\\p{L}_][\\p{L}\\p{N}_$]*(?:\\.[\\p{L}_][\\p{L}\\p{N}_$]*)?");

    public PostgresqlFullTextOptions {
        indexConfig = validConfig(indexConfig, "index-config");
        queryConfig = validConfig(queryConfig, "query-config");
        highlightStart = validText(highlightStart, "highlight.start", false);
        highlightEnd = validText(highlightEnd, "highlight.end", false);
        highlightFragmentDelimiter = validText(highlightFragmentDelimiter,
                "highlight.fragment-delimiter", true);
        if (highlightMaxWords <= 0) throw invalid("highlight.max-words must be greater than 0");
        if (highlightMinWords < 0 || highlightMinWords > highlightMaxWords) {
            throw invalid("highlight.min-words must be between 0 and max-words");
        }
        if (highlightMaxFragments <= 0) throw invalid("highlight.max-fragments must be greater than 0");
    }

    public static PostgresqlFullTextOptions defaults() {
        return from(new PostgresqlProperties());
    }

    public static PostgresqlFullTextOptions from(PostgresqlProperties properties) {
        Objects.requireNonNull(properties, "properties");
        PostgresqlProperties.FullText fullText = Objects.requireNonNullElseGet(
                properties.getFullText(), PostgresqlProperties.FullText::new);
        HighlightProperties highlight = Objects.requireNonNullElseGet(
                properties.getHighlight(), HighlightProperties::new);
        return new PostgresqlFullTextOptions(fullText.getIndexConfig(), fullText.getQueryConfig(),
                highlight.getStartTag(), highlight.getEndTag(), fullText.getMaxWords(), fullText.getMinWords(),
                highlight.getMaxFragments(), highlight.getFragmentDelimiter());
    }

    public String indexConfigLiteral() { return sqlLiteral(indexConfig); }
    public String queryConfigLiteral() { return sqlLiteral(queryConfig); }

    public String headlineOptions() {
        return "StartSel=" + option(highlightStart)
                + ", StopSel=" + option(highlightEnd)
                + ", MaxWords=" + highlightMaxWords
                + ", MinWords=" + highlightMinWords
                + ", MaxFragments=" + highlightMaxFragments
                + ", FragmentDelimiter=" + option(highlightFragmentDelimiter);
    }

    private static String validConfig(String value, String name) {
        if (value == null || !CONFIG.matcher(value).matches()) {
            throw invalid(name + " must be a PostgreSQL text-search configuration name");
        }
        return value;
    }

    private static String validText(String value, String name, boolean allowEmpty) {
        if (value == null || (!allowEmpty && value.isEmpty()) || value.length() > 256
                || value.chars().anyMatch(character -> Character.isISOControl(character))) {
            throw invalid(name + " must be " + (allowEmpty ? "a" : "a non-empty")
                    + " text value up to 256 characters without control characters");
        }
        return value;
    }

    private static String option(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String sqlLiteral(String value) { return "'" + value.replace("'", "''") + "'"; }
    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Invalid yulinlin.postgresql.full-text configuration: " + message);
    }
}
