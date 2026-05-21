# Stage 1: Build with Maven
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Copy POMs first for dependency caching
COPY pom.xml .
COPY shared-domain/pom.xml ./shared-domain/
COPY shared-kernel/pom.xml ./shared-kernel/
COPY object-storage-domain/pom.xml ./object-storage-domain/
COPY object-storage-application/pom.xml ./object-storage-application/
COPY object-storage-infrastructure/pom.xml ./object-storage-infrastructure/
COPY persistence-context-domain/pom.xml ./persistence-context-domain/
COPY persistence-context-application/pom.xml ./persistence-context-application/
COPY persistence-context-infrastructure/pom.xml ./persistence-context-infrastructure/
COPY bootstrap-application/pom.xml ./bootstrap-application/

# Download dependencies
RUN mvn dependency:go-offline -DskipTests

# Copy source
COPY shared-domain/src ./shared-domain/src/
COPY shared-kernel/src ./shared-kernel/src/
COPY object-storage-domain/src ./object-storage-domain/src/
COPY object-storage-application/src ./object-storage-application/src/
COPY object-storage-infrastructure/src ./object-storage-infrastructure/src/
COPY persistence-context-domain/src ./persistence-context-domain/src/
COPY persistence-context-application/src ./persistence-context-application/src/
COPY persistence-context-infrastructure/src ./persistence-context-infrastructure/src/
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
