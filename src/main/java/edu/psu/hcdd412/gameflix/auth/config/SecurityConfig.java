package edu.psu.hcdd412.gameflix.auth.config;

import edu.psu.hcdd412.gameflix.auth.web.JsonAuthEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 6 style: a SecurityFilterChain bean configured with lambdas
 * (no WebSecurityConfigurerAdapter).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JsonAuthEntryPoint entryPoint) throws Exception {
        http
            // Stateless JSON API authenticated by an Authorization header, not a cookie, so
            // there is no ambient credential for a CSRF attack to ride on.
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated())
            // Bearer-token filter: verifies the HS256 signature and the exp/iss claims
            // *before* any claim is trusted, then exposes the Jwt to controllers.
            .oauth2ResourceServer(rs -> rs
                .jwt(Customizer.withDefaults())
                .authenticationEntryPoint(entryPoint))
            .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
            .httpBasic(b -> b.disable())
            .formLogin(f -> f.disable());
        return http.build();
    }

    /** CORS for the configured front-end origin only. Never "*". */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${gameflix.cors.allowed-origin}") String allowedOrigin) {
        if (allowedOrigin.contains("*")) {
            throw new IllegalStateException("gameflix.cors.allowed-origin must name a single origin, not a wildcard");
        }
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of(allowedOrigin));
        cfg.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }
}
