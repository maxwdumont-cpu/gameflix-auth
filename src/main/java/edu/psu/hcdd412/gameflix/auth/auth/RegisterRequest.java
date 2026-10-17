package edu.psu.hcdd412.gameflix.auth.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Password limits: at least 8 characters (OWASP minimum when there is no MFA), at most 64
 * characters, and at most 72 UTF-8 bytes. The byte rule exists because bcrypt only reads the
 * first 72 bytes, and 64 characters of non-ASCII text can be well over 72 bytes.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 50) String displayName,
        @NotBlank @Size(min = 8, max = 64) @MaxUtf8Bytes(72) String password) {

    /** Never print the password, even in a debug log. */
    @Override
    public String toString() {
        return "RegisterRequest{email='" + email + "', displayName='" + displayName + "', password=[REDACTED]}";
    }
}
