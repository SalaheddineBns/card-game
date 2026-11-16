# syntax=docker/dockerfile:1.7
# One image = Angular app + Spring Boot API on the same origin (cookies, CSRF and WebSocket need it).
# Build:  docker build -t card-game .
# Tests are NOT run here (CI runs them: .github/workflows/ci.yml).
# No BuildKit cache mounts: Railway rejects them without a service-specific id. Dependencies are still
# cached as normal layers because they are installed before the source code is copied.

# ---------- 1. Angular build ----------
FROM node:24-bookworm-slim AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build
# → /app/frontend/dist/frontend/browser

# ---------- 2. Spring Boot jar (with the Angular build as static resources) ----------
FROM eclipse-temurin:25-jdk AS backend
WORKDIR /app/backend
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY backend/src/ src/
COPY --from=frontend /app/frontend/dist/frontend/browser/ src/main/resources/static/
RUN ./mvnw -B -q -DskipTests package \
    && cp target/backend-*.jar /app/app.jar

# ---------- 3. Runtime ----------
FROM eclipse-temurin:25-jre
RUN groupadd --system app && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=backend --chown=app:app /app/app.jar /app/app.jar
USER app
ENV SPRING_PROFILES_ACTIVE=prod \
    PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=25 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8080
# Railway sets PORT; server.port=${PORT:8080}
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
