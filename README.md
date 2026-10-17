# GameFlix User Service — Login & Registration (HCDD 412, Module 4)

A Spring Boot 3.5 / Java 21 service that registers users, logs them in, issues a signed JWT,
and uses that token to protect `GET /api/users/me`. Users are stored in MySQL 8.4 through
Spring Data JPA.

| Endpoint | Behavior | Codes |
|---|---|---|
| `POST /api/auth/register` | Creates a user. Password hashed (bcrypt, cost 12) before it is saved. Response never contains the password or the hash. | 201 · 400 invalid input · 409 email already registered |
| `POST /api/auth/login` | Checks credentials, returns `{ accessToken, tokenType, expiresIn }`. The token is HS256-signed and has an `exp` claim. | 200 · 401 on any failure, always the same message |
| `GET /api/users/me` | Protected. Returns the caller's own profile, found from the token's `sub` claim. | 200 · 401 missing / malformed / tampered / expired token |

**Stack:** Java 21 · Spring Boot 3.5.16 (Spring Security 6.5, lambda `SecurityFilterChain`, no
`WebSecurityConfigurerAdapter`) · Spring Data JPA / Hibernate 6 · MySQL 8.4 (`mysql-connector-j`)
· `spring-boot-starter-oauth2-resource-server` for JWT encode/verify (Nimbus JOSE) · JUnit 5 +
MockMvc.

---

## 1. How to run it

### Prerequisites
- Java 21 and Maven 3.9 — or just Docker (every command below has a Docker form, which is how
  I ran it).
- Docker for MySQL (or an existing MySQL 8.x — see "Using your own MySQL").

### 1.1 Secrets: `.env` (never committed)
```bash
cp .env.example .env
# then edit .env:
#   MYSQL_ROOT_PASSWORD=<anything>
#   DB_USERNAME=gameflix_app
#   DB_PASSWORD=<anything>
#   GAMEFLIX_JWT_SECRET=<output of: openssl rand -base64 48>
```
`.env` and `application-local.properties` are in `.gitignore`. The committed
`application.properties` only contains `${...}` placeholders, and `application.properties.example`
shows every key with a placeholder value.

