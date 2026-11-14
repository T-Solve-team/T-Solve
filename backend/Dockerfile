# syntax=docker/dockerfile:1
# T-Solve backend image: Maven builds the jar, a slim Java 21 JRE runs it. One image for the web and the worker.
#   docker build -t tsolve-backend .
#   docker run --rm -p 8080:8080 --env-file .env tsolve-backend
# The Spring profile comes from SPRING_PROFILES_ACTIVE at run time (web: none; worker: worker,worker-mock).
# No secrets and no .env are baked in: every setting comes from the environment when the container starts.
# Base image versions are pinned; Dependabot (DevOps) proposes the updates.

# ---- 1. Build the jar -------------------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies first: this layer is reused until pom.xml changes. Best effort: a plugin that cannot be
# resolved offline is fetched again by the package step.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline || true

COPY src ./src
# `package` stops before `verify`, so the integration tests (Testcontainers needs Docker) and Spotless do not run here.
RUN mvn -B -DskipTests package \
    && cp target/t-solve-backend-*.jar /build/app.jar

# ---- 2. Run it --------------------------------------------------------------------------------
FROM eclipse-temurin:21.0.5_11-jre-alpine
WORKDIR /app
COPY --from=build /build/app.jar /app/app.jar

# Not root: a system user without a login shell.
RUN addgroup -S app && adduser -S -G app app
USER app

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
# wget (in Alpine's busybox) exits with 1 when the health URL does not answer 200, which Docker reads as "unhealthy".
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget -q --spider "http://127.0.0.1:${SERVER_PORT:-8080}/actuator/health" || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
