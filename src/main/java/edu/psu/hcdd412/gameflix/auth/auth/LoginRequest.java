package edu.psu.hcdd412.gameflix.auth.auth;

/**
 * No bean-validation annotations on purpose: a blank or malformed login is treated as a
 * credential failure (401, generic message), not a 400 that tells the caller something about
 * what the server checks.
 */
public record LoginRequest(String email, String password) {

    @Override
    public String toString() {
        return "LoginRequest{email='" + email + "', password=[REDACTED]}";
    }
}
