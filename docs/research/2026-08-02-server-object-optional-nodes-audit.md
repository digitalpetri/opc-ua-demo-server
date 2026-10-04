---
date: "2026-08-02T10:11:58-07:00"
researcher: "Kevin Herron"
repository: "opc-ua-demo-server"
git_commit: "c700082f79f2400851ab10b51c7db27cc1a38d8a"
branch: "dev"
dirty: false
topic: "Server object optional node exposure and implementation audit"
tags: ["research", "opc-ua", "server-object", "milo"]
status: "complete"
last_updated: "2026-08-02T10:11:58-07:00"
last_updated_by: "Kevin Herron"
---

# Server Object Optional Node Exposure and Implementation Audit

This audit determines which well-known children of the OPC UA `Server` Object are mandatory,
conditionally required, useful to implement, or misleading in the current Milo demo server. It
also identifies the Milo loader behavior that creates unsupported instances and proposes a tested,
upgrade-resistant pruning and implementation policy.

## Executive summary

The demo server is exposing optional standard instances because Milo loads the complete generated
OPC UA namespace-0 NodeSet, not because Milo or the demo implements every represented feature. For
unconfigured methods, this is particularly misleading: generated method instances are marked
`Executable=true`, while `UaMethodNode` defaults to a handler that returns `Bad_NotImplemented`.

The recommended policy is:

1. Keep the standard type system, including ObjectTypes and MethodTypes, intact.
2. Keep every mandatory child of the `Server` Object, even when the standard explicitly permits it
   to be empty or to report no support.
3. Keep optional well-known instances only when the corresponding functionality or conformance
   unit is implemented.
4. Remove an unsupported instance at its root. Milo's `UaNode.delete()` recursively removes its
   child Nodes and References, so this also prevents direct NodeId access to orphaned descendants.

For the current demo server, the immediate disposition is:

| Disposition | Nodes |
| --- | --- |
| Implement | `Server.LocalTime`; namespace metadata for the application and demo namespaces |
| Keep | All mandatory `ServerType` children; `Server.Namespaces`; `GetMonitoredItems`; `ResendData` |
| Keep conditionally | `ServerConfiguration`, only when `gds-push-enabled=true`, after pruning its own unsupported optional children |
| Remove | `Server.UrisVersion`, `Server.EstimatedReturnTime`, `SetSubscriptionDurable`, `RequestServerStateChange`, `Dictionaries`, `Quantities`, `DefaultHAConfiguration`, `DefaultHEConfiguration`, `PublishSubscribe`, `Resources`, and `ServerLog` |

`Server.Namespaces` is not non-conformant merely because it does not mirror `NamespaceArray`; Part 5
explicitly permits incomplete membership. It is nevertheless useful and feasible to add truthful
metadata for the application and demo namespaces. The optional DataTypeTest namespace metadata can
also be restored after its two no-value static metadata properties are given good, truthful values.

## Scope and evidence

This audit covers:

