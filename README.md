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

