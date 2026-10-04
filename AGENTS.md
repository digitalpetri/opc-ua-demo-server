# Project Context

**Tech Stack:** Java 25 managed through `mise`, Maven single-module

The Eclipse Milo OPC UA Demo Server is a standalone server for development, interoperability
testing, and demonstration. It exposes a varied OPC UA address space, including test data, alarms
and conditions, role-based access control, reverse connect, and standard server objects.

**Architecture:**

- **Server bootstrap and configuration:** creates and configures the Milo server, security, and
  lifecycle.
- **Namespaces:** builds the demo, CTT, alarm, and data-type-test address spaces.
- **Server objects:** implements standard objects such as FileType, TrustListType, and
  ServerConfigurationType.
- **Reverse connect:** parses reverse-connect configuration and manages outbound targets.

## Key Entry Points

- Server entry point: `src/main/java/com/digitalpetri/opcua/server/OpcUaDemoServer.java`
- Demo namespace: `src/main/java/com/digitalpetri/opcua/server/namespace/demo/DemoNamespace.java`
- Default configuration: `src/main/resources/default-server.conf`
- Tests: `src/test/java/com/digitalpetri/opcua/server/`

## Building and Testing

| Command | Purpose |
| --- | --- |
| `mise install` | Install pinned Java and Maven tools |
| `mise trust .mise.toml` | Trust the local mise config when prompted |
| `mise exec -- mvn -q clean compile` | Compile without tests |
| `mise exec -- mvn -q clean verify` | Run the full build, tests, and formatting check |
| `mise exec -- mvn -q spotless:apply` | Fix Java formatting |
| `mise exec -- mvn -q clean package` | Build the executable JAR |
| `mise exec -- java -jar target/opc-ua-demo-server.jar` | Run the packaged server |

Before running tests, read `docs/running-tests.md` for unit-test and integration-test invocation
patterns.

Native-image work uses the `native` mise environment. Install it with `MISE_ENV=native mise install`
and build with:

```bash
MISE_ENV=native mise exec -- mvn -q clean package -Pnative -DskipTests
```

## Additional Resources

- Java conventions: `docs/java-coding-conventions.md`
- Running tests: `docs/running-tests.md`
- Test documentation and quality: `docs/test-documentation-and-quality-guidelines.md`
- Dependency source code: `docs/dependencies.md`

## Verification

Use these steps to verify completed work. Implementation plans should include them as success
criteria.

1. Format with `mise exec -- mvn -q spotless:apply`.
2. Run the relevant focused tests described in `docs/running-tests.md`.
3. Run `mise exec -- mvn -q clean verify`.
4. For native-image changes, also run the native build command above.

Before committing, ensure all applicable verification steps pass.

---

> **Build Rule:** ALWAYS delegate Maven commands to a worker subagent. Run them from the repository
> root through `mise exec --`, using the pinned toolchain installed by `mise install`.
