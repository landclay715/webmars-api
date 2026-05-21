FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY webmars-api/ .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/webmars-api-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", \
  "-Dspring.datasource.url=jdbc:postgresql://dpg-d878umog4nts73dlhnl0-a/webmars", \
  "-Dspring.datasource.username=landon", \
  "-Dspring.datasource.password=VRuWY0ZlYOWuUj8a5yZNxDjIPDATvl2r", \
  "-Dspring.jpa.hibernate.ddl-auto=validate", \
  "-jar", "app.jar"]
