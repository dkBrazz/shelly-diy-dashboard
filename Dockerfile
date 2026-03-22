# Stage 1: Build the frontend
FROM node:22-alpine AS frontend-builder
WORKDIR /app/frontend
# Copy package.json and package-lock.json (if it exists)
COPY frontend/package*.json ./
RUN npm install

# Copy frontend source and build it
COPY frontend/ ./
# Vite config is set to output to ../src/main/resources/static
# So we need to ensure the target directory exists or will be created relative to /app/frontend
RUN npm run build

# Stage 2: Build the Spring Boot application
FROM eclipse-temurin:24-jdk-alpine AS builder
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
FROM eclipse-temurin:24-jdk-alpine AS extractor
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar application.jar
RUN java -Djarmode=layertools -jar application.jar extract

# Stage 4: Create the final runtime image
FROM eclipse-temurin:24-jre-alpine
WORKDIR /app

# Best practice: use a non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy the extracted layers
COPY --from=extractor /app/dependencies/ ./
COPY --from=extractor /app/spring-boot-loader/ ./
COPY --from=extractor /app/snapshot-dependencies/ ./
COPY --from=extractor /app/application/ ./

# Expose the application port (default for Spring Boot is 8080)
EXPOSE 8080

# Use JarLauncher to start the application (best practice for layered jars)
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
