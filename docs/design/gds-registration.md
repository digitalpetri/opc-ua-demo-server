# GDS application registration

Status: implemented. See README.md for operator setup and verification notes below.

Add optional application registration through Milo's `GdsClient`. After the demo server starts,
it connects to one configured GDS, finds or registers its application record, persists the returned
ApplicationId, and disconnects. Transient failures are retried without preventing clients from
using the demo server.

Application registration supplies the GDS with the server's identity and discovery URLs. Selecting
the application for push management and configuring credentials for incoming management sessions
remain GDS-side operations. Part 12 explicitly leaves that configuration outside the standard
[application registration workflow](https://reference.opcfoundation.org/specs/OPC-10000-12/6.4).

## Configuration

Keep `gds-push-enabled` with its existing meaning and default. Add `gds.registration` independently.
This avoids changing existing configuration files and allows directory registration even when
push management is disabled. Registration credentials authenticate to the GDS; they do not change
the demo server's `SecurityAdmin` credentials.

Default configuration:

```hocon
gds-push-enabled = true

gds.registration {
  enabled = false

  # Required when enabled.
  endpoint-url = ""

  # Require an exact policy match and SignAndEncrypt. No automatic downgrade.
  security-policy = "Basic256Sha256"

  identity {
    username = ""
    password = ""
  }

  # Empty means derive from the running server's discovery URLs.
  discovery-url-list = []

  # Permit intentional changes to a matching application record.
  update-existing = false

  request-timeout = 10 seconds
  attempt-timeout = 60 seconds
  retry-interval = 30 seconds
}
```

Example override in `data/server.conf`:

```hocon
gds.registration {
  enabled = true
  endpoint-url = "opc.tcp://gds.example.com:58810/GlobalDiscoveryServer"
  identity {
    username = "demo-registration"
    password = ${GDS_REGISTRATION_PASSWORD}
  }
  discovery-url-list = ["opc.tcp://demo.example.com:4840/milo"]
}
```

The account needs the GDS's DiscoveryAdmin role or ApplicationAdmin privilege. Registration uses
SignAndEncrypt, as specified by the
[registration workflow](https://reference.opcfoundation.org/specs/OPC-10000-12/6.4).
Keep the mode fixed rather than exposing an option that cannot satisfy this workflow.

Parsing rules:

- A missing section or `enabled = false` creates no registration client, worker, or state file.
  Disabled sections may contain placeholder values; required-field validation applies when enabled.
- Require an `opc.tcp` URL with a host and valid port if supplied. Reject user-info, query, and
  fragment components. Preserve the endpoint path.
- Initially support `Basic256Sha256`, `Aes128_Sha256_RsaOaep`, and `Aes256_Sha256_RsaPss`. These use
  the RSA application identity the demo already creates. Reject other policies explicitly. ECC
  registration can be added without changing the schema.
- Require nonblank username and nonempty password. Support username authentication initially.
  Do not offer an anonymous fallback if authentication fails.
- Validate each discovery URL using the same URL rules. Remove duplicates while preserving order.
  Overrides may represent NAT or external DNS and need not exactly match local advertised URLs.
  An empty effective URL list is a registration error.
- Durations must represent positive whole milliseconds. Bound request timeout to Milo's unsigned
  32-bit millisecond range, and require attempt timeout to be at least request timeout. Bound
  scheduling durations so their conversion to milliseconds cannot overflow.
- Identify invalid configuration by its full key. Never include a password, rendered identity
  object, or an underlying configuration exception containing secret values in logs or exceptions.
  Credential objects must redact `toString()`.

Resolve the merged HOCON configuration with `.resolve()` so environment substitutions work. Load
the classpath defaults independently from the stream used to copy a first-run `server.conf`; the
current bootstrap consumes that stream when copying. Preserve defaults for older user files.
Document that unresolved required substitutions fail loading even inside a disabled section, so
the shipped defaults must not contain required environment references.

## Client identity and trust

Build an `OpcUaClient` with the server's ApplicationUri, application name, product URI, and current
DefaultApplicationGroup. Use `setCertificateGroup(...)` for the application identity and explicitly
override its validator with `DefaultClientCertificateValidator` using the existing application
trust-list manager and quarantine. The group's existing validator is for incoming client
certificates and must not be reused to validate the GDS server certificate.

Require ordinary certificate-chain, ApplicationUri, hostname, validity, and usage validation.
The existing incoming `trust-all-certificates` switch must not bypass outgoing GDS validation.
Operators bootstrap trust by installing the GDS certificate or issuer in the application trust
list and trusting the demo certificate at the GDS. The chosen identity must be valid for client
authentication as well as the demo's server role.

Create a fresh client for each registration attempt so endpoint discovery and certificate selection
use current information after a push update. Select an endpoint with the configured policy,
SignAndEncrypt, UA TCP binary transport, and a compatible username token policy. Fail explicitly
when none matches. Use advertised endpoints without automatic hostname rewriting.

## Application record

Build `ApplicationRecordDataType` from the running server, after successful endpoint binding:

| Field | Source |
| --- | --- |
| ApplicationId | `NodeId.NULL_VALUE` for registration; GDS-returned ID for an update |
| ApplicationUri | Existing UUID-backed server ApplicationUri |
| ApplicationType | `ApplicationType.Server` |
| ApplicationNames | Server application name as a one-element array |
| ProductUri | Server configuration |
| DiscoveryUrls | Configured override or running server's discovery URLs, deduplicated |
| ServerCapabilities | Shared demo capability definition, currently `DA` and `AC` |

Factor the existing `ServerConfigurationObject` capability values into a shared definition so
the directory record and push-management object cannot drift. Derive URLs from the post-startup
application description, which reflects bound endpoints, rather than from bind addresses or
pre-startup endpoint configuration. Warn when automatically derived URLs contain only loopback
addresses; the explicit override covers remote GDS deployments.

## Registration algorithm

One attempt owns one client and always disconnects it in a cleanup path.

1. Discover the endpoint, connect securely, and call `GdsClient.create(client)`. Milo installs the
   GDS codecs and model and resolves the Directory using the connected server's namespace table.
2. Call `findApplications(applicationUri)` even when a persisted ApplicationId exists.
3. With no records, call `registerApplication(desiredRecord)` and retain its returned ID.
4. With one record, verify its URI, ApplicationType, and ProductUri. A conflicting identity is an
   error requiring operator action. Compare names, URLs, and capabilities ignoring array order
   and duplicate entries. Treat null arrays as empty for comparison.
5. If those metadata fields match, reuse the returned ID without a write. If they differ, report
   the differing field names. With `update-existing = false`, require operator action. With
   `update-existing = true`, call `updateApplication(...)` with the existing ID and desired
   metadata. This switch is the operator's explicit choice to manage that record from this config.
6. With multiple records, report ambiguity and stop. Never choose the first result.
7. Reject a null/empty ApplicationId. Persist a valid result and log successful registration or
   confirmation, including ApplicationUri and ApplicationId. Disconnect and stop scheduling.

If registration returns `Bad_EntryExists`, repeat the lookup once within the attempt and apply the
same comparison rules. A timeout or disconnect after a write has an unknown outcome; the next
attempt starts with lookup and therefore recovers a successful write without blindly repeating it.

Run this flow on every process startup. There is no periodic renewal after success and no
`UnregisterApplication` call during shutdown or when the feature is disabled. Application directory
records remain useful across server restarts. An operator removes obsolete records at the GDS.
Deletion of a directory record while the demo runs is repaired on the next restart.

## Persistence

Store a versioned JSON record at `data/gds/registration.json`, containing the configured GDS URL,
the connected GDS ApplicationUri, the demo ApplicationUri, the assigned ApplicationId expressed
with its namespace URI, and the last successful registration time. Write a temporary sibling file
and atomically replace the previous record. Store no credentials.

Treat this file as remembered registration state, not proof that a remote record still exists.
Lookup by ApplicationUri is authoritative on every attempt. A changed GDS URL or either
ApplicationUri invalidates remembered state. Namespace indexes may change between sessions;
never reuse a serialized numeric namespace index as a remote identifier. Missing or malformed
state causes a warning and a fresh lookup. A local write failure reports that remote registration
succeeded but local persistence failed, and retries the lookup/persistence flow without creating a
second record.

## Lifecycle and failures

`OpcUaDemoServer.onStartup()` first awaits `server.startup()`, then starts the registration worker.
The SDK starts ordinary lifecycle participants before endpoint binding, so the worker must be
owned by the outer demo lifecycle. If server startup fails, no registration attempt begins.

Parse and validate enabled configuration before server construction. Configuration errors fail
startup. Remote GDS failures affect only registration; the demo server continues serving clients.
If worker initialization fails locally after server startup, clean up worker resources and shut
down the server before propagating the startup failure.

Use one owned scheduled executor and one attempt at a time. Blocking `GdsClient` methods are
adequate on this dedicated worker; network operations must never run on a server event-loop thread.
Apply request timeouts to discovery and method calls. Enforce a total attempt deadline across
discovery, connection, GDS initialization, lookup, writes, and cleanup. A deadline must disconnect
the owned client and stop its reconnect activity, not merely time out the caller's future.

Retry transport failures, timeouts, and temporary server-unavailable statuses after
`retry-interval`, measured from completion. Trust failures also retry so an operator can approve
certificates without restarting. Authentication/authorization failures, unsupported endpoints or
GDS methods, invalid records, and metadata conflicts stop attempts until restart. Log an actionable
status and field names without secrets. Log the first failure or a changed failure at WARN,
unchanged retry failures at DEBUG, and recovery at INFO.

Shutdown first marks the worker stopped, cancels scheduled retries, disconnects any client, and
waits at most five seconds for cleanup, then shuts down the server in a `finally` path. Synchronize
client publication with stop so shutdown can also close a client created during cancellation.
Late callbacks must neither persist state nor schedule another attempt. Cancellation cannot undo
a registration already accepted remotely; the following startup resolves that through lookup.

## Implementation changes

Use a small `com.digitalpetri.opcua.server.gds` package with `@NullMarked`:

- `GdsRegistrationConfig` parses and validates the section and keeps credentials redacted.
- `GdsRegistrationService` owns scheduling, connection setup, record comparison, and the flow.
  Keep the one-attempt operation separate from scheduling so failure behavior can be tested.
- `GdsRegistrationStore` reads and atomically writes the small state file.

Add compile dependencies on `milo-sdk-client` and `milo-sdk-client-gds`, using the existing Milo
BOM. Remove the client dependency's current test scope. The local `1.2.0-SNAPSHOT` source artifact
contains `GdsClient`; the sibling Milo checkout also supplies `milo-sdk-client-gds-testing` and
`FakeGdsNamespace` for integration tests. Verify those artifacts resolve in a clean build before
depending on them in CI. No custom GDS codecs or raw Call wrappers are needed.

Update the default config and README with opt-in setup, trust bootstrap, GDS account permissions,
environment substitutions, metadata conflict handling, and the separate GDS-side push setup.
Do not introduce automatic certificate pulling, multiple GDS targets, or configuration reload in
this implementation.

## Verification criteria

Unit tests cover absent/disabled configuration, merged defaults, environment resolution, invalid
fields, secret redaction, record construction and comparison, portable ApplicationId persistence,
and malformed state recovery.

Integration tests use a local Milo server with `FakeGdsNamespace` and real `GdsClient` calls. Cover
first registration, restart reuse, allowed updates, refused conflicts, duplicate records, a lost
registration response, GDS namespace-index changes, and changing the configured GDS. Assert the
registered URI matches the running demo and the discovery URL serves `GetEndpoints`.

Exercise secure username authentication, untrusted certificates, denied registration, recovery
after trust approval, attempt timeout, startup bind failure, and shutdown during connect/retry.
Verify the demo remains usable during GDS failure, no client reconnects after shutdown, and a
subsequent attempt selects certificates replaced through push management. Verify a GDS can still
use the existing push-management methods after directory registration.

Before completing implementation, delegate Maven commands to a worker as required by `AGENTS.md`.
Install the pinned tools with `mise install`, then run from the repository root:

```sh
mise exec -- mvn -q spotless:apply
mise exec -- mvn -q test -Dtest='GdsRegistration*Test'
mise exec -- mvn -q verify -Dit.test=GdsRegistrationIT
mise exec -- mvn -q clean verify
```

Because the feature adds runtime client classes, validate native packaging as well. Install the
native toolchain with `MISE_ENV=native mise install`, then run:

```sh
MISE_ENV=native mise exec -- mvn -q clean package -Pnative -DskipTests
```

Smoke-test registration from the native binary against a local GDS. A successful compilation
alone does not verify that the GDS codecs and client transport work in the image.

## Implementation notes

The worker uses the asynchronous `GdsClient` variants with bounded waits on one owned scheduled
executor. It refreshes the namespace table with a bounded wait before `GdsClient.create`, so model
initialization cannot take the blocking namespace fallback outside the attempt deadline. Both the
session and transport are disconnected during cancellation. Registration has no asynchronous
callbacks that persist state or schedule retries.

Persistence uses the existing Typesafe Config library to read and render JSON, avoiding reflection
or another runtime serialization dependency. Atomic replacement is required; an unsupported atomic
move is treated as a local persistence failure and leaves the previous file intact.

The `milo-sdk-client-gds` and `milo-sdk-client-gds-testing` runtime and source artifacts were verified
against the configured Central Portal snapshot repository in an empty temporary Maven repository.

The native smoke test is part of `GdsRegistrationIT` and runs explicitly after native packaging:

```sh
mise exec -- mvn -q verify -Dit.test='GdsRegistrationIT#nativeBinaryRegistersSecurely' \
  -Dgds.native.binary="$PWD/target/opc-ua-demo-server"
```

It launches the native executable with a temporary data directory, mutual certificate trust, and a
local secure username-authenticated GDS. It verifies the registered identity and uses the published
discovery URL for `GetEndpoints`. Ordinary JVM verification skips only this native-specific test.
