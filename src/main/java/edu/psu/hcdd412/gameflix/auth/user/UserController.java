package edu.psu.hcdd412.gameflix.auth.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository users;

    public UserController(UserRepository users) {
        this.users = users;
    }

    /**
     * By the time this runs, the resource-server filter has already verified the signature
     * and exp. The caller is identified only by the token's sub claim; there is no user id
     * in the URL to tamper with.
     */
    @GetMapping("/me")
    public UserProfile me(@AuthenticationPrincipal Jwt jwt) {
        long id;
        try {
            id = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        // A valid token for an account that has since been deleted is still a 401.
        return users.findById(id)
                .map(UserProfile::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
