package com.yulinlin.data.lang.security;

/**
 * Response wrapper contract whose data field can be replaced by encrypted data.
 */
public interface CryptoDataResponse {

    Object getData();

    void onCryptAfter(Object encryptedData);
}
