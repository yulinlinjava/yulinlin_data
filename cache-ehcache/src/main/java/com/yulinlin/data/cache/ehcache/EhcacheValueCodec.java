package com.yulinlin.data.cache.ehcache;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import com.esotericsoftware.kryo.kryo5.Kryo;
import com.esotericsoftware.kryo.kryo5.io.Input;
import com.esotericsoftware.kryo.kryo5.io.Output;
import com.esotericsoftware.kryo.kryo5.util.Pool;
import com.yulinlin.data.core.cache.CacheValueType;

import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Encodes one cache value and its TTL into a self-describing byte envelope. */
final class EhcacheValueCodec {

    private static final int MAGIC = 0x594C4531; // YLE1
    private static final int HEADER_SIZE = Integer.BYTES + 1 + Long.BYTES + Integer.BYTES;
    private static final byte JSON = 1;
    private static final byte SMILE = 2;
    // Serializer id 3 is reserved by a removed legacy codec. Do not reuse persisted format ids.
    private static final byte KRYO = 4;
    private static final int KRYO_POOL_CAPACITY = 32;

    private final EhcacheValueSerializer writeSerializer;
    private final ObjectMapper json;
    private final ObjectMapper smile;
    private volatile Pool<Kryo> kryos;
    private final Map<WriterKey, ObjectWriter> writers = new ConcurrentHashMap<>();
    private final Map<ReaderKey, ObjectReader> readers = new ConcurrentHashMap<>();

    EhcacheValueCodec(ObjectMapper configuredMapper, EhcacheValueSerializer writeSerializer) {
        ObjectMapper base = configuredMapper == null ? new ObjectMapper() : configuredMapper;
        this.json = optimized(base.copy());
        this.smile = optimized(base.copyWith(new SmileFactory()));
        this.writeSerializer = writeSerializer;
        if (writeSerializer == EhcacheValueSerializer.KRYO) kryos();
    }

