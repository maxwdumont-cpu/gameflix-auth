# GameFlix User Service (auth) - runtime image.
# The jar is built first by `mvn clean package` (locally or in GitHub Actions, which also runs
# the MySQL-backed tests); this image only packages and runs it.
#   mvn clean package && docker build -t gameflix-backend .
#   docker run --rm -p 8081:8081 --env-file .env -e DB_URL=jdbc:mysql://host.docker.internal:3307/gameflix gameflix-backend
FROM eclipse-temurin:21-jre

WORKDIR /app
RUN useradd --system --uid 10001 --no-create-home gameflix

COPY target/gameflix-auth-1.0.0.jar /app/app.jar

# Run as a non-root user. No secrets are baked in: DB_URL, DB_USERNAME, DB_PASSWORD and
# GAMEFLIX_JWT_SECRET are supplied as environment variables at `docker run` time.
USER gameflix
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
