package com.yulinlin.security.crypto;

/**
 * Browser-friendly encrypted JSON envelope. The iv and data fields use
 * unpadded Base64 URL encoding.
 */
public record CryptoEnvelope(
        String version,
        String deviceType,
        String algorithm,
        String iv,
        String data) {
}
