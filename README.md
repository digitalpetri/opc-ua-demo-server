# Eclipse Milo OPC UA Demo Server

This is a standalone OPC UA demo server built using [Eclipse Milo](https://github.com/eclipse-milo/milo).

An internet-facing instance of this demo server is accessible at `opc.tcp://milo.digitalpetri.com:62541/milo`.

It accepts both unsecured and secured connections. Before connecting with security you must upload your client's DER-encoded X509 certificate using the form at http://milo.digitalpetri.com.

Authenticate anonymously or with one of the following credential pairs:
- `user1` / `password`
- `user2` / `password`
- `admin` / `password`

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

In order to have access to the `server.conf` file and security directories, you may want to mount a volume mapped to the container's `/app/data` directory:
```bash
docker run --rm -it -p 4840:4840 -v /tmp/opc-ua-demo-server-data:/app/data opc-ua-demo-server
```

### Maven + JDK 21+
**Using JDK 21+**, run `mvn clean package` in the root directory.

An executable JAR file will be created in the `target` directory. This JAR file can be run with `java -jar target/opc-ua-demo-server.jar`.

## Configuration

### Server 

TODO explain `server.conf`

### Security

The server's application instance certificate is stored in the KeyStore at `/app/data/security/pki/certificates.pfx`. If the server starts and this file doesn't exist it will generate a new one.

Issuer and trusted certificates are managed using the standard OPC UA PKI layout found at `/app/data/security/pki/issuer` and `/app/data/security/pki/trusted`.

Certificates from untrusted clients can be found at `/app/data/security/rejected` after they have attempted to connect at least once. Moving a client certificate to `/app/data/security/pki/trusted/certs` will mark it "trusted" and allow the client to connect with security enabled.

These directories are monitored by the server and changes will be picked up automatically.