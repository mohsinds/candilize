# =============================================================
# candilize-auth/Dockerfile
# =============================================================
FROM maven:3.9.6-eclipse-temurin-21 AS builder

RUN apt-get update && apt-get install -y protobuf-compiler && rm -rf /var/lib/apt/lists/*

WORKDIR /build

# Copy ROOT pom + ALL module poms (root pom declares all 4 modules)
COPY pom.xml .
COPY candilize-proto/pom.xml     candilize-proto/
COPY candilize-auth/pom.xml      candilize-auth/
COPY candilize-market/pom.xml    candilize-market/
COPY candilize-technical/pom.xml candilize-technical/

# Download deps only for proto + auth (other modules declared but no src = skipped)
RUN mvn dependency:go-offline \
    -pl candilize-proto,candilize-auth -am \
    --no-transfer-progress

# Copy source only for what we need to build
COPY candilize-proto/src candilize-proto/src
COPY candilize-auth/src  candilize-auth/src

# Build proto first, then auth
RUN mvn clean package \
    -pl candilize-proto,candilize-auth -am \
    -DskipTests \
    --no-transfer-progress

# =============================================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder \
    /build/candilize-auth/target/candilize-auth-0.0.1-SNAPSHOT.jar \
    app.jar

RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8081 9090

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
