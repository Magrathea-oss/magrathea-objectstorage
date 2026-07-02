# Stage 1: Build with Maven + Node.js
FROM maven:3.9-eclipse-temurin-21 AS builder

ARG NODE_VERSION=26.1.0
ARG NODE_DIST=node-v${NODE_VERSION}-linux-x64
ARG NODE_URL=https://nodejs.org/dist/v${NODE_VERSION}/${NODE_DIST}.tar.xz

ENV NODE_HOME=/opt/node
ENV PATH="${NODE_HOME}/bin:${PATH}"

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl xz-utils ca-certificates libatomic1 python3 \
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
COPY storage-engine-reactive-repository-application/pom.xml ./storage-engine-reactive-repository-application/
COPY storage-engine-reactive-application/pom.xml ./storage-engine-reactive-application/
COPY storage-engine-reactive-infrastructure/pom.xml ./storage-engine-reactive-infrastructure/
COPY object-store-reactive-repository-application/pom.xml ./object-store-reactive-repository-application/
COPY object-store-reactive-application/pom.xml ./object-store-reactive-application/
COPY object-store-reactive-infrastructure/pom.xml ./object-store-reactive-infrastructure/
COPY object-store-reactive-repository-storage-engine-infrastructure/pom.xml ./object-store-reactive-repository-storage-engine-infrastructure/
COPY s3-reactive-api-adapter/pom.xml ./s3-reactive-api-adapter/
COPY admin-api-adapter/pom.xml ./admin-api-adapter/
COPY bootstrap-application/pom.xml ./bootstrap-application/

# Frontend and documentation script manifests for dependency caching
COPY magrathea-ui/package.json magrathea-ui/package-lock.json magrathea-ui/vite.config.js ./magrathea-ui/
COPY magrathea-ui/index.html ./magrathea-ui/
COPY magrathea-ui/public ./magrathea-ui/public/
COPY magrathea-ui/src ./magrathea-ui/src/
COPY bootstrap-application/src/main/scripts/package.json bootstrap-application/src/main/scripts/package-lock.json ./bootstrap-application/src/main/scripts/

# Install npm dependencies for the frontend and bootstrap documentation scripts
RUN npm ci --prefix magrathea-ui && \
    npm ci --prefix bootstrap-application/src/main/scripts

# Download Maven dependencies (skip unavailable plugins)
RUN mvn -B dependency:go-offline -DskipTests -Dmaven.plugin.skip=true -fn 2>/dev/null || echo 'Warning: some plugins unavailable, continuing'

# Copy all source code
COPY docs ./docs/
COPY admin-api-adapter/src ./admin-api-adapter/src/
COPY object-store-domain/src ./object-store-domain/src/
COPY storage-engine-domain/src ./storage-engine-domain/src/
COPY storage-engine-reactive-repository-application/src ./storage-engine-reactive-repository-application/src/
COPY storage-engine-reactive-application/src ./storage-engine-reactive-application/src/
COPY storage-engine-reactive-infrastructure/src ./storage-engine-reactive-infrastructure/src/
COPY object-store-reactive-repository-application/src ./object-store-reactive-repository-application/src/
COPY object-store-reactive-application/src ./object-store-reactive-application/src/
COPY object-store-reactive-infrastructure/src ./object-store-reactive-infrastructure/src/
COPY object-store-reactive-repository-storage-engine-infrastructure/src ./object-store-reactive-repository-storage-engine-infrastructure/src/
COPY s3-reactive-api-adapter/src ./s3-reactive-api-adapter/src/
COPY bootstrap-application/src ./bootstrap-application/src/

# Gherkin requirements appendix quality gate and deterministic regeneration.
# 1) --check fails the image build if the committed ARC42 appendix is stale
#    relative to the shared requirement feature files (Docker quality gate).
# 2) The generator is then re-run so the documentation assets below are always
#    built from a deterministically regenerated appendix, not host-generated state.
COPY scripts/generate-gherkin-requirements-appendix.py ./scripts/generate-gherkin-requirements-appendix.py
RUN python3 scripts/generate-gherkin-requirements-appendix.py --check && \
    python3 scripts/generate-gherkin-requirements-appendix.py

# Regenerate web documentation assets from source docs inside the builder.
# This intentionally does not copy generated bootstrap static resources from the host context.
RUN set -eux; \
    for required in \
      docs/usermanual/en/index.adoc \
      docs/usermanual/it/index.adoc \
      docs/usermanual/es/index.adoc \
      docs/usermanual/de/index.adoc \
      docs/usermanual/cn/index.adoc \
      docs/arc42/arc42-template.adoc \
      docs/arc42/images \
      docs/c4/images \
      docs/test-report.md \
      docs/adr \
      magrathea-ui/public/favicon.svg \
      magrathea-ui/public/icons.svg; \
    do \
      test -e "$required"; \
    done; \
    for script in \
      bootstrap-application/src/main/scripts/asciidoc-to-json.mjs \
      bootstrap-application/src/main/scripts/asciidoc-to-arc42-json.mjs \
      bootstrap-application/src/main/scripts/markdown-to-json.mjs \
      bootstrap-application/src/main/scripts/adr-to-json.mjs; \
    do \
      test -f "$script"; \
    done; \
    find docs/c4/images -maxdepth 1 -type f -name '*.png' -print -quit | grep -q .; \
    find docs/arc42/images -maxdepth 1 -type f -print -quit | grep -q .; \
    mkdir -p bootstrap-application/src/main/resources/static/docs/c4/images \
             bootstrap-application/src/main/resources/static/docs/arc42/images; \
    cp docs/c4/images/*.png bootstrap-application/src/main/resources/static/docs/c4/images/; \
    cp -R docs/arc42/images/. bootstrap-application/src/main/resources/static/docs/arc42/images/; \
    node bootstrap-application/src/main/scripts/asciidoc-to-json.mjs; \
    node bootstrap-application/src/main/scripts/asciidoc-to-arc42-json.mjs; \
    node bootstrap-application/src/main/scripts/markdown-to-json.mjs; \
    node bootstrap-application/src/main/scripts/adr-to-json.mjs; \
    npm run build --prefix magrathea-ui; \
    cp -R magrathea-ui/dist/. bootstrap-application/src/main/resources/static/; \
    test -f bootstrap-application/src/main/resources/static/index.html; \
    test -f bootstrap-application/src/main/resources/static/favicon.svg; \
    test -f bootstrap-application/src/main/resources/static/icons.svg; \
    test -f bootstrap-application/src/main/resources/static/docs/index.en.json; \
    test -f bootstrap-application/src/main/resources/static/docs/arc42.json; \
    test -f bootstrap-application/src/main/resources/static/docs/test-report.json; \
    test -f bootstrap-application/src/main/resources/static/docs/adr/0001.json; \
    find bootstrap-application/src/main/resources/static/docs/c4/images -maxdepth 1 -type f -name '*.png' -print -quit | grep -q .; \
    find bootstrap-application/src/main/resources/static/docs/arc42/images -maxdepth 1 -type f -print -quit | grep -q .

# Build final JAR packaging with Maven.
ENV NODE_PATH=/build/magrathea-ui/node_modules
RUN mvn -B clean package -DskipTests -fn && \
    cp bootstrap-application/target/*.jar /app.jar

# Stage 2: Runtime
FROM docker.io/eclipse-temurin:21-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /app.jar /app.jar
COPY --from=builder /build/docs /app/docs

EXPOSE 8080
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app.jar"]
