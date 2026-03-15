# ─────────────────────────────────────────────
#  Stage 1: Build
# ─────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy POM first for dependency layer caching
COPY pom.xml ./
RUN mvn dependency:go-offline -B

# Copy source and build fat JAR
COPY src ./src
RUN mvn package -B -DskipTests

# ─────────────────────────────────────────────
#  Stage 2: Runtime
# ─────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        netcat-openbsd \
        curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy fat JAR from builder
COPY --from=builder /build/target/temporal-workers-java-0.1.0.jar /app/app.jar

# Copy scripts
COPY scripts/ /app/scripts/
RUN chmod +x /app/scripts/entrypoint.sh /app/scripts/setup.d/*.sh

# ── Runtime ──────────────────────────────────
ENV TEMPORAL_HOST=temporal
ENV TEMPORAL_PORT=7233
ENV TEMPORAL_NAMESPACE=default
ENV WORKER_LOG_LEVEL=INFO

# Force IPv4 — prevents gRPC/Netty from attempting IPv6 connections
ENV JAVA_OPTS="-Djava.net.preferIPv4Stack=true"

ENTRYPOINT ["/app/scripts/entrypoint.sh"]
