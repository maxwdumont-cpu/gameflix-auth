package edu.psu.hcdd412.gameflix.auth.user;

import java.time.Instant;

/** What the API returns for a user. There is intentionally no password field of any kind. */
public record UserProfile(Long id, String email, String displayName, String role, Instant createdAt) {

    public static UserProfile from(User u) {
        return new UserProfile(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole().name(), u.getCreatedAt());
    }
}
