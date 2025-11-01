#!/bin/bash

set -e

echo "===================================="
echo "GraalVM Native Image Profile Setup"
echo "===================================="
echo ""
echo "This script will run the server with the GraalVM tracing agent to generate"
echo "reflection configuration files needed for native image compilation."
echo ""
echo "The server will run until you press Ctrl+C."
echo "While it's running, connect with an OPC UA client and exercise the functionality"
echo "you want to include in the native image."
echo ""

# Check if GraalVM is being used
if ! java -version 2>&1 | grep -q "GraalVM\|Oracle GraalVM"; then
    echo "WARNING: GraalVM is not detected as the active Java runtime."
    echo "Please ensure you're using GraalVM 25 or later."
    echo ""
    echo "Current Java version:"
    java -version
    echo ""
    read -p "Continue anyway? (y/n) " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# Clean and build the JAR first
echo "Building the application JAR..."
mvn clean package -DskipTests

# Create directory for native image configuration
CONFIG_DIR="src/main/resources/META-INF/native-image"
mkdir -p "$CONFIG_DIR"

echo ""
echo "Starting server with GraalVM tracing agent..."
echo "Configuration files will be written to: $CONFIG_DIR"
echo ""
echo "Press Ctrl+C when you're done exercising the application."
echo ""

# Run the application with the native image agent
java -agentlib:native-image-agent=config-output-dir="$CONFIG_DIR" \
    -jar target/opc-ua-demo-server.jar

echo ""
echo "===================================="
echo "Configuration Generation Complete"
echo "===================================="
echo ""
echo "Generated configuration files in: $CONFIG_DIR"
echo ""
ls -lh "$CONFIG_DIR"
echo ""
echo "You can now build the native image with:"
echo "  mvn clean package -Pnative"
echo ""
