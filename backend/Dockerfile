FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY backend/pom.xml ./pom.xml
COPY backend/src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
RUN addgroup -S spendwise && adduser -S spendwise -G spendwise
COPY --from=build /workspace/target/spendwise-api-0.1.0-SNAPSHOT.jar ./app.jar
COPY backend/docker-entrypoint.sh ./docker-entrypoint.sh
RUN chmod +x ./docker-entrypoint.sh
USER spendwise
EXPOSE 10000
ENTRYPOINT ["/app/docker-entrypoint.sh"]