### 1.2 MySQL — database and user the service expects
```bash
docker compose up -d          # MySQL 8.4 on 127.0.0.1:3307
```
| | |
|---|---|
| Host / port | `localhost:3307` (3307 so it doesn't collide with a local MySQL on 3306) |
| Database | `gameflix` (the service) and `gameflix_test` (the automated tests) |
| User | `gameflix_app`, password = `DB_PASSWORD` from `.env` |

The image creates `gameflix` and `gameflix_app`; `db/init/01-test-database.sql` creates
`gameflix_test` and grants it to the same user. The `users` table is created by Hibernate on
first start (see §3).

**Using your own MySQL instead of Docker:**
```sql
CREATE DATABASE gameflix;
CREATE DATABASE gameflix_test;
CREATE USER 'gameflix_app'@'%' IDENTIFIED BY '<password>';
GRANT ALL PRIVILEGES ON gameflix.* TO 'gameflix_app'@'%';
GRANT ALL PRIVILEGES ON gameflix_test.* TO 'gameflix_app'@'%';
```
then set `DB_URL=jdbc:mysql://localhost:3306/gameflix` (and `TEST_DB_URL=...gameflix_test`).

### 1.3 Build, test, run
```bash
set -a; source .env; set +a      # export the variables into this shell

mvn test                          # 16 MockMvc tests against MySQL (gameflix_test)
mvn package                       # runs the tests again, builds target/gameflix-auth-1.0.0.jar
java -jar target/gameflix-auth-1.0.0.jar      # http://localhost:8081
```
Docker-only equivalent (no local JDK needed):
```bash
docker run --rm --network host -v "$PWD":/app -v "$HOME/.m2":/root/.m2 -w /app \
  --env-file .env maven:3.9-eclipse-temurin-21 mvn -B package
docker run --rm --network host -v "$PWD/target":/app:ro --env-file .env \
  eclipse-temurin:21-jre java -jar /app/gameflix-auth-1.0.0.jar
```
The service listens on **8081** (8080 is taken on my machine). Override with `PORT`.

### 1.4 Try it
```bash
./run-evidence.sh     # runs all required cases with curl, then the MySQL SELECT
```
Or by hand:
```bash
curl -i -X POST localhost:8081/api/auth/register -H 'Content-Type: application/json' \
     -d '{"email":"demo.player@example.com","displayName":"Demo Player","password":"Demo-Throwaway-42"}'
TOKEN=$(curl -s -X POST localhost:8081/api/auth/login -H 'Content-Type: application/json' \
     -d '{"email":"demo.player@example.com","password":"Demo-Throwaway-42"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')
curl -i localhost:8081/api/users/me -H "Authorization: Bearer $TOKEN"
```

### Configuration keys
| Key | Source | Default |
|---|---|---|
| `spring.datasource.url` | `DB_URL` | `jdbc:mysql://localhost:3307/gameflix` |
| `spring.datasource.username` / `password` | `DB_USERNAME` / `DB_PASSWORD` | none — required |
| `gameflix.jwt.secret` | `GAMEFLIX_JWT_SECRET` (Base64, ≥ 32 bytes decoded) | none — startup fails without it |
| `gameflix.jwt.ttl` | `application.properties` | `15m` |
| `gameflix.cors.allowed-origin` | `GAMEFLIX_FRONTEND_ORIGIN` | `http://localhost:3000` |
| `server.port` | `PORT` | `8081` |

---

## 2. Schema

`db/schema.sql` holds the output of `SHOW CREATE TABLE users;` from the running database:

```sql
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `display_name` varchar(50) NOT NULL,
  `email` varchar(254) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `role` enum('ADMIN','USER') NOT NULL,
  `tos_accepted_at` datetime(6) DEFAULT NULL,
  `tos_version` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

- **`password_hash VARCHAR(255)`** — sized for the hash, not the password. A stored value is
  `{bcrypt}` + a 60-char bcrypt hash = **68 characters** (confirmed by `LENGTH()` in the
  evidence). 255 leaves room for an Argon2id hash (~100 chars) if the algorithm is ever migrated.
- **`UNIQUE KEY uk_users_email`** — uniqueness is enforced by MySQL. Registration does *no*
  "does this email exist?" query first: it inserts with `saveAndFlush`, and if MySQL rejects the
  row with error 1062 on `uk_users_email`, the service answers 409. Two simultaneous
  registrations can't both win, because the database, not the application, decides. Emails are
  also trimmed and lower-cased before saving, so `Test@X.com` and `test@x.com` collide.
- The column is named `password_hash` (as in the Module 3 ERD) rather than `password`, so the
  evidence query is `SELECT id, email, password_hash FROM users;`.
- `tos_version` / `tos_accepted_at` come from the ERD and stay NULL for now; the Terms-of-Service
  flow isn't part of this assignment.

## 3. `spring.jpa.hibernate.ddl-auto`

- **Service: `update`.** Hibernate creates `users` on the first start and leaves existing rows
  alone after that, so registered accounts survive restarts and the SELECT screenshot can be
  reproduced. `create-drop` would empty the table every shutdown. (For a real deployment this
  would be `validate` plus a migration tool such as Flyway.)
- **Tests: `create-drop`** on the separate `gameflix_test` database, so every test run starts
  from a fresh table and never touches the service's data.

## 4. Password hashing — bcrypt, cost factor 12

**Algorithm.** bcrypt is one of the four adaptive algorithms in the OWASP Password Storage Cheat
Sheet, ships with `spring-boot-starter-security` (no extra dependency, unlike Argon2id/scrypt
which need BouncyCastle), and is salted per hash automatically. It is wrapped in a
`DelegatingPasswordEncoder`, so every stored hash starts with `{bcrypt}` and records how it was
made. Moving to Argon2id later only means adding a new encoder id; old hashes keep verifying.

**Work factor 12.** OWASP's minimum for bcrypt is 10, which is also Spring's default. Each +1
doubles the cost. At 12 a login takes about **210 ms** on my laptop (Intel Core 7 240H, measured
with curl across 5 logins) — slow enough to make offline guessing expensive (4× the cost of 10),
fast enough that a user doesn't notice. `PasswordEncoderFactories.createDelegatingPasswordEncoder()`
can't take a strength, so `PasswordConfig` builds the delegating encoder by hand with
`new BCryptPasswordEncoder(12)` under the same `bcrypt` id.

**Password length.** 8–64 characters, and also at most **72 UTF-8 bytes**. bcrypt only reads the
first 72 bytes, and 64 *characters* is not always ≤ 72 *bytes*: 30 🎮 emoji are 60 characters
but 120 bytes. A custom `@MaxUtf8Bytes(72)` constraint returns 400 instead of silently hashing a
truncated password (test 5).

**No plaintext anywhere.** The password exists only in the request DTO; it is hashed before the
entity is built. The response DTO (`UserProfile`) has no password field at all. The request
records and the `User` entity override `toString()` to redact the password / omit the hash.
Validation errors return the field name and rule, never the rejected value. SQL logging is off.
The test suite asserts the response body contains neither the password nor `$2a$`, and I grepped
the Maven log and the service's Docker log for the throwaway password: 0 matches.

## 5. JWT — HS256, 15-minute lifetime

**Signing.** HMAC-SHA256 with a secret from `GAMEFLIX_JWT_SECRET`. `JwtProperties` refuses to
start if the secret is missing, isn't Base64, or decodes to fewer than 32 bytes (256 bits, the
HS256 minimum). The decoder is pinned to HS256, so a token with a different `alg` is rejected.

**Claims.** `iss` (`gameflix-user-service`), `sub` (the numeric user id), `iat`, `exp`, and
`role`. Nothing confidential: no email, name, or password. The payload is Base64URL, not
encrypted — `run-evidence.sh` decodes it with three lines of Python to make that point.

**Verification order.** Spring's bearer-token filter verifies the signature first, then
`exp`/`nbf` and `iss`, and only then hands the `Jwt` to the controller. `/me` reads only `sub`.

**`exp` is required, not just checked.** Spring's default validator checks `exp` *if it is
there*; a correctly signed token with no `exp` would be accepted forever. `JwtConfig` adds a
`JwtClaimValidator` that rejects any token without `exp` (test 13).

**Lifetime: 15 minutes (`expiresIn: 900`).** There is no refresh token or revocation list yet, so
the lifetime is the only thing limiting how long a stolen token works. 15 minutes keeps that
window short while still covering a normal browse-and-launch session; the front end can send the
user back to login on a 401. Once a refresh-token endpoint exists the access token could go to
5–10 minutes. Spring allows 60 seconds of clock skew on `exp`.

## 6. Errors, generic login failure, CORS

All errors use the Module 3 shape `{ status, message, correlationId }` (+ `fieldErrors` on 400).

- **Login failures are indistinguishable.** Wrong password, unknown email, missing fields — all
  return `401 {"message":"Invalid email or password"}`. For unknown emails the service still
  runs a bcrypt comparison against a dummy hash, so the response *time* doesn't reveal which
  emails are registered either (~210 ms both ways in my measurement).
- **401 vs 403.** Bad or missing token → 401 with `WWW-Authenticate: Bearer`.
- **CORS** allows exactly one origin, `gameflix.cors.allowed-origin` (default
  `http://localhost:3000`), methods GET/POST/OPTIONS, headers `Authorization` and
  `Content-Type`. A `*` in the setting stops startup. Preflight from another origin gets 403 with
  no `Access-Control-Allow-Origin` (tests 15–16).
- **CSRF is disabled on purpose**: the API is stateless and the credential is an
  `Authorization` header, not a cookie, so a browser won't attach it to a forged cross-site
  request. Sessions are `STATELESS`; HTTP Basic and form login are off.

## 7. Test evidence

**Approach: automated** — `@SpringBootTest` + `@AutoConfigureMockMvc` with the real security
filter chain and a real MySQL database (`gameflix_test`), plus a live `curl` run against the
packaged jar (`run-evidence.sh`) and the MySQL query. Screenshots are in
`HCDD412_M4_Auth_Evidence_Dumont.docx`.

Result: **`Tests run: 16, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS.**

| # | Test (`AuthApiTest`) | Required case |
|---|---|---|
| 1 | register valid → 201, no password/hash in body | ✅ successful registration |
| 2 | stored value starts `{bcrypt}$2a$12$`, 68 chars | (hash proof) |
| 3 | same email (different case) → 409, only one row | ✅ duplicate-email rejection |
| 4 | bad email / blank name / short password → 400 | |
| 5 | password over 72 UTF-8 bytes → 400 | |
| 6 | login → 200, HS256 token, `exp − iat = 900`, no PII in payload | ✅ login returns token |
| 7 | wrong password → 401 generic message | ✅ wrong password |
| 8 | unknown email → 401, same message | |
| 9 | `/me` with valid token → 200 own profile | ✅ protected call, valid token |
| 10 | `/me` no token → 401 + `WWW-Authenticate: Bearer` | ✅ missing token |
| 11 | `/me` payload edited (USER→ADMIN), signature kept → 401 | ✅ tampered token |
| 12 | `/me` correctly signed but expired → 401 | |
| 13 | `/me` correctly signed, no `exp` → 401 | |
| 14 | `/me` signed with a different key → 401 | |
| 15 | CORS preflight from allowed origin → 200 | |
| 16 | CORS preflight from other origin → 403 | |

**Database:** after registering `demo.player@example.com` with the throwaway password
`Demo-Throwaway-42`:
```
+----+-------------------------+----------------------------------------------------------------------+-----+
| id | email                   | password_hash                                                        | len |
+----+-------------------------+----------------------------------------------------------------------+-----+
|  1 | demo.player@example.com | {bcrypt}$2a$12$p5TDyriO4Vo9HiXzgSxE7OenplHlAKR5OIJzJs83EFsPQFdxHJ4EW |  68 |
+----+-------------------------+----------------------------------------------------------------------+-----+
```

## 8. Design changes from Module 3

The Module 3 endpoint design and ERD for the User Service changed in these ways:

| Module 3 design | Now | Why |
|---|---|---|
| `POST /users` (register) | `POST /api/auth/register` | Assignment spec. Groups the two unauthenticated routes under `/api/auth/**`, which makes the security rule one line. |
| `POST /auth/tokens` (login) | `POST /api/auth/login` | Same. Response keeps the Module 3 shape `{ accessToken, expiresIn }` and adds `tokenType: "Bearer"`. |
| `GET /users/:id` (self or admin) | `GET /api/users/me` | The caller is identified from the token's `sub`, so there's no id in the URL to swap for someone else's. Admin lookup of other users is deferred. |
| ERD `users.email` "UK" | named constraint `uk_users_email` | The service matches on the constraint name to turn MySQL error 1062 into a 409. |
| ERD `password_hash` | `password_hash VARCHAR(255)` with `{bcrypt}` prefix | Size and prefix chosen for the delegating encoder (68 chars now, room for Argon2id). |
| Emails stored as typed | trimmed + lower-cased | So the unique constraint catches case variants. |
| — | `fieldErrors` on 400 responses | The Module 3 error shape had no place to say which field failed. |
| Subscriptions, ToS acceptance | not built yet | Out of scope for Module 4; `tos_*` columns exist but stay NULL. |

## 9. Use of AI assistance

> **Max — this section has to be yours.** The facts below are accurate; rewrite them in your
> own words after you've read the code, and pick (or replace) the "changed or rejected" item
> with one you can explain in person.

Where I used AI: I used Claude (Cowork) to write this service — the Spring Boot project, the
security and JWT configuration, the tests, the evidence script, and a first draft of this
README. It built and ran everything on my own machine (pop-os) through Docker: MySQL 8.4 in a
container, Maven 3.9 / Temurin 21 for the build, and the packaged jar. The test run, curl run,
and database query in the evidence document are from that machine on 2026-10-17.

How I checked it: [what you reviewed, e.g. read `SecurityConfig`, `JwtConfig`, `AuthService`
line by line; decoded a token at jwt.io; ran `./run-evidence.sh` yourself].

One thing it produced that was changed or rejected — candidates that really happened:

- **Test output leaked the password.** The first version of the tests used MockMvc's
  `andDo(print())`, which writes every request body to the console. The Maven log contained the
  throwaway password 3 times — a violation of "no plaintext password written to a log," even
  in test output. All `print()` calls were removed and the log was re-checked (0 matches).
- **"Validate `exp`" isn't the same as "require `exp`."** Spring's default JWT validator accepts
  a signed token that has no `exp` claim at all. An extra validator was added to make `exp`
  mandatory, with a test (13) that proves a no-`exp` token gets 401.
- **Rejected: the factory default encoder.** `PasswordEncoderFactories.createDelegatingPasswordEncoder()`
  is the usual one-liner, but it's fixed at bcrypt cost 10. It was replaced with a hand-built
  `DelegatingPasswordEncoder` at cost 12.
- **64 characters ≠ 72 bytes.** A plain `@Size(max = 64)` doesn't protect against bcrypt's
  72-byte truncation for non-ASCII passwords; a byte-length check was added (test 5).

## Project layout
```
src/main/java/edu/psu/hcdd412/gameflix/auth/
  GameflixAuthApplication.java
  config/   SecurityConfig · JwtConfig · JwtProperties · PasswordConfig
  auth/     AuthController · AuthService · TokenService · Register/LoginRequest · TokenResponse
            MaxUtf8Bytes · EmailAlreadyRegisteredException · InvalidCredentialsException
  user/     User · Role · UserRepository · UserController (/api/users/me) · UserProfile
  web/      ApiExceptionHandler · ApiError · JsonAuthEntryPoint
src/test/java/.../AuthApiTest.java     16 MockMvc tests against MySQL
db/schema.sql                          SHOW CREATE TABLE users
db/init/01-test-database.sql           creates gameflix_test on first container start
compose.yaml · .env.example · application.properties.example · run-evidence.sh
```
