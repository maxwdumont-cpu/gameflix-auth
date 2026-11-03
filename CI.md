# CI pipeline (HCDD 412, Module 5 Assignment 2: GitHub Actions option)

Workflow: [`.github/workflows/ci.yml`](.github/workflows/ci.yml) · Image: [`Dockerfile`](Dockerfile) · Runs: [Actions tab](https://github.com/maxwdumont-cpu/gameflix-auth/actions)

The workflow runs on every push and pull request to `main`, and can be started by hand (`workflow_dispatch`).

## What one run does

| Step | What it does |
|---|---|
| MySQL service | Starts a throwaway MySQL 8.4 container beside the runner, with a health check so the job waits until it accepts connections. |
| Checkout, JDK 21 | Checks out the repo and installs Temurin JDK 21 with the Maven cache on. |
| JWT secret | Generates a fresh signing key with `openssl rand -base64 48` and masks it in the log. The service will not start without one, and no key is stored anywhere. |
| `mvn -B clean package` | Compiles, runs the 16 MockMvc tests against the MySQL service, builds `target/gameflix-auth-1.0.0.jar`. |
| `docker build -t gameflix-backend .` | Packages the jar into the runtime image. |
| Smoke test | Runs the image just built and calls it: `/api/users/me` with no token → 401, register → 201, login → token, `/api/users/me` with the token → 200. Anything else fails the job. |
| Container logs | Prints the container's last 60 log lines, even when an earlier step failed. |

## Differences from the course template, and why

- **JDK 21, not 17.** `pom.xml` sets `java.version` 21 and the course setup requires Java 21; the template's JDK 17 cannot compile it.
- **A MySQL service container.** The tests boot the real Spring context against a real database, so `mvn clean package` fails on a runner with no MySQL.
- **`actions/checkout@v4` / `actions/setup-java@v4`, not v3.** v3 targets a retired Node runtime. GitHub currently shows a deprecation notice for v4 too (v5 is recommended); it does not affect the build.
- **Smoke test.** `docker build` only proves the image builds. Running it and calling the endpoints proves it serves requests against MySQL.
- **Port 8081**, not 8080 (see README §1.3).

## Configuration and secrets

- Nothing secret is committed. The DB credentials in `ci.yml` belong to a MySQL container that exists only for one run and is not reachable outside the runner; the JWT key is generated per run.
- The image bakes in no configuration. `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and `GAMEFLIX_JWT_SECRET` are passed as environment variables at `docker run` time.
- `.dockerignore` keeps `.env` and `application-local.properties` out of the build context, so a local `docker build` cannot copy them into an image.
- The image runs as a non-root user on `eclipse-temurin:21-jre`.

## Running the same thing locally

```bash
set -a; source .env; set +a
docker compose up -d                       # MySQL on 127.0.0.1:3307
mvn clean package                          # tests against gameflix_test
docker build -t gameflix-backend .
docker run --rm --network host --env-file .env gameflix-backend   # http://localhost:8081
```

## AI use

Claude (Anthropic) wrote `Dockerfile`, `.dockerignore`, `ci.yml` and this file, and committed them through the GitHub web editor. The pipeline is its own verification: run #1 compiled the project, passed all 16 tests against MySQL (0 failures, 0 errors), built the image, and the smoke test exercised the running container end to end.
