package com.yulinlin.security.auth;

/**
 * Application-provided authentication contract.
 * The security module defines the lifecycle API but does not prescribe account storage or credential validation.
 */
public interface ISecurityManager {

    /** Logs in without a verification code. */
    default IUserDetails login(String username, String password) {
        return login(username, password, null);
    }

    /**
     * Authenticates one user.
     *
     * @param username account name
     * @param password raw credential submitted for this login attempt
     * @param code optional verification code; {@code null} when unused
     * @return the authenticated user; never {@code null}
     * @throws LoginException when the account, password, code or account state rejects the login
     */
    IUserDetails login(String username, String password, String code);

    /** Logs out the user associated with the current request. */
    void logout();

    /**
     * Returns the user associated with the current request.
     *
     * @return current authenticated user, or {@code null} when this request is anonymous
     */
    IUserDetails getUserDetails();

    /**
     * Returns one user's details without changing the current request's identity.
     * Implementations may use an existing login snapshot or cache.
     *
     * @param userId user to read
     * @return user details, or {@code null} when the user does not exist
     */
    IUserDetails getUserDetails(String userId);

    /**
     * Forces the current authenticated user's details to be reloaded.
     *
     * @return refreshed authenticated user, or {@code null} when this request is anonymous
     */
    default IUserDetails refresh() {
        IUserDetails current = getUserDetails();
        return current == null ? null : refresh(current.getUserId());
    }

    /**
     * Forces one user's details to be reloaded without changing the current request's identity.
     *
     * @param userId user to reload
     * @return refreshed user details
     */
    IUserDetails refresh(String userId);
}
