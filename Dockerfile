FROM eclipse-temurin:25-jdk AS builder

# Set the working directory
WORKDIR /app

# Copy the Maven wrapper and give execution permission
COPY server/.mvn/ .mvn
COPY server/mvnw .
COPY server/pom.xml .

RUN chmod +x ./mvnw

# Resolve dependencies
RUN ./mvnw dependency:resolve

# Copy backend configuration and source code
COPY server/config ./config
COPY server/src ./src

# Package the Spring Boot application as a JAR file
RUN ./mvnw clean package -DskipTests -Pprod

FROM eclipse-temurin:25-jre AS runtime

WORKDIR /app

# Copy the JAR file from the builder stage
COPY --from=builder /app/target/stocks-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

# Set entry point for the Spring Boot application
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-Xms512m", "-Xmx1024m", "-jar", "/app/app.jar"]