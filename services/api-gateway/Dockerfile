FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY services services
RUN --mount=type=cache,target=/root/.m2 mvn -q -f services/pom.xml -pl :api-gateway -am -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/services/api-gateway/target/api-gateway-0.1.0.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
