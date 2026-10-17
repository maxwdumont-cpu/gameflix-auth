package edu.psu.hcdd412.gameflix.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Base64;

/**
 * gameflix.jwt.* settings. The secret is never in a committed file: application.properties
 * maps it to ${GAMEFLIX_JWT_SECRET}, and startup fails if it is missing or too short.
 */
@ConfigurationProperties(prefix = "gameflix.jwt")
public record JwtProperties(String secret, String issuer, Duration ttl) {

    /** HS256 needs a key at least as long as the hash output: 256 bits. */
    private static final int MIN_KEY_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "GAMEFLIX_JWT_SECRET is not set. Generate one with: openssl rand -base64 48");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(secret.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("GAMEFLIX_JWT_SECRET must be Base64-encoded", e);
        }
        if (key.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "GAMEFLIX_JWT_SECRET decodes to " + key.length + " bytes; HS256 needs at least " + MIN_KEY_BYTES);
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "gameflix-user-service";
        }
        if (ttl == null) {
            ttl = Duration.ofMinutes(15);
        }
    }

    public byte[] secretBytes() {
        return Base64.getDecoder().decode(secret.trim());
    }
}
