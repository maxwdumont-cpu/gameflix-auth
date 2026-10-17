package edu.psu.hcdd412.gameflix.auth.auth;

import edu.psu.hcdd412.gameflix.auth.user.User;
import edu.psu.hcdd412.gameflix.auth.user.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final TokenService tokens;

    /**
     * A real bcrypt hash of a random value, used when the email isn't registered so that
     * "no such user" costs the same ~250 ms as "wrong password". Without it, response time
     * alone would reveal which emails have accounts, defeating the generic error message.
     */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, TokenService tokens) {
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    /**
     * No "does this email exist?" pre-check. The insert goes straight to MySQL, and the
     * uk_users_email unique constraint decides. saveAndFlush forces the INSERT now, inside
     * this method, so the violation surfaces here rather than at commit time.
     */
    public User register(RegisterRequest req) {
        String email = normalize(req.email());
        String hash = encoder.encode(req.password());
        try {
            return users.saveAndFlush(new User(email, req.displayName().trim(), hash));
        } catch (DataIntegrityViolationException e) {
            if (isEmailUniqueViolation(e)) {
                throw new EmailAlreadyRegisteredException();
            }
            throw e;
        }
    }

    public TokenResponse login(LoginRequest req) {
        if (req == null || req.email() == null || req.password() == null) {
            encoder.matches("", dummyHash);
            throw new InvalidCredentialsException();
        }
        Optional<User> user = users.findByEmail(normalize(req.email()));
        String hashToCheck = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean ok = encoder.matches(req.password(), hashToCheck);
        if (user.isEmpty() || !ok) {
            throw new InvalidCredentialsException();
        }
        return tokens.issue(user.get());
    }

    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isEmailUniqueViolation(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException cve && cve.getConstraintName() != null
                    && cve.getConstraintName().contains(User.EMAIL_UNIQUE_CONSTRAINT)) {
                return true;
            }
            // MySQL: "Duplicate entry 'x' for key 'users.uk_users_email'"
            if (t.getMessage() != null && t.getMessage().contains(User.EMAIL_UNIQUE_CONSTRAINT)) {
                return true;
            }
        }
        return false;
    }
}
