# ─────────────────────────────────────────────────────────────────────────────
# SlotGuard Application — Dockerfile
# Multi-stage build: compile with Maven, run with slim JRE image
# ─────────────────────────────────────────────────────────────────────────────

# Stage 1: Build
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build

# Copy Maven wrapper and pom first for dependency caching
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn

# Download dependencies (cached layer if pom.xml unchanged)
RUN ./mvnw dependency:go-offline -q

# Copy source and build
COPY src ./src
RUN ./mvnw package -DskipTests -q

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Security: run as non-root user
RUN addgroup -g 1001 -S slotguard && adduser -u 1001 -S slotguard -G slotguard
USER slotguard

# Copy the built JAR
COPY --from=builder /build/target/slotguard-application-1.0.0-SNAPSHOT.jar app.jar

# Expose application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/api/slots || exit 1

# Start the application
ENTRYPOINT ["java", "-jar", "app.jar"]
