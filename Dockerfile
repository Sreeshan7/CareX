# CareX Leave — single deployable image: React SPA bundled into the Spring Boot jar (implementation.md §22.2)

# ---------- 1. frontend ----------
FROM node:20-alpine AS fe
WORKDIR /fe
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# ---------- 2. backend ----------
FROM maven:3.9-eclipse-temurin-21 AS be
WORKDIR /be
COPY backend/pom.xml ./
RUN mvn -B -q -DskipTests dependency:resolve
COPY backend/src ./src
RUN rm -rf src/main/resources/static && mkdir -p src/main/resources/static
COPY --from=fe /fe/dist/ ./src/main/resources/static/
# Tests run in CI (they need the embedded PostgreSQL binaries); the image build only packages.
RUN mvn -B -q -DskipTests package

# ---------- 3. runtime ----------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=be /be/target/app.jar /app/app.jar
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:${PORT:-8080}/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
