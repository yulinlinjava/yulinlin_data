package com.yulinlin.jdbc.log;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SqlLogFormatterTest {
    @Test
    void prettifiesTheReportedInsert() {
        assertEquals("insert into user_info (date, upt_time, id, crt_time) values (?, ?, ?, ?)",
                SqlLogFormatter.readable("insert into \"user_info\" ( \"date\" , \"upt_time\" , \"id\" , \"crt_time\" ) values ( ? , ? , ? , ? )", '"'));
    }

    @ParameterizedTest
    @ValueSource(chars = {'"', '`'})
    void simplifiesSimpleNamesIncludingQualifiedNamesButKeepsComplexOnes(char quote) {
        String sql = "select Q用户Q . Q名称Q , QOrder DetailQ , Qa.bQ , QaQQbQ from Quser_infoQ".replace('Q', quote);
        String expected = "select 用户.名称, QOrder DetailQ, Qa.bQ, QaQQbQ from user_info".replace('Q', quote);
        assertEquals(expected, SqlLogFormatter.readable(sql, quote));
    }

    @Test
    void leavesStringContentsEscapesJsonAndQuestionMarksAlone() {
        String literal = "'{\"name\": \"a  b\", \"sql\": \"( ? , ? )\"}'";
        String sql = "select \"payload\" , " + literal + ", 'it''s \"quoted\"', 'C:\\data', E'it\\'s \"quoted\"' from \"t\"";
        assertEquals("select payload, " + literal + ", 'it''s \"quoted\"', 'C:\\data', E'it\\'s \"quoted\"' from t",
                SqlLogFormatter.readable(sql, '"'));
    }

    @Test
    void mysqlDoubleQuotedStringsAreNotStripped() {
        assertEquals("select id, \"a  b\", \"word\" from user_info",
                SqlLogFormatter.readable("select `id`, \"a  b\", \"word\" from `user_info`", '`'));
    }

    @Test
    void protectsCommentsAndDollarQuotedBodies() {
        String sql = "select \"id\", $$\"body\",  'text'$$, $tag$\"another\"$tag$ /* \"outer\" /* \"inner\" */ end */ from \"t\" -- \"tail\"";
        assertEquals("select id, $$\"body\",  'text'$$, $tag$\"another\"$tag$ /* \"outer\" /* \"inner\" */ end */ from t -- \"tail\"",
                SqlLogFormatter.readable(sql, '"'));
        assertEquals("select id # `comment`", SqlLogFormatter.readable("select `id` # `comment`", '`'));
    }

    @Test
    void protectsPrefixesAndUnclosedQuotedFragments() {
        assertEquals("select U&\"name\", 'unterminated \"identifier\"",
                SqlLogFormatter.readable("select U&\"name\", 'unterminated \"identifier\"", '"'));
        assertEquals("select \"unfinished", SqlLogFormatter.readable("select \"unfinished", '"'));
        assertEquals("select $tag$unfinished \"name\"", SqlLogFormatter.readable("select $tag$unfinished \"name\"", '"'));
    }

    @Test
    void representsLiteralControlCharactersWithoutInjectingLogLines() {
        String rendered = SqlLogFormatter.readable("select 'a\n\r\tb\u0000c\u2028d' from \"t\"", '"');
        assertEquals("select 'a\\n\\r\\tb\\u0000c\\u2028d' from t", rendered);
        assertEquals(1, rendered.lines().count());
        assertEquals("select id -- \"comment\"\\nfrom t",
                SqlLogFormatter.readable("select \"id\" -- \"comment\"\nfrom \"t\"", '"'));
    }

    @Test
    void unknownQuotingRulesKeepQuotesAndDoNotInventParameters() {
        assertEquals("select \"id\", `name` from \"t\" where x = ?",
                SqlLogFormatter.readable("select \"id\" , `name` from \"t\" where x = ?", (char) 0));
        assertEquals("", SqlLogFormatter.readable("  \n\t", '"'));
    }
}
