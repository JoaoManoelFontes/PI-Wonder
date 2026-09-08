FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY . .

RUN ./mvnw -pl core -am clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/core/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
