package com.yulinlin.regression.coder;

import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.coder.impl.AtomicBooleanCoder;
import com.yulinlin.data.core.coder.impl.BooleanCoder;
import com.yulinlin.jdbc.coder.JdbcCoderManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** 布尔转换源码回归，不启动应用或连接数据库。 */
class BooleanCoderTest {
    private final BooleanCoder coder = new BooleanCoder();
    private final AtomicBooleanCoder atomicCoder = new AtomicBooleanCoder();

    static Stream<Arguments> booleanValues() {
        return Stream.of(
                Arguments.of(false, false), Arguments.of(true, true),
                Arguments.of("false", false), Arguments.of("FALSE", false),
                Arguments.of(" FaLsE ", false), Arguments.of("TRUE", true),
                Arguments.of("f", false), Arguments.of("T", true),
                Arguments.of('f', false), Arguments.of('t', true),
                Arguments.of("0", false), Arguments.of(" 0 ", false),
                Arguments.of("0.0", false), Arguments.of("-0.0", false),
                Arguments.of("1", true), Arguments.of("-1", true),
                Arguments.of("0.5", true), Arguments.of("1E-1000", true),
                Arguments.of((byte) 0, false), Arguments.of((short) -1, true),
                Arguments.of(0, false), Arguments.of(-1, true),
                Arguments.of(4294967296L, true),
                Arguments.of(0.0f, false), Arguments.of(-0.0d, false),
                Arguments.of(0.5f, true), Arguments.of(-0.5d, true),
                Arguments.of(Double.MIN_VALUE, true),
                Arguments.of(BigInteger.ZERO, false),
                Arguments.of(BigInteger.ONE.shiftLeft(64), true),
                Arguments.of(new BigDecimal("0.00"), false),
                Arguments.of(new BigDecimal("-1E-1000"), true),
                Arguments.of(new AtomicInteger(0), false),
                Arguments.of(new AtomicInteger(-1), true)
        );
    }

    @ParameterizedTest
    @MethodSource("booleanValues")
    void fieldClassAndAtomicEntrypointsAgree(Object value, boolean expected) throws Exception {
        Field field = Fields.class.getDeclaredField("enabled");
        assertEquals(expected, coder.decode(null, field, value));
        assertEquals(expected, coder.decode(null, Boolean.class, value));
        assertEquals(expected, coder.decode(null, boolean.class, value));
        assertEquals(expected, atomicCoder.decode(null, AtomicBoolean.class, value).get());
        assertEquals(expected, atomicCoder.decode(null, Fields.class.getDeclaredField("atomic"), value).get());
    }

    @Test
    void nullRemainsNullAndEncodingPreservesFalse() {
        assertNull(coder.decode(null, (Field) null, null));
        assertNull(coder.decode(null, Boolean.class, null));
        assertNull(atomicCoder.decode(null, (Field) null, null));
        assertNull(atomicCoder.decode(null, AtomicBoolean.class, null));
        assertEquals(Boolean.FALSE, coder.encode(null, "enabled", false));
        assertEquals(Boolean.FALSE, atomicCoder.encode(null, "enabled", new AtomicBoolean(false)));
    }

    @Test
    void invalidValuesAreNotSilentlyEnabledOrDisabled() {
        for (Object value : new Object[]{"", " ", "unknown", "null", "NaN", "Infinity",
                Double.NaN, Double.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, new Object()}) {
            assertThrows(IllegalArgumentException.class, () -> coder.decode(null, Boolean.class, value));
            assertThrows(IllegalArgumentException.class, () -> atomicCoder.decode(null, AtomicBoolean.class, value));
        }
    }

    @Test
    void registeredJdbcBufferPreservesFalseAcrossReadAndWrite() throws Exception {
        JdbcCoderManager manager = new JdbcCoderManager();
        assertInstanceOf(BooleanCoder.class, manager.getCoder(Boolean.class));
        assertInstanceOf(BooleanCoder.class, manager.getCoder(boolean.class));

        IDataBuffer decoder = manager.createDecoderBuffer();
        decoder.put("enabled", "FALSE");
        assertSame(Boolean.FALSE, decoder.getObject("enabled", Fields.class.getDeclaredField("enabled")));
        assertSame(Boolean.FALSE, decoder.getObject("enabled", Fields.class.getDeclaredField("primitive")));
        assertSame(Boolean.FALSE, decoder.getObject("enabled", Boolean.class));
        AtomicBoolean atomic = decoder.getObject("enabled", AtomicBoolean.class);
        assertFalse(atomic.get());

        IDataBuffer encoder = manager.createEncoderBuffer();
        encoder.put("enabled", false);
        assertSame(Boolean.FALSE, encoder.getObject("enabled"));
    }

    private static class Fields {
        Boolean enabled;
        boolean primitive;
        AtomicBoolean atomic;
    }
}
