# ── Stage 1: Build ────────────────────────────────────────────────────────────
# Changed to an official Maven image so the 'mvn' command works natively
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /app

# Copy only the pom.xml (removed the .mvn copy step)
COPY pom.xml .

# Download dependencies before copying source (cache-friendly)
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -B 2>/dev/null || true

COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Stage 2: Run ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create non-root user (security best practice)
RUN addgroup -S paypulse && adduser -S paypulse -G paypulse
USER paypulse

COPY --from=builder /app/target/paypulse-backend-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dspring.profiles.active=docker", \
  "-jar", "app.jar"]