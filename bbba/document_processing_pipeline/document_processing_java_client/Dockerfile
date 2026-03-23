FROM gradle:8.12-jdk21 AS build

WORKDIR /app
COPY . .
RUN gradle build -x test --no-daemon

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app
COPY --from=build /app/build/distributions/*.tar /app/dist.tar

RUN tar xf dist.tar --strip-components=1 && rm dist.tar

ENTRYPOINT ["bin/document-processing-client"]
