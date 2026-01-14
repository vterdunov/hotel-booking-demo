FROM maven:3.9-eclipse-temurin-23-alpine AS builder

WORKDIR /build
COPY ../pom.xml ./pom.xml
COPY ../eureka-server/pom.xml ./eureka-server/pom.xml
COPY ../api-gateway/pom.xml ./api-gateway/pom.xml
COPY ../hotel-service/pom.xml ./hotel-service/pom.xml
COPY ../booking-service/pom.xml ./booking-service/pom.xml

RUN mvn dependency:go-offline -pl hotel-service -am -q

COPY ../hotel-service/src ./hotel-service/src

RUN mvn clean package -DskipTests -pl hotel-service -am -q

FROM eclipse-temurin:23-jre-alpine

WORKDIR /app
COPY --from=builder /build/hotel-service/target/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
