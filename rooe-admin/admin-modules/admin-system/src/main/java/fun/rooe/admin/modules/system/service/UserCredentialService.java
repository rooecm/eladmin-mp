package fun.rooe.admin.modules.system.service;

/**
 * Handles password hashing and verification without exposing password hashes to controllers or
 * cached user details.
 */
public interface UserCredentialService {

    /**
     * Verifies a raw password against the current hash stored for a user.
     *
     * @param username    login name
     * @param rawPassword decrypted password supplied by the user
     * @return {@code true} when the password matches
     */
    boolean matches(String username, String rawPassword);

    /**
     * Encodes a raw password using the configured application password encoder.
     *
     * @param rawPassword password to encode
     * @return encoded password hash
     */
    String encode(String rawPassword);
}
