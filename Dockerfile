# Stage 1: Build the frontend
FROM node:26-alpine AS frontend-builder
WORKDIR /app/frontend
# Copy package.json and package-lock.json (if it exists)
COPY frontend/package*.json ./
RUN npm ci

# Copy frontend source and build it
COPY frontend/ ./
# Vite config is set to output to ../src/main/resources/static
# So we need to ensure the target directory exists or will be created relative to /app/frontend
RUN npm run build

# Stage 2: Build the Spring Boot application
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app
# Copy gradle files
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle.properties .

# Download dependencies (caching layer)
RUN ./gradlew dependencies --no-daemon || true

# Copy source code
COPY src/ src/
# Copy built frontend assets from the first stage
COPY --from=frontend-builder /app/src/main/resources/static src/main/resources/static

# Build the layered jar
RUN ./gradlew bootJar --no-daemon -PskipFrontend

# Stage 3: Extract the layers
# The legacy "layertools" jar mode was removed in Spring Boot 4.1; "tools" replaces it.
FROM eclipse-temurin:25-jre-alpine AS extractor
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# Stage 4: Create the final runtime image
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Best practice: use a non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy the extracted layers
COPY --from=extractor /app/extracted/dependencies/ ./
COPY --from=extractor /app/extracted/spring-boot-loader/ ./
COPY --from=extractor /app/extracted/snapshot-dependencies/ ./
COPY --from=extractor /app/extracted/application/ ./

# Expose the application port (default for Spring Boot is 8080)
EXPOSE 8080

# /actuator/health is public and reports only UP/DOWN; the JVM needs a moment to start.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"UP"' || exit 1

# Start the slim application jar produced by "tools extract"; it references the
# extracted dependency jars via its manifest Class-Path.
ENTRYPOINT ["java", "-jar", "application.jar"]
