# =============================================================
# candilize-auth/Dockerfile
# =============================================================
# ─── Stage 1: Build ──────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS builder

# Install protoc compiler (required by candilize-proto module)
RUN apt-get update && apt-get install -y protobuf-compiler && rm -rf /var/lib/apt/lists/*

WORKDIR /build

# ── Dependency caching layer ─────────────────────────────────
# Copy only pom files first — Docker caches this layer
# and only re-downloads deps when pom files change
COPY pom.xml .
COPY candilize-proto/pom.xml candilize-proto/
COPY candilize-auth/pom.xml  candilize-auth/

RUN mvn dependency:go-offline \
    -pl candilize-proto,candilize-auth -am -q

# ── Source copy & build ──────────────────────────────────────
COPY candilize-proto/src candilize-proto/src
COPY candilize-auth/src  candilize-auth/src

RUN mvn clean package \
    -pl candilize-proto,candilize-auth -am \
    -DskipTests -q

# ─── Stage 2: Runtime ────────────────────────────────────────
# Use JRE (smaller than JDK) on Java 21
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Non-root user (security best practice)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy the built JAR (exact name from pom artifactId + version)
COPY --from=builder \
    /build/candilize-auth/target/candilize-auth-0.0.1-SNAPSHOT.jar \
    app.jar

RUN chown appuser:appgroup app.jar

USER appuser

# HTTP port + gRPC port
EXPOSE 8081 9090

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