    byte[] encode(Object value, Duration ttl) throws Exception {
        byte[] body = switch (writeSerializer) {
            case KRYO -> encodeKryo(value);
            case JSON, SMILE -> encodeJackson(value, writeSerializer);
        };
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + body.length);
        buffer.putInt(MAGIC);
        buffer.put(id(writeSerializer));
        buffer.putLong(ttl.getSeconds());
        buffer.putInt(ttl.getNano());
        buffer.put(body);
        return buffer.array();
    }

    Decoded decode(byte[] value, CacheValueType valueType) throws Exception {
        Envelope envelope = envelope(value);
        if (envelope.serializer() == EhcacheValueSerializer.KRYO) {
            Object decoded = decodeKryo(value);
            validate(decoded, valueType);
            return new Decoded(decoded, envelope.ttl());
        }
        ObjectMapper mapper = mapper(envelope.serializer());
        JavaType javaType = valueType.collection()
                ? mapper.getTypeFactory().constructCollectionType(List.class, valueType.valueClass())
                : mapper.getTypeFactory().constructType(valueType.valueClass());
        return decodeJackson(value, envelope, javaType);
    }

    Decoded decode(byte[] value, Type valueType) throws Exception {
        Envelope envelope = envelope(value);
        if (envelope.serializer() == EhcacheValueSerializer.KRYO) {
            return new Decoded(decodeKryo(value), envelope.ttl());
        }
        ObjectMapper mapper = mapper(envelope.serializer());
        return decodeJackson(value, envelope, mapper.getTypeFactory().constructType(valueType));
    }

    static Duration ttl(byte[] value) {
        return envelope(value).ttl();
    }

    private byte[] encodeJackson(Object value, EhcacheValueSerializer serializer) throws Exception {
        ObjectMapper mapper = mapper(serializer);
        JavaType type = value == null
                ? mapper.getTypeFactory().constructType(Object.class)
                : mapper.getTypeFactory().constructType(value.getClass());
        ObjectWriter writer = writers.computeIfAbsent(new WriterKey(serializer, type),
                ignored -> mapper.writerFor(type));
        return writer.writeValueAsBytes(value);
    }

    private Decoded decodeJackson(byte[] value, Envelope envelope, JavaType javaType) throws Exception {
        ObjectMapper mapper = mapper(envelope.serializer());
        ObjectReader reader = readers.computeIfAbsent(new ReaderKey(envelope.serializer(), javaType),
                ignored -> mapper.readerFor(javaType));
        Object decoded = reader.readValue(value, HEADER_SIZE, value.length - HEADER_SIZE);
        return new Decoded(decoded, envelope.ttl());
    }

    private byte[] encodeKryo(Object value) {
        Pool<Kryo> pool = kryos();
        Kryo kryo = pool.obtain();
        try {
            useContextClassLoader(kryo);
            Output output = new Output(4096, -1);
            kryo.writeClassAndObject(output, value);
            return output.toBytes();
        } finally {
            kryo.reset();
            pool.free(kryo);
        }
    }

    private Object decodeKryo(byte[] value) {
        Pool<Kryo> pool = kryos();
        Kryo kryo = pool.obtain();
        try {
            useContextClassLoader(kryo);
            Input input = new Input(value, HEADER_SIZE, value.length - HEADER_SIZE);
            return kryo.readClassAndObject(input);
        } finally {
            kryo.reset();
            pool.free(kryo);
        }
    }

    private Pool<Kryo> kryos() {
        Pool<Kryo> current = kryos;
        if (current != null) return current;
        synchronized (this) {
            if (kryos == null) {
                kryos = new Pool<>(true, false, KRYO_POOL_CAPACITY) {
                    @Override
                    protected Kryo create() {
                        Kryo kryo = new Kryo();
                        kryo.setRegistrationRequired(false);
                        kryo.setReferences(true);
                        return kryo;
                    }
                };
            }
            return kryos;
        }
    }

    private static void useContextClassLoader(Kryo kryo) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader != null) kryo.setClassLoader(loader);
    }

    private static Envelope envelope(byte[] value) {
        if (value == null || value.length < HEADER_SIZE) {
            throw new IllegalArgumentException("Ehcache value has no Yulinlin envelope");
        }
        ByteBuffer buffer = ByteBuffer.wrap(value);
        if (buffer.getInt() != MAGIC) throw new IllegalArgumentException("Unknown Ehcache value format");
        EhcacheValueSerializer serializer = serializer(buffer.get());
        Duration ttl = Duration.ofSeconds(buffer.getLong(), buffer.getInt());
        if (ttl.isZero() || ttl.isNegative()) throw new IllegalArgumentException("Ehcache value TTL is invalid");
        return new Envelope(serializer, ttl);
    }

    private ObjectMapper mapper(EhcacheValueSerializer serializer) {
        return switch (serializer) {
            case JSON -> json;
            case SMILE -> smile;
            case KRYO -> throw new IllegalArgumentException("Kryo does not use a Jackson mapper");
        };
    }

    private static ObjectMapper optimized(ObjectMapper mapper) {
        mapper.registerModule(new BlackbirdModule());
        return mapper;
    }

    private static byte id(EhcacheValueSerializer serializer) {
        return switch (serializer) {
            case JSON -> JSON;
            case SMILE -> SMILE;
            case KRYO -> KRYO;
        };
    }

    private static EhcacheValueSerializer serializer(byte id) {
        return switch (id) {
            case JSON -> EhcacheValueSerializer.JSON;
            case SMILE -> EhcacheValueSerializer.SMILE;
            case KRYO -> EhcacheValueSerializer.KRYO;
            default -> throw new IllegalArgumentException("Unknown Ehcache value serializer: " + id);
        };
    }

    private static void validate(Object value, CacheValueType valueType) {
        if (value == null) return;
        if (valueType.collection()) {
            if (!(value instanceof List<?>)) {
                throw new IllegalArgumentException("Ehcache value is not a List");
            }
            return;
        }
        if (!valueType.valueClass().isInstance(value)) {
            throw new IllegalArgumentException("Ehcache value type is " + value.getClass().getName()
                    + ", expected " + valueType.valueClass().getName());
        }
    }

    record Decoded(Object value, Duration ttl) {
    }

    private record Envelope(EhcacheValueSerializer serializer, Duration ttl) {
    }

    private record WriterKey(EhcacheValueSerializer serializer, JavaType type) {
    }

    private record ReaderKey(EhcacheValueSerializer serializer, JavaType type) {
    }
}
