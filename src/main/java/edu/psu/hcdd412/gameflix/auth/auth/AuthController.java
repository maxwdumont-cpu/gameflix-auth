package edu.psu.hcdd412.gameflix.auth.auth;

import edu.psu.hcdd412.gameflix.auth.user.User;
import edu.psu.hcdd412.gameflix.auth.user.UserProfile;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    /** 201 + profile (no hash); 400 invalid input; 409 duplicate email. */
    @PostMapping("/register")
    public ResponseEntity<UserProfile> register(@Valid @RequestBody RegisterRequest req) {
        User user = auth.register(req);
        return ResponseEntity.created(URI.create("/api/users/me")).body(UserProfile.from(user));
    }

    /** 200 + signed JWT; 401 with one generic message on any failure. */
    @PostMapping("/login")
    public TokenResponse login(@RequestBody(required = false) LoginRequest req) {
        return auth.login(req);
    }
}
