FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY . .

RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -pl notification -am clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/notification/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
