package com.yulinlin.admin.jdbc;

import com.yulinlin.data.core.coder.ICoder;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.core.wrapper.impl.UpdateWrapper;
import com.yulinlin.data.lang.util.Coin;
import com.yulinlin.data.lang.util.Money;
import com.yulinlin.jdbc.coder.JdbcCoderManager;
import com.yulinlin.jdbc.session.JdbcSession;
import com.yulinlin.jdbc.session.SqlNode;
import com.yulinlin.jdbc.sql.SqlParseManager;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ArithmeticParameterEncodingTest {
    static Stream<Arguments> currencies() {
        return Stream.of(
                Arguments.of(Money.of(12.5), 1250L),
                Arguments.of(Money.of(-2.5), -250L),
                Arguments.of(Money.of(0), 0L),
                Arguments.of(Coin.of(12.5), 125L),
                Arguments.of(Coin.of(-2.5), -25L),
                Arguments.of(Coin.of(0), 0L));
    }

    @ParameterizedTest
    @MethodSource("currencies")
    void currencyIncrementAndDecrementUsesStorageUnits(Number amount, long encoded) {
        for (boolean decrement : List.of(false, true)) {
            SqlNode node = (SqlNode) update(amount, decrement).getRequest();
            assertEquals(encoded, node.getList().getFirst());
            assertEquals(Long.class, node.getList().getFirst().getClass());
            assertTrue(node.getSql().replaceAll("\\s+", " ").contains(decrement ? " - ?" : " + ?"));
        }
    }

    static Stream<Arguments> nativeNumbers() {
        BigInteger integer = new BigInteger("123456789012345678901234567890");
        return Stream.of(
                Arguments.of((byte) 2, (byte) 2),
                Arguments.of((short) 3, (short) 3),
                Arguments.of(4, 4),
                Arguments.of(5L, 5L),
                Arguments.of(1.25f, 1.25f),
                Arguments.of(2.5d, 2.5d),
                Arguments.of(new BigDecimal("12345678901234567890.12345678"),
                        new BigDecimal("12345678901234567890.12345678")),
                Arguments.of(integer, new BigDecimal(integer)));
    }

    @ParameterizedTest
    @MethodSource("nativeNumbers")
    void nativeNumbersStayNumericAndKeepPrecision(Number value, Number expected) {
        SimpParamsContext context = context(new JdbcCoderManager());
        String key = context.putNumberGetKey(value);
        Object encoded = parameter(context, key);
        assertEquals(expected, encoded);
        assertEquals(expected.getClass(), encoded.getClass());
    }

    @Test
    void mutableNumbersAreEncodedAsScalarSnapshots() {
        AtomicInteger integer = new AtomicInteger(3);
        AtomicLong longer = new AtomicLong(4);
        LongAdder longAdder = new LongAdder();
        longAdder.add(5);
        DoubleAdder doubleAdder = new DoubleAdder();
        doubleAdder.add(6.25);
        SimpParamsContext context = context(new JdbcCoderManager());
        String first = context.putNumberGetKey(integer);
        String second = context.putNumberGetKey(longer);
        String third = context.putNumberGetKey(longAdder);
        String fourth = context.putNumberGetKey(doubleAdder);
        integer.incrementAndGet();
        longer.incrementAndGet();
        longAdder.increment();
        doubleAdder.add(1);
        assertEquals(3, parameter(context, first));
        assertEquals(4L, parameter(context, second));
        assertEquals(5L, parameter(context, third));
        assertEquals(6.25d, parameter(context, fourth));
    }

    @Test
    void customNumbersUseTheRegisteredCoder() {
        BigInteger encoded = new BigInteger("123456789012345678901234567890");
        JdbcCoderManager coders = new JdbcCoderManager();
        coders.registerCoder(new CustomNumberCoder(encoded));
        SimpParamsContext context = context(coders);
        assertEquals(new BigDecimal(encoded), parameter(context, context.putNumberGetKey(new CustomNumber())));
    }

    @Test
    void invalidCustomCoderResultsFailBeforeJdbcBinding() {
        for (Object encoded : new Object[]{null, "12", new byte[]{1}, new CustomNumber()}) {
            JdbcCoderManager coders = new JdbcCoderManager();
            coders.registerCoder(new CustomNumberCoder(encoded));
            SimpParamsContext context = context(coders);
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> context.putNumberGetKey(new CustomNumber()));
            assertTrue(error.getMessage().contains(CustomNumber.class.getName()));
            assertTrue(context.getDataBuffer().toMap().isEmpty());
        }
    }

    @Test
    void normalSetStillUsesTheStorageCoder() {
        SimpParamsContext context = context(new JdbcCoderManager());
        assertEquals(1250L, parameter(context, context.putGetKey(Money.of(12.5))));
        assertEquals("12.50", parameter(context, context.putGetKey(new BigDecimal("12.50"))));
        assertEquals("12345678901234567890",
                parameter(context, context.putGetKey(new BigInteger("12345678901234567890"))));
    }

    @Test
    void jdbcBatchAddsAndSubtractsMoneyAndCoinWithoutSerializingObjects() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:arithmetic_" + UUID.randomUUID() + ";MODE=MySQL");
        try (Connection connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE user_info (id BIGINT PRIMARY KEY, gold BIGINT NOT NULL, coins BIGINT NOT NULL)");
            statement.execute("INSERT INTO user_info (id, gold, coins) VALUES (1, 10000, 1000)");
            BatchSession session = new BatchSession(source);
            assertEquals(4, session.execute(connection, List.of(
                    update(Money.of(12.5), false), update(Money.of(2.5), true),
                    update("coins", Coin.of(12.5), false), update("coins", Coin.of(2.5), true))));
            try (var rows = statement.executeQuery("SELECT gold, coins FROM user_info WHERE id = 1")) {
                assertTrue(rows.next());
                assertEquals(11000L, rows.getLong(1));
                assertEquals(1100L, rows.getLong(2));
            }
        }
    }

    @Test
    void jdbcBatchPreservesLargeIntegerAndDecimalArithmetic() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:arithmetic_" + UUID.randomUUID() + ";MODE=MySQL");
        try (Connection connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE user_info (id BIGINT PRIMARY KEY, gold DECIMAL(38, 8) NOT NULL)");
            statement.execute("INSERT INTO user_info (id, gold) VALUES (1, 0)");
            BatchSession session = new BatchSession(source);
            assertEquals(3, session.execute(connection, List.of(
                    update(new BigInteger("12345678901234567890"), false),
                    update(new BigDecimal("0.12345678"), false),
                    update(new BigDecimal("0.00000001"), true))));
            try (var rows = statement.executeQuery("SELECT gold FROM user_info WHERE id = 1")) {
                assertTrue(rows.next());
                assertEquals(new BigDecimal("12345678901234567890.12345677"), rows.getBigDecimal(1));
            }
        }
    }

    private static ParseResult update(Number value, boolean decrement) {
        return update("gold", value, decrement);
    }

    private static ParseResult update(String field, Number value, boolean decrement) {
        UpdateWrapper<Object> wrapper = new UpdateWrapper<>().table("user_info");
        if (decrement) wrapper.fields().dec(field, value);
        else wrapper.fields().inc(field, value);
        wrapper.where().eq("id", 1L);
        return (ParseResult) new SqlParseManager().parse(wrapper, context(new JdbcCoderManager()));
    }

    private static SimpParamsContext context(JdbcCoderManager coders) {
        return new SimpParamsContext(RequestType.update, Map.of(),
                coders.createEncoderBuffer(), Object.class, true);
    }

    private static Object parameter(SimpParamsContext context, String placeholder) {
        return context.getDataBuffer().toMap().get(placeholder.substring(2, placeholder.length() - 1));
    }

    private static final class BatchSession extends JdbcSession {
        private BatchSession(JdbcDataSource source) { super(source); }
        private int execute(Connection connection, List<ParseResult> requests) {
            return executeUpdateNode(connection, requests);
        }
    }

    private static final class CustomNumber extends Number {
        @Override public int intValue() { return 1; }
        @Override public long longValue() { return 1; }
        @Override public float floatValue() { return 1; }
        @Override public double doubleValue() { return 1; }
    }

    private static final class CustomNumberCoder implements ICoder<CustomNumber, Object> {
        private final Object encoded;
        private CustomNumberCoder(Object encoded) { this.encoded = encoded; }
        @Override public Object encode(IDataBuffer buffer, String key, CustomNumber value) { return encoded; }
        @Override public CustomNumber decode(IDataBuffer buffer, Field field, Object value) {
            throw new UnsupportedOperationException();
        }
    }
}
