# GraalVM Native Image Support

This project supports building a native executable using GraalVM 25 Native Image.

## Prerequisites

Install GraalVM 25 using SDKMAN:

```bash
sdk install java 25-graalce
sdk use java 25-graalce
```

Maven 3.2.5 or later is also required.

## Building a Native Image

### Direct Build

Build the native image directly:

```bash
mvn clean package -Pnative
```

The native executable will be created at: `target/opc-ua-demo-server`

### Generate Configuration First (Recommended)

For better runtime compatibility, generate reflection configuration files by profiling a typical workload:

1. Run the configuration generation script:

    ```bash
    ./generate-native-config.sh
    ```

2. While the server is running, connect with an OPC UA client and exercise the functionality you need (browse nodes, read/write values, subscribe to changes, call methods, etc.)

3. Press Ctrl+C when done. Configuration files will be generated in `src/main/resources/META-INF/native-image/`

4. Build the native image:

    ```bash
    mvn clean package -Pnative
    ```

## Running the Native Image

```bash
./target/opc-ua-demo-server
```

## Troubleshooting

If you encounter missing reflection configuration errors at runtime, run the `generate-native-config.sh` script and exercise the problematic functionality before rebuilding.