- demo server commit `c700082f79f2400851ab10b51c7db27cc1a38d8a` on branch `dev`;
- the resolved Milo `1.2.0-SNAPSHOT` sources, matched to Eclipse Milo commit
  [`1801141fbf10c1f5b7df9eec9a5709358061df45`](https://github.com/eclipse-milo/milo/tree/1801141fbf10c1f5b7df9eec9a5709358061df45);
- Milo's generated OPC UA `1.05.07` namespace-0 loaders;
- the current OPC Foundation reference pages and OPC Reference MCP index for Parts 5, 8, 11,
  12, 14, 19, 22, and 26.

The checked-in `external/src` dependency snapshot was not treated as authoritative because it is
older than the currently resolved Milo source JAR. The local Milo `integration/1.2` checkout and the
resolved source JAR matched for the relevant loader and namespace files.

No server was started and no Maven command was run. This is a source-and-specification audit, not a
runtime conformance test.

## Why the unsupported nodes appear

Milo starts its namespace-0 namespace by loading all generated nodes and only then configuring a
subset of the standard Server Object. See
[`OpcUaNamespace`](https://github.com/eclipse-milo/milo/blob/1801141fbf10c1f5b7df9eec9a5709358061df45/opc-ua-sdk/sdk-server/src/main/java/org/eclipse/milo/opcua/sdk/server/namespaces/OpcUaNamespace.java#L86-L99)
and its unconditional
[`NodeLoader.loadNodes()` call](https://github.com/eclipse-milo/milo/blob/1801141fbf10c1f5b7df9eec9a5709358061df45/opc-ua-sdk/sdk-server/src/main/java/org/eclipse/milo/opcua/sdk/server/namespaces/OpcUaNamespace.java#L124-L139).
That loader includes well-known instances contributed by multiple specification parts, not only the
mandatory instance declarations of `ServerType`.

Milo then configures `NamespaceArray`, `ServerArray`, status, capabilities, limits, redundancy,
`GetMonitoredItems`, and `ResendData`. It does not configure the other optional direct Server
properties and methods. The current code also assigns `DateTime.now()` to `EstimatedReturnTime`,
which does not represent an actual recovery estimate. See
[`configureServerObject()`](https://github.com/eclipse-milo/milo/blob/1801141fbf10c1f5b7df9eec9a5709358061df45/opc-ua-sdk/sdk-server/src/main/java/org/eclipse/milo/opcua/sdk/server/namespaces/OpcUaNamespace.java#L141-L255).

Unconfigured method instances are more than cosmetic. `UaMethodNode` initializes its invocation
handler to
[`MethodInvocationHandler.NOT_IMPLEMENTED`](https://github.com/eclipse-milo/milo/blob/1801141fbf10c1f5b7df9eec9a5709358061df45/opc-ua-sdk/sdk-server/src/main/java/org/eclipse/milo/opcua/sdk/server/nodes/UaMethodNode.java#L44-L49),
but the generated standard method instances have `Executable` and `UserExecutable` set to `true`.
The address space therefore advertises callable functionality that returns `Bad_NotImplemented`.

The demo already follows the correct pruning pattern for `Aliases` and `Locations` in
[`OpcUaDemoServer.java`](../../src/main/java/com/digitalpetri/opcua/server/OpcUaDemoServer.java#L276-L277).
Milo's
[`UaNode.delete()`](https://github.com/eclipse-milo/milo/blob/1801141fbf10c1f5b7df9eec9a5709358061df45/opc-ua-sdk/sdk-server/src/main/java/org/eclipse/milo/opcua/sdk/server/nodes/UaNode.java#L310-L332)
recursively deletes children reached through `HasChild` subtypes, so deleting each unsupported root
is sufficient.

## Direct `ServerType` children

[OPC 10000-5, 6.3.1](https://reference.opcfoundation.org/specs/OPC-10000-5/6.3.1/)
defines the direct `ServerType` children. The table below is exhaustive for the current generated
`Server` instance. All NodeIds are namespace 0.

| Node | NodeId | Rule or condition | Current behavior | Decision |
| --- | ---: | --- | --- | --- |
| `ServerArray` | `i=2254` | Mandatory | Dynamic view of Milo's server table | Keep |
| `NamespaceArray` | `i=2255` | Mandatory | Dynamic view of Milo's namespace table | Keep |
| `UrisVersion` | `i=15004` | Optional; required with `SessionlessInvoke` | Null; no server-side `SessionlessInvoke` implementation was found | Remove |
| `ServerStatus` | `i=2256` | Mandatory | Configured, with dynamic `CurrentTime` | Keep |
| `ServiceLevel` | `i=2267` | Mandatory | Set to 255 | Keep |
| `Auditing` | `i=2994` | Mandatory | Set to false, consistent with no audit event implementation | Keep |
| `EstimatedReturnTime` | `i=12885` | Optional | Set to the configuration-time `DateTime.now()` | Remove |
| `LocalTime` | `i=17634` | Optional | Null | Implement dynamically |
| `ServerCapabilities` | `i=2268` | Mandatory | Mostly configured by Milo | Keep; separately verify profile claims |
| `ServerDiagnostics` | `i=2274` | Mandatory | Present with `EnabledFlag=false` | Keep |
| `VendorServerInfo` | `i=2295` | Mandatory even when empty | Empty | Keep |
| `ServerRedundancy` | `i=2296` | Mandatory even without redundancy | `RedundancySupport=None` | Keep |
| `Namespaces` | `i=11715` | Optional | Contains namespace-0 metadata only by default | Keep and populate where truthful |
| `GetMonitoredItems` | `i=11492` | Optional | Milo handler installed | Keep |
| `ResendData` | `i=12873` | Optional | Milo handler installed | Keep |
| `SetSubscriptionDurable` | `i=12749` | Optional | Executable but default `Bad_NotImplemented` handler | Remove |
| `RequestServerStateChange` | `i=12886` | Optional | Executable but default `Bad_NotImplemented` handler | Remove for now |

### Mandatory does not mean non-empty

Two mandatory nodes can look superficially similar to the unsupported optional folders:

- Part 5 requires `VendorServerInfo` even when no vendor-defined Objects exist beneath it.
- Part 5 requires `ServerRedundancy` even when the server has no redundancy; in that case,
  `RedundancySupport=None` is the intended representation.

They must not be removed. Likewise, a disabled `ServerDiagnostics` Object and `Auditing=false` are
truthful states of mandatory nodes, not evidence that the nodes are optional.

### `UrisVersion`

Part 5 defines `UrisVersion` as optional and makes it mandatory only when the server supports the
Part 4 `SessionlessInvoke` Service. Source searches found data types and generated model nodes for
Sessionless Invoke, but no Milo server service implementation. The property should therefore be
removed rather than left at `Bad_NoValue`.

If Sessionless Invoke is implemented later, `UrisVersion` must be restored together with logic that
monotonically updates it whenever `ServerArray` or `NamespaceArray` changes and provides a
consistent three-property snapshot.

### `EstimatedReturnTime`

Part 5 defines this as the expected time at which `ServerStatus.State` will return to `Running`.
Setting it once to the current time is not an estimate and becomes stale immediately. Remove it
unless a real lifecycle state machine owns it. If `RequestServerStateChange` is implemented later,
that same implementation should manage `EstimatedReturnTime`, `ServerStatus.State`,
`SecondsTillShutdown`, and `ShutdownReason` coherently.

### `LocalTime`

This node is optional, cheap to implement, and useful. Its value should be computed on each Value
read, not once at startup, because the offset and DST flag can change while the server runs.

Recommended value calculation:

1. Use an explicitly configured `ZoneId` if the demo adds one; otherwise use
   `ZoneId.systemDefault()` as the server-location zone.
2. Evaluate the zone rules at the current `Instant`.
3. Set `TimeZoneDataType.Offset` to the current total UTC offset in minutes.
4. Set `DaylightSavingInOffset` from `ZoneRules.isDaylightSavings(instant)`.

An `AttributeFilters.getValue(...)` filter on `Server.LocalTime` matches the existing dynamic
patterns used by Milo for `NamespaceArray`, `ServerArray`, and `ServerStatus.CurrentTime`.

### `SetSubscriptionDurable`

[Part 5, 9.3](https://reference.opcfoundation.org/specs/OPC-10000-5/9.3/) requires durable
subscriptions to preserve queued data and events through long client disconnects and server
restarts. Milo's ordinary in-memory subscription support does not satisfy this. This is not a
small method-handler omission; it requires durable storage, ownership and reconnect semantics, and
restart recovery. Remove the instance.

### `RequestServerStateChange`

[Part 5, 9.4](https://reference.opcfoundation.org/specs/OPC-10000-5/9.4/) requires administrative
authorization and coordinated state, return-time, countdown, shutdown-reason, shutdown, and
optional restart behavior. A reliable restart also depends on the process supervisor or deployment
environment. The method is implementable, but not as an isolated demo method handler. Remove it
until there is an explicit server lifecycle feature with tests for those behaviors.

## `Server.Namespaces` audit

[Part 5, 6.3.14](https://reference.opcfoundation.org/specs/OPC-10000-5/6.3.14/) explicitly says
clients must not assume all entries from `NamespaceArray` appear below `Server.Namespaces`. Missing
metadata entries are therefore not, by themselves, a conformance defect.

The current runtime namespace table is expected to contain:

| Index | Namespace | Source | Metadata status |
| ---: | --- | --- | --- |
| 0 | `http://opcfoundation.org/UA/` | Milo namespace-0 model | Present and populated |
| 1 | Per-process application URI | Milo `ServerNamespace` | Missing |
| 2 | `urn:opc:eclipse:milo:opc-ua-demo-server:namespace:demo` | `DemoNamespace` | Missing |
| 3 | `https://github.com/digitalpetri/DataTypeTest` when enabled | DataTypeTest NodeSet | Deliberately deleted at startup |

Milo reserves index 1 by adding the configured application URI in
[`ServerNamespace`](https://github.com/eclipse-milo/milo/blob/1801141fbf10c1f5b7df9eec9a5709358061df45/opc-ua-sdk/sdk-server/src/main/java/org/eclipse/milo/opcua/sdk/server/namespaces/ServerNamespace.java#L45-L53).
The demo assigns indexes 2 and optionally 3 in
[`OpcUaDemoServer.java`](../../src/main/java/com/digitalpetri/opcua/server/OpcUaDemoServer.java#L249-L259).

### Recommended application and demo metadata

[Part 5, 6.3.13](https://reference.opcfoundation.org/specs/OPC-10000-5/6.3.13/) requires every
exposed `NamespaceMetadataType` instance to contain all seven mandatory properties. It explicitly
allows null version and publication date values when the namespace has no formal values.

For the application and demo namespaces, create metadata instances with:

| Property | Recommended value |
| --- | --- |
| `NamespaceUri` | Exact URI from `NamespaceArray` |
| `NamespaceVersion` | Null String unless a formal namespace/model version is introduced |
| `NamespacePublicationDate` | Null DateTime unless a formal date is introduced |
| `IsNamespaceSubset` | `false` for the application namespace; `true` for the demo namespace because configuration flags can omit portions of it |
| `StaticNodeIdTypes` | Empty `IdType[]` because these are dynamic server namespaces |
| `StaticNumericNodeIdRange` | Empty `NumericRange[]` |
| `StaticStringNodeIdPattern` | Empty String |

The metadata Object BrowseName must be derived from the represented namespace as Part 5 specifies.
Add integration tests that browse `Server.Namespaces` and read all mandatory properties with a Good
status.

### DataTypeTest metadata

The current deletion in
[`DataTypeTestNamespace.java`](../../src/main/java/com/digitalpetri/opcua/server/namespace/test/DataTypeTestNamespace.java#L17-L22)
was added by [PR #41](https://github.com/digitalpetri/opc-ua-demo-server/pull/41) after CTT read
`Bad_NoValue` from `StaticNumericNodeIdRange` and `StaticStringNodeIdPattern`. Omitting the whole
metadata entry is explicitly permitted and is preferable to exposing mandatory properties with no
value.

The NodeSet nevertheless has enough information to make the entry truthful: it supplies URI,
version `1.0.0`, publication date, `IsNamespaceSubset=false`, and
`StaticNodeIdTypes=[Numeric]`. Because numeric ranges are ignored when numeric NodeIds are already
declared static, and the string pattern is irrelevant when String is not in `StaticNodeIdTypes`, the
two remaining Variables can be populated with the covering range `3003:6070` and an empty String.
The implementation must verify that all mandatory values read with Good status before retaining the
metadata subtree.

## Cross-part well-known Server children

The following instances are not declared as direct children in Part 5's `ServerType` table. Other
OPC UA parts add them as standard entry points under or organized by the well-known `Server` Object.
Loading the namespace-0 NodeSet creates them, but their presence still communicates support for the
associated model or conformance unit.

| Node | NodeId | Specification meaning or condition | Demo/Milo support | Decision |
| --- | ---: | --- | --- | --- |
| `Dictionaries` | `i=17594` | Optional entry point for referenced dictionary entries ([Part 19, 8.1](https://reference.opcfoundation.org/specs/OPC-10000-19/8.1/)) | No referenced dictionary hierarchy | Remove |
| `Quantities` | `i=32530` | Entry point for managed `QuantityType` and Unit Objects; `Data Access Quantities Base` CU ([Part 8, 6.2](https://reference.opcfoundation.org/specs/OPC-10000-8/6.2/)) | Demo has ordinary engineering-unit data but not the Quantities model | Remove |
| `DefaultHAConfiguration` | `i=32637` | Required only for historical data Nodes that lack their own referenced configuration ([Part 11, 5.7.3](https://reference.opcfoundation.org/specs/OPC-10000-11/5.7.3/)) | No history provider or historizing Variables | Remove |
| `DefaultHEConfiguration` | `i=32754` | Required only for historical event Nodes that lack their own referenced configuration (Part 11, 5.7.3) | Alarms/events exist, but event history does not | Remove |
| `PublishSubscribe` | `i=14443` | Root of PubSub configuration and metadata; has mandatory children and transport-profile data ([Part 14, 9.1.3.2](https://reference.opcfoundation.org/specs/OPC-10000-14/9.1.3.2/)) | No PubSub transport, engine, configuration model, or handlers | Remove |
| `Resources` | `i=24226` | Base Network Model physical/logical resource entry point; `BNM Entry Points` CU ([Part 22, 5.4.1](https://reference.opcfoundation.org/specs/OPC-10000-22/5.4.1/)) | No BNM resource model | Remove |
| `ServerLog` | `i=19372` | May exist when LogObjects are supported and must expose all available server log records ([Part 26, 7.2](https://reference.opcfoundation.org/specs/OPC-10000-26/7.2/)) | Logback output is not an OPC UA LogObject implementation | Remove |

Two distinctions are important:

- Exposing alarms and conditions does not imply Historical Event Access. `DefaultHEConfiguration`
  is about event history, not live events.
- Using engineering units on AnalogItems does not imply support for the newer Part 8 Quantities and
  Units information model.

## Conditional `ServerConfiguration` audit

`ServerConfiguration` (`i=12637`) is the one cross-part instance that the demo substantially
implements. [Part 12, 7.10.3](https://reference.opcfoundation.org/specs/OPC-10000-12/7.10.3/)
ties it to the `Push Model for Global Certificate and TrustList Management` conformance unit.

The current demo starts `ServerConfigurationObject` only when `gds-push-enabled=true`, but leaves
the generated instance exposed when the option is false. See
[`OpcUaDemoServer.java`](../../src/main/java/com/digitalpetri/opcua/server/OpcUaDemoServer.java#L261-L274).

The `CreateSigningRequest` `Nonce` can be incorporated without changing Milo. Milo's
`CertificateFactory` interface has no entropy parameter, so the demo factory provides a dedicated
overload that seeds a fresh `SecureRandom` from the platform before mixing in the caller's nonce.
If a replacement certificate factory cannot honour that contract, the demo logs a warning and
falls back to the standard Milo `CertificateFactory#createKeyPair(NodeId)` API.

### When push management is disabled

Delete `NodeIds.ServerConfiguration` recursively. Leaving the root in place exposes mandatory
methods and properties with no implementation or value.

### When push management is enabled

Keep the root and the four implemented mandatory methods:

- `UpdateCertificate`;
- `ApplyChanges`;
- `CreateSigningRequest`;
- `GetRejectedList`.

The TrustList and available certificate-group integrations should also remain. The existing code
already removes certificate groups not backed by Milo's `CertificateManager`.

Then correct or prune the following descendants:

| Descendant | Current state | Decision |
| --- | --- | --- |
| `ApplicationUri`, `ProductUri`, `ApplicationType` | Optional instance properties loaded with null values | Populate from `OpcUaServerConfig`/`BuildInfo` and `ApplicationType.Server`, or delete; populating is more useful |
| `ServerCapabilities` | Mandatory; set to `new String[] {""}` | Replace with valid Annex-D identifiers `DA` and `AC`; do not advertise `RCP` for this pure Server's outbound Reverse Connect initiation |
| `SupportedPrivateKeyFormats` | Mandatory; `PEM`, `PFX` | Keep |
| `MaxTrustListSize` | Mandatory; 0 | Keep if 0 correctly means no declared limit for the implementation; otherwise calculate the real limit |
| `MulticastDnsEnabled` | Mandatory; false | Keep |
| `HasSecureElement` | Optional; false | Keep or remove; false is truthful |
| `CancelChanges` | Optional, executable, no handler | Remove unless real transaction rollback is implemented |
| `ResetToServerDefaults` | Optional, executable, no handler | Remove |
| `TransactionDiagnostics` | Optional, no transaction model | Remove |
| `ConfigurationFile` | Optional, no configuration-file implementation | Remove |

The valid server capability identifiers are defined by
[Part 12 Annex D](https://reference.opcfoundation.org/specs/OPC-10000-12/annex-d/). An empty String
is not a capability identifier. `RCP` applies to Clients and Client/Server applications that accept
Server-initiated reverse connections, not to a pure Server that initiates outbound connections. Do
not advertise `RCP`, `HD`, `HE`, `PUB`, or `PSC` after the corresponding unsupported capabilities
are excluded.

## Recommended ownership between Milo and the demo

### Milo SDK

Milo owns enough implementation knowledge to avoid exposing optional instances that the SDK itself
does not support. A robust upstream design would either:

- load only mandatory standard instances by default and opt in optional well-known instances; or
- run a post-load pruning policy before the namespace becomes visible, with application hooks for
  optional features.

At minimum, Milo can safely prune its unimplemented direct `ServerType` properties and methods by
default. Cross-part roots should be opt-in because their runtime engines are not provided merely by
the generated model classes.

### Demo server

The demo owns deployment- and application-specific decisions:

- selecting the local time zone and implementing `LocalTime`;
- describing its application/demo/DataTypeTest namespaces;
- opting into and configuring `ServerConfiguration`;
- deciding which Annex-D capabilities it advertises;
- opting into future history, PubSub, LogObject, or BNM implementations.

Until Milo has an opt-in mechanism, place one explicit `configureStandardServerObject()` or
`pruneUnsupportedStandardInstances()` step beside the existing `Aliases` and `Locations` deletion.
Keep the list centralized so upgrades to Milo's generated NodeSet can be reviewed in one place.

## Proposed implementation sequence

### 1. Prune the unsupported roots

Add a small helper that resolves each well-known NodeId through `AddressSpaceManager` and invokes
`UaNode.delete()`. Delete:

- `Server_UrisVersion`;
- `Server_EstimatedReturnTime`;
- `Server_SetSubscriptionDurable`;
- `Server_RequestServerStateChange`;
- `Dictionaries`;
- `Quantities`;
- `DefaultHAConfiguration`;
- `DefaultHEConfiguration`;
- `PublishSubscribe`;
- `Resources`;
- `ServerLog`;
- `ServerConfiguration` when push management is disabled.

This should happen after Milo has loaded namespace 0 and before clients can browse the server.

### 2. Implement `LocalTime`

Install a dynamic Value filter and test both a fixed-offset zone and a DST-observing zone. Prefer an
injectable zone/clock helper so the DST test does not depend on the machine's wall clock.

### 3. Add namespace metadata

Add complete application and demo metadata entries. Restore the DataTypeTest metadata after its two
no-value properties have been populated and the CTT regression is covered.

### 4. Tighten `ServerConfiguration`

Conditionally delete the root, populate identity/capability values when enabled, and delete its
unimplemented optional methods and Objects.

### 5. Consider an upstream Milo change

Move generic pruning defaults or an optional-instance policy into Milo after the demo behavior is
covered by tests. Do not make generated NodeSet editing the long-term customization mechanism;
regeneration would reintroduce the instances.

## Verification criteria for implementation

1. Add an integration test that browses the direct and organized children of `Server` and asserts
   the exact supported set. Also assert removed root and representative descendant NodeIds return
   `Bad_NodeIdUnknown`.
2. Assert `GetMonitoredItems` and `ResendData` remain executable and callable, while no exposed
   standard method retains `MethodInvocationHandler.NOT_IMPLEMENTED`.
3. Read `LocalTime` and verify offset minutes and DST flag against an injected `ZoneId` and `Clock`.
4. Browse `Server.Namespaces`; read all seven mandatory properties of every returned metadata
   Object with Good status.
5. Test both `gds-push-enabled=false` and `true`. When false, `ServerConfiguration` must be absent.
   When true, its mandatory properties must have Good values, its mandatory methods must be
   implemented, and unsupported optional descendants must be absent.
6. Rerun the focused CTT `Base Information / Base Info Core Structure 2 / 001.js` coverage that
   previously found the DataTypeTest metadata problem.
7. Follow the repository verification sequence: formatting, relevant focused tests, and
   `mise exec -- mvn -q clean verify`.

## Open questions and follow-up risks

- The hard-coded `StandardUA2022` entry in Milo's `ServerProfileArray` should be audited separately
  against the actual supported service and conformance-unit set. Pruning optional instances makes
  the address space more truthful but does not by itself prove the advertised profile.
- `MaxHistoryContinuationPoints` and history-related operation limits remain in the mandatory
  `ServerCapabilities` structure even though the demo has no history provider. Their exact expected
  values should be checked during the profile/limits audit; this does not justify keeping history
  configuration roots.
- Decide whether the server time zone should be explicit configuration. The JVM default is a
  defensible fallback, but container deployments frequently default to UTC regardless of the
  operator's intended server location.
- `RCP` must not be advertised for this pure Server's outbound reverse-connect role. `DA` and `AC`
  are directly evidenced by the current demo address space; history and PubSub identifiers are not.

## Source index

### Repository and Milo sources

- [`OpcUaDemoServer.java`](../../src/main/java/com/digitalpetri/opcua/server/OpcUaDemoServer.java)
- [`ServerConfigurationObject.java`](../../src/main/java/com/digitalpetri/opcua/server/objects/ServerConfigurationObject.java)
- [`DemoNamespace.java`](../../src/main/java/com/digitalpetri/opcua/server/namespace/demo/DemoNamespace.java)
- [`DataTypeTestNamespace.java`](../../src/main/java/com/digitalpetri/opcua/server/namespace/test/DataTypeTestNamespace.java)
- [`DataTypeTest.NodeSet.xml`](../../src/main/resources/DataTypeTest.NodeSet.xml)
- [Eclipse Milo `OpcUaNamespace`](https://github.com/eclipse-milo/milo/blob/1801141fbf10c1f5b7df9eec9a5709358061df45/opc-ua-sdk/sdk-server/src/main/java/org/eclipse/milo/opcua/sdk/server/namespaces/OpcUaNamespace.java)
- [Eclipse Milo `UaMethodNode`](https://github.com/eclipse-milo/milo/blob/1801141fbf10c1f5b7df9eec9a5709358061df45/opc-ua-sdk/sdk-server/src/main/java/org/eclipse/milo/opcua/sdk/server/nodes/UaMethodNode.java)
- [Eclipse Milo generated `ObjectNodeLoader`](https://github.com/eclipse-milo/milo/blob/1801141fbf10c1f5b7df9eec9a5709358061df45/opc-ua-sdk/sdk-server/src/main/java/org/eclipse/milo/opcua/sdk/server/namespaces/loader/ObjectNodeLoader.java)
- [Eclipse Milo generated `VariableNodeLoader`](https://github.com/eclipse-milo/milo/blob/1801141fbf10c1f5b7df9eec9a5709358061df45/opc-ua-sdk/sdk-server/src/main/java/org/eclipse/milo/opcua/sdk/server/namespaces/loader/VariableNodeLoader.java)
- [Eclipse Milo generated `MethodNodeLoader`](https://github.com/eclipse-milo/milo/blob/1801141fbf10c1f5b7df9eec9a5709358061df45/opc-ua-sdk/sdk-server/src/main/java/org/eclipse/milo/opcua/sdk/server/namespaces/loader/MethodNodeLoader.java)

### OPC UA specifications

- [Part 5, 6.3.1 — ServerType](https://reference.opcfoundation.org/specs/OPC-10000-5/6.3.1/)
- [Part 5, 6.3.13 — NamespaceMetadataType](https://reference.opcfoundation.org/specs/OPC-10000-5/6.3.13/)
- [Part 5, 6.3.14 — NamespacesType](https://reference.opcfoundation.org/specs/OPC-10000-5/6.3.14/)
- [Part 5, 9.3 — SetSubscriptionDurable](https://reference.opcfoundation.org/specs/OPC-10000-5/9.3/)
- [Part 5, 9.4 — RequestServerStateChange](https://reference.opcfoundation.org/specs/OPC-10000-5/9.4/)
- [Part 8, 6.2 — Quantities entry point](https://reference.opcfoundation.org/specs/OPC-10000-8/6.2/)
- [Part 11, 5.7.3 — Default historical configuration](https://reference.opcfoundation.org/specs/OPC-10000-11/5.7.3/)
- [Part 12, 7.10.3 — ServerConfigurationType](https://reference.opcfoundation.org/specs/OPC-10000-12/7.10.3/)
- [Part 12, Annex D — Server Capability Identifiers](https://reference.opcfoundation.org/specs/OPC-10000-12/annex-d/)
- [Part 14, 9.1.3.2 — PublishSubscribeType](https://reference.opcfoundation.org/specs/OPC-10000-14/9.1.3.2/)
- [Part 19, 8.1 — Dictionaries Object](https://reference.opcfoundation.org/specs/OPC-10000-19/8.1/)
- [Part 22, 5.4.1 — Resources Folder](https://reference.opcfoundation.org/specs/OPC-10000-22/5.4.1/)
- [Part 26, 7.2 — ServerLog](https://reference.opcfoundation.org/specs/OPC-10000-26/7.2/)
