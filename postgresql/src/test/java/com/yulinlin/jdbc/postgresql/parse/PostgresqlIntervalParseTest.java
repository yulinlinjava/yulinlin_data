package com.yulinlin.jdbc.postgresql.parse;

import com.yulinlin.data.core.node.group.IntervalGroup;
import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.jdbc.postgresql.PostgresqlParseManager;
import com.yulinlin.jdbc.postgresql.coder.PostgresqlCoderManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PostgresqlIntervalParseTest {
    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rejectsNonPositiveIntervalsWithoutDatabase(int step) {
        var context = new SimpParamsContext(RequestType.group, Map.of(),
                new PostgresqlCoderManager().createEncoderBuffer(), Object.class, true);
        assertThrows(IllegalArgumentException.class,
                () -> new PostgresqlParseManager().parse(new IntervalGroup("amount", step), context));
    }
}
