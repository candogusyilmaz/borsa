FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app/server

COPY server/.mvn/ .mvn/
COPY server/mvnw server/pom.xml ./

RUN chmod +x ./mvnw
RUN ./mvnw dependency:resolve

COPY server/src/ ./src/
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Copy the JAR file from the builder stage
COPY --from=builder /app/server/target/stocks-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

# Set entry point for the Spring Boot application
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-Xms512m", "-Xmx1024m", "-jar", "/app/app.jar"]
