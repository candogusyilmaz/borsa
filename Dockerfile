# Stage 1: Build stage
FROM eclipse-temurin:21-jdk-alpine as builder

# Set the working directory
WORKDIR /app

# Copy the Maven wrapper and give execution permission
COPY .mvn/ .mvn
COPY mvnw .
COPY pom.xml .

RUN chmod +x ./mvnw

# Resolve dependencies
RUN ./mvnw dependency:resolve

# Copy the source code
COPY src ./src

# Package the Spring Boot application as a JAR file
RUN ./mvnw clean package -DskipTests -Pprod

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine as runtime

WORKDIR /app

# Copy the JAR file from the builder stage
COPY --from=builder /app/target/stocks-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

# Set entry point for the Spring Boot application
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-Xms512m", "-Xmx1024m", "-jar", "/app/app.jar"]