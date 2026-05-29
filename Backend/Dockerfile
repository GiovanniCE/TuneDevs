# Builds a container image for running the Spring Boot backend; it provides docker image that starts the backend service.

# Stage 1: Build the application using the project's Gradle Wrapper (9.3.1)
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY gradlew gradlew.bat build.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN chmod +x gradlew
RUN ./gradlew bootJar --no-daemon -x test

# Stage 2: Run the application using Java 21 Runtime
FROM eclipse-temurin:21-jdk-jammy

# Install ALSA (Audio) utilities and libraries required for hardware access
# This is essential for the container to "speak" to the mapped /dev/snd devices
RUN apt-get update && apt-get install -y \
    alsa-utils \
    libasound2 \
    libasound2-dev \
    python3 \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy the built jar from the build stage (Gradle places output in build/libs)
COPY --from=build /app/build/libs/*.jar app.jar
CMD ["java", "-jar", "app.jar"]