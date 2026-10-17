package edu.psu.hcdd412.gameflix.auth.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * The users table from the Module 3 ERD (User Service database).
 *
 * Two columns matter most for this assignment:
 *  - email has a database-level UNIQUE constraint (uk_users_email). The 409 on duplicate
 *    registration is driven by that constraint, not by a "does it exist?" query.
 *  - password_hash is VARCHAR(255). A delegating-encoder bcrypt hash is 68 chars
 *    ("{bcrypt}" + 60); 255 leaves room to migrate to Argon2id (~100 chars) later.
 */
@Entity
@Table(name = "users",
       uniqueConstraints = @UniqueConstraint(name = User.EMAIL_UNIQUE_CONSTRAINT, columnNames = "email"))
public class User {

    public static final String EMAIL_UNIQUE_CONSTRAINT = "uk_users_email";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    // Carried over from the ERD; populated when the ToS flow is built (not in this assignment).
    @Column(name = "tos_version", length = 20)
    private String tosVersion;

    @Column(name = "tos_accepted_at")
    private Instant tosAcceptedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
        // for JPA
    }

    public User(String email, String displayName, String passwordHash) {
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public String getTosVersion() { return tosVersion; }
    public Instant getTosAcceptedAt() { return tosAcceptedAt; }
    public Instant getCreatedAt() { return createdAt; }

    /** Deliberately leaves passwordHash out so a stray log line can't leak it. */
    @Override
    public String toString() {
        return "User{id=" + id + ", email='" + email + "', role=" + role + "}";
    }
}
