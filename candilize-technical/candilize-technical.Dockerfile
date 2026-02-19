# =============================================================
# candilize-technical/Dockerfile
# =============================================================
# ─── Stage 1: Build ──────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS builder

# Install protoc compiler (required by candilize-proto module)
RUN apt-get update && apt-get install -y protobuf-compiler && rm -rf /var/lib/apt/lists/*

WORKDIR /build

# ── Dependency caching layer ─────────────────────────────────
COPY pom.xml .
COPY candilize-proto/pom.xml     candilize-proto/
COPY candilize-technical/pom.xml candilize-technical/

RUN mvn dependency:go-offline \
    -pl candilize-proto,candilize-technical -am -q

# ── Source copy & build ──────────────────────────────────────
COPY candilize-proto/src     candilize-proto/src
COPY candilize-technical/src candilize-technical/src

RUN mvn clean package \
    -pl candilize-proto,candilize-technical -am \
    -DskipTests -q

# ─── Stage 2: Runtime ────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder \
    /build/candilize-technical/target/candilize-technical-0.0.1-SNAPSHOT.jar \
    app.jar

RUN chown appuser:appgroup app.jar

USER appuser

# HTTP port only (gRPC client, not server)
EXPOSE 8082

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
