# ── Build stage ──────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# ── Runtime stage ────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app
# Copy only the shaded fat jar (exclude original-* produced by shade plugin)
COPY --from=build /app/target/temporal-java-worker-1.0.0.jar worker.jar
ENTRYPOINT ["java", "-jar", "worker.jar"]
