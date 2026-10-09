package com.yulinlin.data.core.cache;

/** Incremental MurmurHash3 x64 128-bit implementation with a fixed seed. */
final class Murmur3Hash128 {

    private static final long C1 = 0x87c37b91114253d5L;
    private static final long C2 = 0x4cf5ad432745937fL;

    private final byte[] tail = new byte[16];
    private long h1;
    private long h2;
    private long length;
    private int tailLength;

    Murmur3Hash128() {
        this(0);
    }

    Murmur3Hash128(int seed) {
        long unsignedSeed = seed & 0xffff_ffffL;
        this.h1 = unsignedSeed;
        this.h2 = unsignedSeed;
    }

    void putUtf8(CharSequence value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 0x80) {
                putByte(character);
            } else if (character < 0x800) {
                putByte(0xc0 | character >>> 6);
                putByte(0x80 | character & 0x3f);
            } else if (Character.isHighSurrogate(character)
                    && index + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(index + 1))) {
                int codePoint = Character.toCodePoint(character, value.charAt(++index));
                putByte(0xf0 | codePoint >>> 18);
                putByte(0x80 | codePoint >>> 12 & 0x3f);
                putByte(0x80 | codePoint >>> 6 & 0x3f);
                putByte(0x80 | codePoint & 0x3f);
            } else if (Character.isSurrogate(character)) {
                // Match the replacement byte used by String#getBytes(UTF_8).
                putByte('?');
            } else {
                putByte(0xe0 | character >>> 12);
                putByte(0x80 | character >>> 6 & 0x3f);
                putByte(0x80 | character & 0x3f);
            }
        }
    }

    void putAscii(char value) {
        putByte(value);
    }

    void putLong(long value) {
        putByte((int) value);
        putByte((int) (value >>> 8));
        putByte((int) (value >>> 16));
        putByte((int) (value >>> 24));
        putByte((int) (value >>> 32));
        putByte((int) (value >>> 40));
        putByte((int) (value >>> 48));
        putByte((int) (value >>> 56));
    }

    Result finish() {
        long finalH1 = h1;
        long finalH2 = h2;
        long k1 = 0;
        long k2 = 0;

        switch (tailLength) {
            case 15: k2 ^= unsigned(tail[14]) << 48;
            case 14: k2 ^= unsigned(tail[13]) << 40;
            case 13: k2 ^= unsigned(tail[12]) << 32;
            case 12: k2 ^= unsigned(tail[11]) << 24;
            case 11: k2 ^= unsigned(tail[10]) << 16;
            case 10: k2 ^= unsigned(tail[9]) << 8;
            case 9:
                k2 ^= unsigned(tail[8]);
                k2 *= C2;
                k2 = Long.rotateLeft(k2, 33);
                k2 *= C1;
                finalH2 ^= k2;
            case 8: k1 ^= unsigned(tail[7]) << 56;
            case 7: k1 ^= unsigned(tail[6]) << 48;
            case 6: k1 ^= unsigned(tail[5]) << 40;
            case 5: k1 ^= unsigned(tail[4]) << 32;
            case 4: k1 ^= unsigned(tail[3]) << 24;
            case 3: k1 ^= unsigned(tail[2]) << 16;
            case 2: k1 ^= unsigned(tail[1]) << 8;
            case 1:
                k1 ^= unsigned(tail[0]);
                k1 *= C1;
                k1 = Long.rotateLeft(k1, 31);
                k1 *= C2;
                finalH1 ^= k1;
            default:
                break;
        }

        finalH1 ^= length;
        finalH2 ^= length;
        finalH1 += finalH2;
        finalH2 += finalH1;
        finalH1 = fmix64(finalH1);
        finalH2 = fmix64(finalH2);
        finalH1 += finalH2;
        finalH2 += finalH1;
        return new Result(finalH1, finalH2);
    }

    private void putByte(int value) {
        tail[tailLength++] = (byte) value;
        length++;
        if (tailLength == tail.length) {
            mixBlock(littleEndianLong(tail, 0), littleEndianLong(tail, 8));
            tailLength = 0;
        }
    }

    private void mixBlock(long k1, long k2) {
        k1 *= C1;
        k1 = Long.rotateLeft(k1, 31);
        k1 *= C2;
        h1 ^= k1;

        h1 = Long.rotateLeft(h1, 27);
        h1 += h2;
        h1 = h1 * 5 + 0x52dce729;

        k2 *= C2;
        k2 = Long.rotateLeft(k2, 33);
        k2 *= C1;
        h2 ^= k2;

        h2 = Long.rotateLeft(h2, 31);
        h2 += h1;
        h2 = h2 * 5 + 0x38495ab5;
    }

    private static long littleEndianLong(byte[] bytes, int offset) {
        return unsigned(bytes[offset])
                | unsigned(bytes[offset + 1]) << 8
                | unsigned(bytes[offset + 2]) << 16
                | unsigned(bytes[offset + 3]) << 24
                | unsigned(bytes[offset + 4]) << 32
                | unsigned(bytes[offset + 5]) << 40
                | unsigned(bytes[offset + 6]) << 48
                | unsigned(bytes[offset + 7]) << 56;
    }

    private static long unsigned(byte value) {
        return value & 0xffL;
    }

    private static long fmix64(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    record Result(long first, long second) {
    }
}
