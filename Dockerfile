# Stage 1: Build with Maven + Node.js
FROM maven:3.9-eclipse-temurin-21 AS builder

ARG NODE_VERSION=26.1.0
ARG NODE_DIST=node-v${NODE_VERSION}-linux-x64
ARG NODE_URL=https://nodejs.org/dist/v${NODE_VERSION}/${NODE_DIST}.tar.xz

ENV NODE_HOME=/opt/node
ENV PATH="${NODE_HOME}/bin:${PATH}"

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl xz-utils ca-certificates libatomic1 \
    && curl -fsSL "${NODE_URL}" -o /tmp/node.tar.xz \
    && mkdir -p "${NODE_HOME}" \
    && tar -xJf /tmp/node.tar.xz -C "${NODE_HOME}" --strip-components=1 \
    && ln -sf "${NODE_HOME}/bin/node" /usr/local/bin/node \
    && ln -sf "${NODE_HOME}/bin/npm" /usr/local/bin/npm \
    && ln -sf "${NODE_HOME}/bin/npx" /usr/local/bin/npx \
    && node --version \
    && npm --version \
    && rm -rf /var/lib/apt/lists/* /tmp/node.tar.xz

WORKDIR /build

# Copy POMs first for dependency caching
COPY pom.xml .

# All module POMs
COPY object-store-domain/pom.xml ./object-store-domain/
COPY storage-engine-domain/pom.xml ./storage-engine-domain/
COPY storage-engine-application/pom.xml ./storage-engine-application/
COPY storage-engine-infrastructure/pom.xml ./storage-engine-infrastructure/
COPY object-store-reactive-repository-application/pom.xml ./object-store-reactive-repository-application/
COPY object-store-reactive-application/pom.xml ./object-store-reactive-application/
COPY object-store-reactive-infrastructure/pom.xml ./object-store-reactive-infrastructure/
COPY object-store-reactive-repository-storage-engine-infrastructure/pom.xml ./object-store-reactive-repository-storage-engine-infrastructure/
COPY s3-reactive-api-adapter/pom.xml ./s3-reactive-api-adapter/
COPY admin-api-adapter/pom.xml ./admin-api-adapter/
COPY bootstrap-application/pom.xml ./bootstrap-application/

# Frontend POM (just package.json for npm)
COPY magrathea-ui/package.json magrathea-ui/package-lock.json magrathea-ui/vite.config.js ./magrathea-ui/
COPY magrathea-ui/index.html ./magrathea-ui/
COPY magrathea-ui/public ./magrathea-ui/public/
COPY magrathea-ui/src ./magrathea-ui/src/

# Download Maven dependencies
RUN mvn -B dependency:go-offline -DskipTests

# Copy all source code
COPY docs ./docs/
COPY admin-api-adapter/src ./admin-api-adapter/src/
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

# Build (Maven will call npm via exec-maven-plugin during generate-resources)
RUN mvn -B clean package -DskipTests && \
    cp bootstrap-application/target/*.jar /app.jar

# Stage 2: Runtime
FROM docker.io/eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app.jar /app.jar
COPY --from=builder /build/docs /app/docs

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app.jar"]
