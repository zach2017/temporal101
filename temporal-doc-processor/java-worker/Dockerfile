FROM gradle:8.7-jdk17 AS build

WORKDIR /app
COPY build.gradle settings.gradle ./
COPY src ./src

RUN gradle bootJar --no-daemon

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY --from=build /app/build/libs/doc-processor.jar app.jar

# Create storage directories
RUN mkdir -p /app/storage/uploads /app/storage/documents

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
