package edu.psu.hcdd412.gameflix.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

/**
 * Password hashing: bcrypt, cost factor 12, behind a DelegatingPasswordEncoder.
 *
 * PasswordEncoderFactories.createDelegatingPasswordEncoder() would also pick bcrypt, but only
 * at the default cost of 10, and there's no way to pass a strength into it. So the delegating
 * encoder is built by hand with the same "bcrypt" id. Stored hashes look like
 * {bcrypt}$2a$12$..., so the algorithm and cost are recorded in every row and a later
 * migration (e.g. to Argon2id) can add a new id while old hashes still verify.
 */
@Configuration
public class PasswordConfig {

    public static final int BCRYPT_STRENGTH = 12;

    @Bean
    public PasswordEncoder passwordEncoder() {
        String idForEncode = "bcrypt";
        Map<String, PasswordEncoder> encoders = Map.of(
                idForEncode, new BCryptPasswordEncoder(BCRYPT_STRENGTH));
        return new DelegatingPasswordEncoder(idForEncode, encoders);
    }
}
