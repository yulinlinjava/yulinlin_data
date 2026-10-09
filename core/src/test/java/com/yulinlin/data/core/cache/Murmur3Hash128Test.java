package com.yulinlin.data.core.cache;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Murmur3Hash128Test {

    @Test
    void matchesMurmurHash3X64ReferenceVectors() {
        List<Vector> vectors = List.of(
                new Vector("", 0x0000000000000000L, 0x0000000000000000L),
                new Vector("hello", 0xcbd8a7b341bd9b02L, 0x5b1e906a48ae1d19L),
                new Vector("123456789012345", 0x887001aea2afcfd6L, 0x1ec326364f0801b3L),
                new Vector("1234567890123456", 0x4fbe5dc5c0e32cf8L, 0xc0c8e96b60c322c1L),
                new Vector("12345678901234567", 0x748617968026b77eL, 0x291e6386473f7103L),
                new Vector("The quick brown fox jumps over the lazy dog",
                        0xe34bbc7bbc071b6cL, 0x7a433ca9c49a9347L),
                new Vector("中文查询参数-\uD83D\uDE80", 0x09a3b843bee83faaL, 0x95c27d677efe322cL)
        );

        for (Vector vector : vectors) {
            Murmur3Hash128 hasher = new Murmur3Hash128();
            int split = vector.value().length() / 2;
            hasher.putUtf8(vector.value().substring(0, split));
            hasher.putUtf8(vector.value().substring(split));
            Murmur3Hash128.Result actual = hasher.finish();

            assertThat(actual.first()).as(vector.value()).isEqualTo(vector.first());
            assertThat(actual.second()).as(vector.value()).isEqualTo(vector.second());
        }
    }

    private record Vector(String value, long first, long second) {
    }
}
