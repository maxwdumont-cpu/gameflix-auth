package edu.psu.hcdd412.gameflix.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.psu.hcdd412.gameflix.auth.config.JwtProperties;
import edu.psu.hcdd412.gameflix.auth.user.User;
import edu.psu.hcdd412.gameflix.auth.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack tests: real Spring context, real security filter chain, real MySQL
 * (the gameflix_test database). DB credentials and the JWT secret come from the
 * environment, exactly like the running service.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=${TEST_DB_URL:jdbc:mysql://localhost:3307/gameflix_test}",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "gameflix.cors.allowed-origin=http://localhost:3000"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthApiTest {

    private static final String EMAIL = "test.player@example.com";
    private static final String PASSWORD = "Throwaway-Pass-123";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ObjectMapper json;
    @Autowired JwtEncoder encoder;
    @Autowired JwtProperties jwtProps;

    @BeforeEach
    void clean() {
        users.deleteAll();
    }

    // ---------------------------------------------------------------- register

    @Test @Order(1)
    @DisplayName("1. register: valid input -> 201, profile returned, no password or hash in body")
    void registerSuccess() throws Exception {
        mvc.perform(register(EMAIL, "Test Player", PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString(PASSWORD))))
                .andExpect(content().string(not(containsString("$2a$"))));
    }

    @Test @Order(2)
    @DisplayName("2. register: stored value is a {bcrypt} cost-12 hash, never the password")
    void passwordIsHashed() throws Exception {
        mvc.perform(register(EMAIL, "Test Player", PASSWORD)).andExpect(status().isCreated());
        User saved = users.findByEmail(EMAIL).orElseThrow();
        assertThat(saved.getPasswordHash())
                .startsWith("{bcrypt}$2a$12$")
                .hasSize(68)
                .doesNotContain(PASSWORD);
    }

    @Test @Order(3)
    @DisplayName("3. register: duplicate email -> 409 (also when only the letter case differs)")
    void registerDuplicate() throws Exception {
        mvc.perform(register(EMAIL, "First", PASSWORD)).andExpect(status().isCreated());
        mvc.perform(register("Test.Player@Example.com", "Second", PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.correlationId").exists());
        assertThat(users.count()).isEqualTo(1);
    }

    @Test @Order(4)
    @DisplayName("4. register: invalid input -> 400 with field names, rejected value not echoed")
    void registerInvalid() throws Exception {
        mvc.perform(register("not-an-email", "", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.displayName").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(content().string(not(containsString("short"))));
    }

    @Test @Order(5)
    @DisplayName("5. register: password over 72 UTF-8 bytes (bcrypt limit) -> 400")
    void registerPasswordTooManyBytes() throws Exception {
        String thirtyEmoji = "🎮".repeat(30); // 30 game controllers = 60 chars, 120 bytes
        mvc.perform(register(EMAIL, "Emoji Fan", thirtyEmoji))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    // ---------------------------------------------------------------- login

    @Test @Order(6)
    @DisplayName("6. login: correct credentials -> 200 with signed JWT that has exp, no PII")
    void loginSuccess() throws Exception {
        mvc.perform(register(EMAIL, "Test Player", PASSWORD)).andExpect(status().isCreated());

        MvcResult res = mvc.perform(login(EMAIL, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn();

        String token = json.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
        JsonNode header = decodePart(token, 0);
        JsonNode payload = decodePart(token, 1);
        assertThat(header.get("alg").asText()).isEqualTo("HS256");
        assertThat(payload.has("exp")).isTrue();
        assertThat(payload.get("exp").asLong() - payload.get("iat").asLong()).isEqualTo(900);
        assertThat(payload.get("iss").asText()).isEqualTo("gameflix-user-service");
        assertThat(payload.toString()).doesNotContain(EMAIL).doesNotContain(PASSWORD);
    }

    @Test @Order(7)
    @DisplayName("7. login: wrong password -> 401 with the generic message")
    void loginWrongPassword() throws Exception {
        mvc.perform(register(EMAIL, "Test Player", PASSWORD)).andExpect(status().isCreated());
        mvc.perform(login(EMAIL, "Wrong-Pass-999"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test @Order(8)
    @DisplayName("8. login: unknown email -> 401 with the SAME generic message")
    void loginUnknownEmail() throws Exception {
        mvc.perform(login("nobody@example.com", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    // ---------------------------------------------------------------- /me

    @Test @Order(9)
    @DisplayName("9. GET /api/users/me with a valid token -> 200, caller's own profile")
    void meWithValidToken() throws Exception {
        mvc.perform(register(EMAIL, "Test Player", PASSWORD)).andExpect(status().isCreated());
        String token = loginAndGetToken();
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.displayName").value("Test Player"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test @Order(10)
    @DisplayName("10. GET /api/users/me with no token -> 401")
    void meMissingToken() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer")));
    }

    @Test @Order(11)
    @DisplayName("11. GET /api/users/me with a tampered token (payload edited, signature kept) -> 401")
    void meTamperedToken() throws Exception {
        mvc.perform(register(EMAIL, "Test Player", PASSWORD)).andExpect(status().isCreated());
        String token = loginAndGetToken();

        // Decode the payload, change role to ADMIN, re-encode, keep the original signature.
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String forged = payload.replace("\"USER\"", "\"ADMIN\"");
        assertThat(forged).isNotEqualTo(payload);
        String tampered = parts[0] + "." +
                Base64.getUrlEncoder().withoutPadding().encodeToString(forged.getBytes(StandardCharsets.UTF_8)) +
                "." + parts[2];

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(12)
    @DisplayName("12. GET /api/users/me with an expired (but correctly signed) token -> 401")
    void meExpiredToken() throws Exception {
        mvc.perform(register(EMAIL, "Test Player", PASSWORD)).andExpect(status().isCreated());
        User u = users.findByEmail(EMAIL).orElseThrow();
        Instant issued = Instant.now().minus(2, ChronoUnit.HOURS);
        String expired = sign(JwtClaimsSet.builder()
                .issuer(jwtProps.issuer())
                .subject(String.valueOf(u.getId()))
                .issuedAt(issued)
                .expiresAt(issued.plus(15, ChronoUnit.MINUTES))
                .claim("role", "USER")
                .build());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(13)
    @DisplayName("13. GET /api/users/me with a correctly signed token that has NO exp -> 401")
    void meTokenWithoutExp() throws Exception {
        mvc.perform(register(EMAIL, "Test Player", PASSWORD)).andExpect(status().isCreated());
        User u = users.findByEmail(EMAIL).orElseThrow();
        String noExp = sign(JwtClaimsSet.builder()
                .issuer(jwtProps.issuer())
                .subject(String.valueOf(u.getId()))
                .issuedAt(Instant.now())
                .claim("role", "USER")
                .build());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + noExp))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(14)
    @DisplayName("14. GET /api/users/me with a token signed by a different key -> 401")
    void meWrongKey() throws Exception {
        // header.payload signed with HMAC-SHA256 under a key the server has never seen
        String foreign = "eyJhbGciOiJIUzI1NiJ9."
                + "eyJpc3MiOiJnYW1lZmxpeC11c2VyLXNlcnZpY2UiLCJzdWIiOiIxIiwiZXhwIjo0MTAyNDQ0ODAwfQ."
                + "c2lnbmVkLXdpdGgtYW5vdGhlci1rZXktbm90LXZhbGlk";
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + foreign))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- CORS

    @Test @Order(15)
    @DisplayName("15. CORS: preflight from the configured front-end origin is allowed")
    void corsAllowedOrigin() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test @Order(16)
    @DisplayName("16. CORS: preflight from any other origin is rejected (403, no allow-origin header)")
    void corsOtherOriginRejected() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    // ---------------------------------------------------------------- helpers

    private org.springframework.test.web.servlet.RequestBuilder register(String email, String name, String pw)
            throws Exception {
        String body = json.writeValueAsString(new java.util.LinkedHashMap<String, String>() {{
            put("email", email); put("displayName", name); put("password", pw);
        }});
        return post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private org.springframework.test.web.servlet.RequestBuilder login(String email, String pw) throws Exception {
        String body = json.writeValueAsString(java.util.Map.of("email", email, "password", pw));
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String loginAndGetToken() throws Exception {
        MvcResult res = mvc.perform(login(EMAIL, PASSWORD)).andExpect(status().isOk()).andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String sign(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private JsonNode decodePart(String token, int i) throws Exception {
        return json.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[i]));
    }
}
