## ── Build stage ──
FROM maven:latest AS build
WORKDIR /build
COPY pom.xml .
# Cache dependencies first
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -B -DskipTests

## ── Runtime stage ──
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/pdf-text-client-1.0.0.jar pdf-client.jar

ENTRYPOINT ["java", "-jar", "pdf-client.jar"]
# Default args overridden at runtime via docker compose / CLI
CMD ["--help"]
