# Reverse Connect Feature Spec

Add server-side OPC UA Reverse Connect support to the demo server.

This spec captures the desired user-facing behavior and acceptance criteria. It is intended to be
easy to turn into a Linear issue and intentionally avoids implementation planning.

* * *

## Problem

OPC UA Reverse Connect allows the server to initiate the underlying TCP connection to an OPC UA
client that is listening for reverse connections. The demo server does not currently support acting
as a Reverse Connect server.

Administrators need the demo server to open outbound Reverse Connect sockets for demos, testing,
and firewall/NAT scenarios where inbound client-to-server connections are unavailable. Static
`server.conf` settings are the V1 mechanism for declaring those client listener targets.

* * *

## Scope

V1 covers server-side Reverse Connect support with static targets loaded from `server.conf`.

The feature should support:

- one or more client reverse listener targets.
- enabling or disabling individual targets.
- the client listener URL to dial.
- the server endpoint URL advertised in `ReverseHello`.
- registration/retry timing.
- TCP connect timeout timing.

* * *

## User-Facing Behavior

When Reverse Connect targets are configured, the server initiates outbound OPC UA TCP connections
to the configured client reverse listeners. For each enabled target, the server sends a
`ReverseHello` advertising the configured server endpoint URL.

After `ReverseHello`, normal OPC UA connection establishment, SecureChannel negotiation,
certificate validation, endpoint security policy selection, and session activation still apply.

If a connection attempt fails, times out, receives an error from the client, or the reverse-opened
channel closes, the server schedules another attempt after the configured registration period.

When no targets are configured, server behavior is unchanged.

* * *

## Proposed Configuration

Reverse Connect targets are declared in a top-level `reverse-connect` section in `server.conf`.

```hocon
# Server-initiated Reverse Connect targets.
reverse-connect {
  target-list = [
    # {
    #   # Register this target and schedule outbound connection attempts.
    #   enabled = true
    #
    #   # OPC UA client reverse listener the server should dial.
    #   client-listener-url = "opc.tcp://client.example.com:48060"
    #
    #   # Server endpoint advertised in ReverseHello. Must match one of this server's endpoints.
    #   endpoint-url = "opc.tcp://localhost:4840/milo"
    #
    #   # Retry/re-registration interval after failed attempts or closed reverse channels.
    #   registration-period = 30 seconds
    #
    #   # TCP connect timeout for each outbound attempt.
    #   connect-timeout = 5 seconds
    # }
  ]
}
```

* * *

## Configuration Fields

| Field | Type | Default | Required | Description |
| --- | --- | --- | --- | --- |
| `reverse-connect.target-list` | list of objects | `[]` | no | Reverse Connect targets configured for the server. An empty list means no outbound Reverse Connect attempts are scheduled. |
| `enabled` | boolean | `true` | no | Whether this target should schedule outbound connection attempts. Disabled targets remain in the configuration but do not connect. |
| `client-listener-url` | string | none | yes | `opc.tcp://host:port` URL for the OPC UA client reverse listener the server should dial. |
| `endpoint-url` | string | none | yes | Server endpoint URL advertised in `ReverseHello`. This must match one of the server's configured OPC UA TCP endpoints. |
| `registration-period` | duration | `30 seconds` | no | Retry/re-registration interval after failed attempts or closed reverse channels. |
| `connect-timeout` | duration | `5 seconds` | no | TCP connection timeout for each outbound attempt. |

Duration values should use HOCON duration syntax, such as `30 seconds`, `30s`, or `5000 ms`.

* * *

## Requirements

### P0

- Existing configurations must continue to work when `reverse-connect` is absent.
- `reverse-connect.target-list = []` must mean no outbound Reverse Connect attempts are scheduled.
- Each target must support `enabled`, `client-listener-url`, `endpoint-url`,
  `registration-period`, and `connect-timeout`.
- `enabled` must default to `true` when omitted.
- `registration-period` must default to `30 seconds` when omitted.
- `connect-timeout` must default to `5 seconds` when omitted.
- Enabled targets must schedule outbound Reverse Connect attempts to `client-listener-url`.
- Disabled targets must be accepted but must not schedule outbound Reverse Connect attempts.
- The server must advertise the configured `endpoint-url` in `ReverseHello`.
- `endpoint-url` must match one of the server's configured OPC UA TCP endpoints.
- Once the reverse connection proceeds beyond `ReverseHello`, the server must use its normal
  configured endpoints, security policies, trust lists, and identity validation.
- `registration-period` and `connect-timeout` must accept HOCON duration syntax.
- Invalid target configuration must fail startup with a clear error identifying the target and
  field.

### P1

- The default configuration should include a commented example target.
- Error messages should explain the expected value shape for invalid URLs and durations.
- Logs should make it possible to see whether a configured target is enabled, disabled, attempting
  connection, connected, or retrying.

### P2

- None identified for V1.

* * *

## Validation

Startup should fail with a clear configuration error when a target is invalid.

Validation should reject:

- missing `client-listener-url`.
- missing `endpoint-url`.
- URLs that are not `opc.tcp`.
- URLs with no host.
- `endpoint-url` values that do not match a configured server OPC UA TCP endpoint.
- non-positive `registration-period` values.
- non-positive `connect-timeout` values.
- duration values that cannot be parsed by the HOCON config library.

* * *

## Acceptance Criteria

- `default-server.conf` documents the `reverse-connect.target-list` section with a commented sample
  target.
- Existing configurations continue to work without adding any Reverse Connect settings.
- A valid enabled target causes the server to schedule outbound Reverse Connect attempts.
- A valid disabled target is accepted but does not schedule outbound attempts.
- Invalid target configuration fails startup with a specific, actionable error message.
- `registration-period` and `connect-timeout` accept HOCON duration syntax.
- The server relies on the normal configured endpoints, security policies, trust lists, and identity
  validation once a reverse connection proceeds beyond `ReverseHello`.

* * *

## Non-Goals

- Runtime management of targets.
- Live reload of `server.conf` changes.
- A separate `paused` state in `server.conf`.
- Configurable retry policy selection beyond `registration-period`.
- GDS lookup for client endpoint discovery.
- Client-side Reverse Connect listener support in this demo server.
- Changes to certificate trust behavior, endpoint security policies, or user authentication.
