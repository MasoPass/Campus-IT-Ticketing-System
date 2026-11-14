package com.campusdesk.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Password hashing with PBKDF2-HMAC-SHA256, which ships with every JDK (no extra library).
 *
 * Why not just SHA-256(password)? A plain hash is FAST, so an attacker with a stolen
 * database can try billions of guesses per second. PBKDF2 runs the hash hundreds of
 * thousands of times (a "work factor"), making each guess expensive. A random per-user
 * SALT means two users with the same password get different hashes, which defeats
 * precomputed "rainbow tables".
 *
 * Stored format (one string, so the settings travel with the hash):
 *     pbkdf2-sha256$600000$<salt base64>$<hash base64>
 */
public final class PasswordHasher {

    public static final String ALGORITHM_ID = "pbkdf2-sha256";
    /** OWASP's 2023 recommendation for PBKDF2-HMAC-SHA256. */
    public static final int DEFAULT_ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;

    private final int iterations;
    private final SecureRandom random = new SecureRandom();

    public PasswordHasher() {
        this(DEFAULT_ITERATIONS);
    }

    /** Tests use fewer iterations so they run fast. */
    public PasswordHasher(int iterations) {
        this.iterations = iterations;
    }

    public String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = pbkdf2(password.toCharArray(), salt, iterations);
        Base64.Encoder b64 = Base64.getEncoder();
        return ALGORITHM_ID + "$" + iterations + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(hash);
    }

    public boolean matches(String password, String stored) {
        String[] parts = stored == null ? new String[0] : stored.split("\\$");
        if (parts.length != 4 || !ALGORITHM_ID.equals(parts[0])) return false;
        try {
            int iters = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(password.toCharArray(), salt, iters);
            // Constant-time comparison: a normal equals() returns at the first different byte,
            // and that timing difference can leak information to an attacker.
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** True if the stored hash used fewer iterations than we use now (rehash it on next login). */
    public boolean needsRehash(String stored) {
        String[] parts = stored == null ? new String[0] : stored.split("\\$");
        try {
            return parts.length != 4 || Integer.parseInt(parts[1]) < iterations;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 not available", e);   // every JDK 8+ has it
        } finally {
            spec.clearPassword();
        }
    }
}
