package edu.psu.hcdd412.gameflix.auth.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.Objects;

/**
 * HS256 signing and verification with one shared secret (this service both issues and checks
 * its own tokens, so a symmetric key is enough; other services would move to RS256 + JWKS).
 */
@Configuration
public class JwtConfig {

    private final SecretKey key;
    private final JwtProperties props;

    public JwtConfig(JwtProperties props) {
        this.props = props;
        this.key = new SecretKeySpec(props.secretBytes(), "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256) // pin the algorithm; anything else is rejected
                .build();

        // The default validators check exp/nbf *if present* and the issuer. They do not
        // require exp to exist, so a token minted without one would never expire.
        // The extra validator below makes exp mandatory.
        OAuth2TokenValidator<Jwt> expRequired =
                new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull);

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(props.issuer()),
                expRequired));
        return decoder;
    }
}
