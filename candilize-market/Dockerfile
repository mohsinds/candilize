# =============================================================
# candilize-market/Dockerfile
# =============================================================
FROM maven:3.9.6-eclipse-temurin-21 AS builder

RUN apt-get update && apt-get install -y protobuf-compiler && rm -rf /var/lib/apt/lists/*

WORKDIR /build

# Copy ROOT pom + ALL module poms
COPY pom.xml .
COPY candilize-proto/pom.xml     candilize-proto/
COPY candilize-auth/pom.xml      candilize-auth/
COPY candilize-market/pom.xml    candilize-market/
COPY candilize-technical/pom.xml candilize-technical/

RUN mvn dependency:go-offline \
    -pl candilize-proto,candilize-market -am \
    --no-transfer-progress

COPY candilize-proto/src  candilize-proto/src
COPY candilize-market/src candilize-market/src

RUN mvn clean package \
    -pl candilize-proto,candilize-market -am \
    -DskipTests \
    --no-transfer-progress

# =============================================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder \
    /build/candilize-market/target/candilize-market-0.0.1-SNAPSHOT.jar \
    app.jar

RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080 9091

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
