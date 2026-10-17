package com.yulinlin.security.auth;

/** Expected authentication rejection such as invalid credentials, code or account state. */
public class LoginException extends RuntimeException {

    public LoginException(String message) {
        super(message);
    }

    public LoginException(String message, Throwable cause) {
        super(message, cause);
    }
}
