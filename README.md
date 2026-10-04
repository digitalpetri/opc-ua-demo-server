![Docker Pulls](https://img.shields.io/docker/pulls/digitalpetri/opc-ua-demo-server)
 ![Docker Image Version (tag)](https://img.shields.io/docker/v/digitalpetri/opc-ua-demo-server/1.0.3)

# Eclipse Milo OPC UA Demo Server

This is a standalone OPC UA demo server built
using [Eclipse Milo](https://github.com/eclipse-milo/milo).

An internet-facing instance of this demo server is accessible at
`opc.tcp://milo.digitalpetri.com:62541/milo`.

It accepts both unsecured and secured connections. All incoming client certificates are automatically trusted.

Authenticate anonymously or with one of the following credential pairs:

- `User` / `password`
    - roles: `WellKnownRole_AuthenticatedUser`
- `UserA` / `password`
    - roles: `SiteA_Read`, `SiteA_Write`
- `UserB` / `password`
    - roles: `SiteB_Read`, `SiteB_Write`
- `SiteAdmin` / `password`
    - roles: `SiteA_Read`, `SiteB_Read`
- `SecurityAdmin` / `password`
    - roles: `WellKnownRole_SecurityAdmin`

## Building and Running

### Maven + JDK 25

This repository pins Java 25 and Maven versions with `mise`. Install the pinned tools:

```bash
mise install
```

If `mise` reports that the config is not trusted, review `.mise.toml` and run
`mise trust .mise.toml` once before retrying.

Use this path to run the server locally from the command line without an IDE.

From the repository root, build the executable JAR:

```bash
mise exec -- mvn clean package
```

Then start the server:

```bash
mise exec -- java -jar target/opc-ua-demo-server.jar
```

The server process runs until you stop it with `Ctrl-C`. When launched from the repository root,
it creates and uses the local `data` directory, including `data/server.conf` and the security
directories. The default configuration listens on `opc.tcp://localhost:4840/milo`.

### Docker

Build the Docker image:

```bash
docker build . -t opc-ua-demo-server
```

Start the server:

```bash
docker run --rm -it -p 4840:4840 opc-ua-demo-server
```

In order to have access to the `server.conf` file and security directories, you may want to mount a
volume mapped to the container's `/app/data` directory:

```bash
docker run --rm -it -p 4840:4840 -v /tmp/opc-ua-demo-server-data:/app/data opc-ua-demo-server
```

## Configuration

### Server

On startup the server loads its configuration from the active data directory. When run with
`java -jar` from the repository root, this is `data/server.conf`. When run in Docker, this is
`/app/data/server.conf`. If the file doesn't exist, the default configuration from
`src/main/resources/default-server.conf` will be copied to that location.

The server configuration file is in HOCON format and its configuration keys and values are
documented with comments.

### Alias Names

OPC UA Part 17 Alias Names support is enabled by default. The server publishes two categories under
the standard `TagVariables` category:

- `MiloDemoStatic` contains `Demo.Static.<Type>` aliases for fixed, writable Variables under
  `ns=2;s=Demo.Variants.Scalar.<Type>`.
- `MiloDemoDynamic` contains `Demo.Dynamic.<Type>` aliases for changing, read-only Variables under
  `ns=2;s=Demo.Dynamic.<Type>`. This category follows `address-space.dynamic.enabled`.

Both categories cover `Boolean`, `Int32`, `UInt32`, `Double`, `String`, and `DateTime`, for twelve
aliases with the default configuration.

Clients can search from the standard `Aliases` Object with `FindAlias` or the optional
`FindAliasVerbose` Method. Client-driven Add/Delete Methods are not enabled. The category
`LastChange` versions are stored at `aliases/versions.properties` under the active data directory.
Set `address-space.aliases.enabled=false` to remove the standard Aliases entry point and disable the
feature.

### Security

The server's application instance certificate is stored in the KeyStore at
`security/pki/certificates.pfx` under the active data directory. If the server starts and this file
doesn't exist it will generate a new one.

Issuer and trusted certificates are managed using the standard OPC UA PKI layout found at
`security/pki/issuer` and `security/pki/trusted` under the active data directory.

Certificates from untrusted clients can be found at `security/rejected` under the active data
directory after they have attempted to connect at least once. Moving a client certificate to
`security/pki/trusted/certs` will mark it "trusted" and allow the client to connect with security
enabled.

These directories are monitored by the server and changes will be picked up automatically.

### GDS application registration

Application registration is opt-in and independent of `gds-push-enabled`. Add overrides to
`data/server.conf` (or the active data directory):

```hocon
gds.registration {
  enabled = true
  endpoint-url = "opc.tcp://gds.example.com:58810/GlobalDiscoveryServer"
  security-policy = "Basic256Sha256"
  identity {
    username = "demo-registration"
    password = ${GDS_REGISTRATION_PASSWORD}
  }
  discovery-url-list = ["opc.tcp://demo.example.com:4840/milo"]
}
```

The GDS account needs DiscoveryAdmin or ApplicationAdmin permission. For a GDS that grants
registration to anonymous sessions, set `identity.type = "anonymous"`; `username` and `password`
are then ignored. Registration always uses SignAndEncrypt and the exact configured policy, including
for anonymous sessions. Supported policies are `Basic256Sha256`, `Aes128_Sha256_RsaOaep`, and
`Aes256_Sha256_RsaPss`. A failed username login never falls back to anonymous, and there is no
security downgrade fallback. These credentials authenticate to the GDS and do not change this server's incoming
`SecurityAdmin` account.

Before enabling registration, install the GDS certificate in `security/pki/trusted/certs`, or trust
its issuing CA and supply the issuer chain and current CRLs in the application PKI directories.
Trust the demo's application certificate at the GDS as well. Outgoing connections validate trust,
ApplicationUri, hostname, validity, and certificate usage even when `trust-all-certificates=true`.
Application certificates, including push replacements, must support client authentication as well
as server authentication. Rejected GDS certificates appear in `security/rejected`; approving trust
allows a later retry to succeed without restarting.

An empty `discovery-url-list` uses the running server's discovery URLs. Supply an override for NAT
or external DNS, and ensure those URLs are reachable from the GDS's clients. Duplicate URLs are
removed. URL paths are preserved, and advertised GDS endpoint hostnames are not rewritten.

After endpoint binding, a dedicated worker looks up this server's persistent ApplicationUri and
registers it if absent. A matching record is reused. Identity conflicts or multiple matching
records require operator action. Metadata differences report the field names; set
`gds.registration.update-existing=true` only when this configuration should manage the existing
record's names, discovery URLs, and capabilities. Identity conflicts cannot be overridden.

Defaults are `request-timeout=10 seconds`, `attempt-timeout=60 seconds`, and
`retry-interval=30 seconds`. Durations must be positive whole milliseconds; attempt timeout must
be at least request timeout, which is limited to UInt32 milliseconds. Transport, timeout, trust,
and temporary availability failures retry after the preceding attempt finishes. Authentication,
authorization, unsupported methods/endpoints, and record conflicts stop registration until
restart. Remote failures leave the demo server available. Repeated identical failures log at DEBUG
following the first WARN.

Successful registration saves credential-free JSON to `gds/registration.json` under the data
directory using an atomic replacement. The ApplicationId includes its namespace URI. Every startup
still looks up the remote record, so deleting or corrupting the local file cannot cause a duplicate
registration. A local persistence failure retries lookup and persistence. There is no periodic
renewal or unregister on shutdown; remove obsolete directory entries at the GDS. Remote deletion
is repaired on the next startup.

User configuration is merged with shipped defaults and resolved for environment substitutions.
Unresolved required substitutions fail configuration loading even in a disabled section. Keep
placeholder strings in disabled configurations or supply the referenced environment variables.
An absent or disabled registration section creates no registration client, worker, or state file.

Directory registration does not select the application for push management or configure the GDS's
credentials for incoming management sessions. Configure those separately at the GDS. Certificate
pulling, multiple GDS targets, and configuration reload are not supported.

### GDS push management

With `gds-push-enabled = true` (the default) the standard `ServerConfiguration` Object accepts
certificate and trust list updates from a Global Discovery Server or any other client holding the
`SecurityAdmin` role (user `SecurityAdmin`, password `password`), following the push model of
OPC 10000-12 7.10.

The server implements the OPC 10000-12 7.10.2 transaction model and reports `SupportsTransactions =
true`. `UpdateCertificate` and a TrustList `CloseAndUpdate` stage their changes in the calling
session's transaction and return `ApplyChangesRequired = true`. Nothing takes effect until that
session calls `ApplyChanges`, which installs every staged change, re-resolves the advertised
endpoints so `GetEndpoints` and new secure channels use the new certificates, and then, after a short
grace period, closes sessions whose secure channel was established with a certificate that was
replaced. Those clients must call `GetEndpoints` again and reconnect. `CancelChanges`, or closing the
session, discards staged changes. The `TransactionDiagnostics` Object reports the outcome of the
current or most recent transaction. No restart is needed after provisioning.

If an unexpected storage or endpoint-refresh failure occurs during `ApplyChanges`, successful
changes remain installed. The transaction ends and `TransactionDiagnostics` reports the failure;
`CancelChanges` cannot undo changes already applied. Read the installed certificates and trust
lists, then stage a new transaction to complete the update or restore the previous contents.

