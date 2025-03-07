# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-21 AS builder

# Set working directory inside the container
WORKDIR /app

# Copy the project files
COPY . .

# Uncomment on Apple M4 until bug is fixed in JDK 21.0.7
# See https://bugs.openjdk.org/browse/JDK-8345296
#ENV MAVEN_OPTS="-XX:UseSVE=0"

# Build the application using Maven
RUN mvn clean package

# Stage 2: Run the application using a minimal Java runtime image
FROM bellsoft/liberica-openjdk-alpine:21 AS runtime

# Set working directory inside the container
WORKDIR /app

# Copy the compiled JAR from the build stage
COPY --from=builder /app/target/opc-ua-demo-server.jar opc-ua-demo-server.jar

# Uncomment on Apple M4 until bug is fixed in JDK 21.0.7
# See https://bugs.openjdk.org/browse/JDK-8345296
#ENV JAVA_TOOL_OPTIONS="-XX:UseSVE=0"

# Define the entry point to run server
ENTRYPOINT ["java", "-jar", "opc-ua-demo-server.jar"]