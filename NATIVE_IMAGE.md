# GraalVM Native Image Support

This project supports building a native executable using GraalVM 25 Native Image.

## Prerequisites

Native-image work uses the repository's `native` mise environment. It keeps the Maven version from
`.mise.toml` and replaces the standard Temurin runtime with the GraalVM version pinned in
`.mise.native.toml`.

Install the native-image toolchain:

```bash
MISE_ENV=native mise install
```

If `mise` reports that either config is not trusted, review the file and trust it once before
retrying:

```bash
mise trust .mise.toml
mise trust .mise.native.toml
```

## Building a Native Image

### Direct Build

Build the native image directly:

```bash
MISE_ENV=native mise exec -- mvn clean package -Pnative
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
    MISE_ENV=native mise exec -- mvn clean package -Pnative
    ```

## Running the Native Image

```bash
./target/opc-ua-demo-server
```

## Build Metadata

The version, build number, and build date reported in the server's `BuildInfo` structure come from
`src/main/resources/com/digitalpetri/opcua/server/build-info.properties`, which Maven resource
filtering populates into `target/classes`.

Do not move this metadata into the shaded JAR's manifest. `native-maven-plugin` compiles from
`target/classes` plus the dependency JARs and never sees the uber JAR, so manifest-only values are
lost in the native image and `BuildDate` degrades to `DateTime.NULL_VALUE` (`1601-01-01T00:00:00Z`).
The manifest entries in `pom.xml` remain only as a fallback for the shaded JAR.

## Troubleshooting

If you encounter missing reflection configuration errors at runtime, run the `generate-native-config.sh` script and exercise the problematic functionality before rebuilding.
