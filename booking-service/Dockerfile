FROM maven:3.9-eclipse-temurin-23-alpine AS builder

WORKDIR /build
COPY ../pom.xml ./pom.xml
COPY ../eureka-server/pom.xml ./eureka-server/pom.xml
COPY ../api-gateway/pom.xml ./api-gateway/pom.xml
COPY ../hotel-service/pom.xml ./hotel-service/pom.xml
COPY ../booking-service/pom.xml ./booking-service/pom.xml

RUN mvn dependency:go-offline -pl booking-service -am -q

COPY ../booking-service/src ./booking-service/src

RUN mvn clean package -DskipTests -pl booking-service -am -q

FROM eclipse-temurin:23-jre-alpine

WORKDIR /app
COPY --from=builder /build/booking-service/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
