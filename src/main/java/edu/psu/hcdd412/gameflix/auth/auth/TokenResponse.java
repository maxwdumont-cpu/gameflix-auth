package edu.psu.hcdd412.gameflix.auth.auth;

/** Login response. Shape matches the Module 3 endpoint spec: { accessToken, expiresIn }. */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
