package com.yulinlin.data.core.cache;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One equality-condition cache bucket inside a table namespace.
 *
 * <p>The value key is deliberately a digest rather than the original value so cache providers do
 * not retain business values in their invalidation metadata. Text is folded to lower case and has
 * surrounding whitespace removed. That normalization may merge two database values into one bucket,
 * which only causes conservative over-invalidation and cannot expose stale data.</p>
 */
public record CachePartition(CacheNamespace namespace, String field, String valueKey) {

    private static final HexFormat HEX = HexFormat.of();
    private static final Pattern SIMPLE_FIELD = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");

    public CachePartition {
        namespace = Objects.requireNonNull(namespace, "namespace");
        field = normalizeField(field);
        valueKey = requireText(valueKey, "valueKey");
    }

    /** Creates a partition only for values with a stable scalar representation. */
    public static Optional<CachePartition> of(CacheNamespace namespace, String field, Object value) {
        String material = scalarMaterial(value);
        if (material == null) return Optional.empty();

        Murmur3Hash128 hasher = new Murmur3Hash128();
        CacheKeyMetadata.write(hasher, "partition-value-v1");
        CacheKeyMetadata.write(hasher, material);
        Murmur3Hash128.Result hash = hasher.finish();
        return Optional.of(new CachePartition(namespace, field,
                HEX.toHexDigits(hash.first()) + HEX.toHexDigits(hash.second())));
    }

    static String normalizeField(String value) {
        String field = requireText(value, "field");
        if (!SIMPLE_FIELD.matcher(field).matches()) {
            throw new IllegalArgumentException("field must be a simple column name");
        }
        return requireText(field, "field").toLowerCase(Locale.ROOT);
    }

    private static String scalarMaterial(Object value) {
        if (value == null) return null;
        if (value instanceof CharSequence text) {
            return "scalar:" + normalizeText(text.toString());
        }
        if (value instanceof Character character) {
            return "scalar:" + normalizeText(Character.toString(character));
        }
        if (value instanceof BigDecimal decimal) {
            return "scalar:" + decimal.stripTrailingZeros().toPlainString();
        }
        if (value instanceof BigInteger integer) return "scalar:" + integer;
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return "scalar:" + value;
        }
        if (value instanceof Float number) {
            return Float.isFinite(number)
                    ? "scalar:" + BigDecimal.valueOf(number.doubleValue()).stripTrailingZeros().toPlainString()
                    : "scalar:" + number.toString().toLowerCase(Locale.ROOT);
        }
        if (value instanceof Double number) {
            return Double.isFinite(number)
                    ? "scalar:" + BigDecimal.valueOf(number).stripTrailingZeros().toPlainString()
                    : "scalar:" + number.toString().toLowerCase(Locale.ROOT);
        }
        if (value instanceof Boolean bool) return "scalar:" + (bool ? "1" : "0");
        return null;
    }

    private static String normalizeText(String value) {
        String text = value.strip().toLowerCase(Locale.ROOT);
        try {
            return new BigDecimal(text).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ignored) {
            return text;
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
