![Docker Pulls](https://img.shields.io/docker/pulls/digitalpetri/opc-ua-demo-server)
 ![Docker Image Version (tag)](https://img.shields.io/docker/v/digitalpetri/opc-ua-demo-server/1.0.0)

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

## Building

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

### Maven + JDK 25

**Using JDK 25**, run `mvn clean package` in the root directory.

An executable JAR file will be created in the `target` directory. This JAR file can be run with
`java -jar target/opc-ua-demo-server.jar`.

## Configuration

### Server

On startup the server loads its configuration from `/app/data/server.conf`. If it doesn't exist, the
default configuration from `src/main/resources/default-server.conf` will be copied to that location.

The server configuration file is in HOCON format and its configuration keys and values are
documented with comments.

### Security

The server's application instance certificate is stored in the KeyStore at
`/app/data/security/pki/certificates.pfx`. If the server starts and this file doesn't exist it will
generate a new one.

Issuer and trusted certificates are managed using the standard OPC UA PKI layout found at
`/app/data/security/pki/issuer` and `/app/data/security/pki/trusted`.

Certificates from untrusted clients can be found at `/app/data/security/rejected` after they have
attempted to connect at least once. Moving a client certificate to
`/app/data/security/pki/trusted/certs` will mark it "trusted" and allow the client to connect with
security enabled.

These directories are monitored by the server and changes will be picked up automatically.
