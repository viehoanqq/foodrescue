FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY services services
RUN --mount=type=cache,target=/root/.m2 mvn -q -f services/pom.xml -pl :store-service -am -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/services/store-service/target/store-service-0.1.0.jar /app/app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
