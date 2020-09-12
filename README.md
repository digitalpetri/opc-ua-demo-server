# Eclipse Milo OPC UA Demo Server

This is a standalone OPC UA demo server built using [Eclipse Milo](https://github.com/eclipse/milo).

An internet-facing instance of this demo server is accessible at `opc.tcp://milo.digitalpetri.com:62541/milo`.

It accepts both unsecured and secured connections. Before connecting with security you must upload your client's DER-encoded X509 certificate using the form at http://milo.digitalpetri.com.

Authenticate anonymously or with one of the following credential pairs:
- `user1` / `password`
- `user2` / `password`
- `admin` / `password`

## Building

**Using JDK 11+**, run `./gradlew dist` in the root directory.

A distribution for your platform will be packaged into a zip file at `build/milo-demo-server.zip`.

## Running

Build your own distribution or download the distribution for your platform from the GitHub release section.

The packaged distribution includes everything needed to run the demo server, including a Java runtime. Unzip the package for your platform, change into the target directory, and then run `bin/milo-demo-server` (on Linux or macOS) or `bin/milo-demo-server.bat` (on Windows).

### Example

- `unzip milo-demo-server.zip`
- `cd milo-demo-server/`
- `bin/milo-demo-server`

```
.
. (logging snipped for brevity)
.
[main] INFO  c.d.opcua.server.DemoServer - Eclipse Milo Demo Server started in 2400ms
[main] INFO  c.d.opcua.server.DemoServer - 	config dir:	/Users/kevin/Library/Preferences/com.digitalpetri.Milo-Demo-Server
[main] INFO  c.d.opcua.server.DemoServer - 	data dir:	/Users/kevin/Library/Application Support/com.digitalpetri.Milo-Demo-Server
```

When the server starts it will log the endpoint URLs it can be reached at and then finally the location of the config and data directories being used. These will vary by platform.

## Configuration

### Server

The server can be configured by modifying the JSON file at `$configDir/server.json`:
```json
{
  "serverConfig": {
    "bindAddressList": [
      "0.0.0.0"
    ],
    "bindPort": 62541,
    "endpointAddressList": [
      "<hostname>",
      "<localhost>"
    ],
    "securityPolicyList": [
      "None",
      "Basic128Rsa15",
      "Basic256",
      "Basic256Sha256",
      "Aes128_Sha256_RsaOaep",
      "Aes256_Sha256_RsaPss"
    ],
    "certificateHostnameList": [
      "<0.0.0.0>"
    ],
    "gdsPushEnabled": false,
    "registration": {
      "enabled": false,
      "frequency": 60000,
      "endpointUrl": "opc.tcp://localhost:4840/UADiscovery"
    }
  }
}
```

### Security

The server's application instance certificate is stored in the KeyStore at `$dataDir/security/certificates.pfx`. If the server starts and this file doesn't exist it will generate a new one.

Issuer and trusted certificates are managed using the standard OPC UA PKI layout found at `$dataDir/security/pki`.

Certificates from untrusted clients can be found at `$dataDir/security/pki/rejected` after they have attempted to connect at least once. Moving a client certificate to `$dataDir/security/pki/trusted/certs` will mark it "trusted" and allow the client to connect with security enabled. 

These directories are monitored by the server and changes will be picked up automatically.
