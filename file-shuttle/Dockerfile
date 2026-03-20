# ── Stage 1: Build ────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# ── Stage 2: Runtime ──────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends \
    nfs-common curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/file-shuttle-1.0.0.jar /app/file-shuttle.jar
RUN mkdir -p /data/local /mnt/nfs-share
ENTRYPOINT ["java", "-jar", "/app/file-shuttle.jar"]
