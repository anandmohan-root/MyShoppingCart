# One Dockerfile for all five services: docker compose passes SERVICE=<module name>.
# The build stage is identical for every service, so Docker builds it once and reuses it.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre
ARG SERVICE
# The OpenTelemetry Java agent sends traces, metrics and logs without any code in the services.
ADD --chmod=644 https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.20.1/opentelemetry-javaagent.jar /opentelemetry-javaagent.jar
COPY --from=build /src/${SERVICE}/target/${SERVICE}-0.1.0-SNAPSHOT.jar /app.jar
USER 1000
ENTRYPOINT ["java", "-javaagent:/opentelemetry-javaagent.jar", "-jar", "/app.jar"]
