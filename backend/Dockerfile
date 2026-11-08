FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY backend/pom.xml ./pom.xml
COPY backend/src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
RUN addgroup -S spendwise && adduser -S spendwise -G spendwise
COPY --from=build /workspace/target/spendwise-api-0.1.0-SNAPSHOT.jar ./app.jar
USER spendwise
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]


