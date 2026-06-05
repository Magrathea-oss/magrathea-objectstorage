# Stage 1: Build with Maven
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Copy POMs first for dependency caching
COPY pom.xml .

# Domain modules
COPY object-store-domain/pom.xml ./object-store-domain/
COPY storage-engine-domain/pom.xml ./storage-engine-domain/

# Application modules
COPY storage-engine-application/pom.xml ./storage-engine-application/

# Infrastructure modules
COPY storage-engine-infrastructure/pom.xml ./storage-engine-infrastructure/

# Reactive modules
COPY object-store-reactive-repository-application/pom.xml ./object-store-reactive-repository-application/
COPY object-store-reactive-application/pom.xml ./object-store-reactive-application/
COPY object-store-reactive-infrastructure/pom.xml ./object-store-reactive-infrastructure/
COPY object-store-reactive-repository-storage-engine-infrastructure/pom.xml ./object-store-reactive-repository-storage-engine-infrastructure/

# API adapter
COPY s3-reactive-api-adapter/pom.xml ./s3-reactive-api-adapter/

# Bootstrap
COPY bootstrap-application/pom.xml ./bootstrap-application/

# Download dependencies
RUN mvn dependency:go-offline -DskipTests

# Copy source for all modules
COPY object-store-domain/src ./object-store-domain/src/
COPY storage-engine-domain/src ./storage-engine-domain/src/
COPY storage-engine-application/src ./storage-engine-application/src/
COPY storage-engine-infrastructure/src ./storage-engine-infrastructure/src/
COPY object-store-reactive-repository-application/src ./object-store-reactive-repository-application/src/
COPY object-store-reactive-application/src ./object-store-reactive-application/src/
COPY object-store-reactive-infrastructure/src ./object-store-reactive-infrastructure/src/
COPY object-store-reactive-repository-storage-engine-infrastructure/src ./object-store-reactive-repository-storage-engine-infrastructure/src/
COPY s3-reactive-api-adapter/src ./s3-reactive-api-adapter/src/
COPY bootstrap-application/src ./bootstrap-application/src/

# Build (skip tests for faster build in Docker)
RUN mvn clean package -DskipTests && \
    cp bootstrap-application/target/*.jar /app.jar

# Stage 2: Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app.jar /app.jar
COPY --from=builder /build/docs /app/docs

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app.jar"]
