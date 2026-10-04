/**
 * Demo-server-side Reverse Connect configuration.
 *
 * <p>This package owns the immutable configuration model parsed from the {@code reverse-connect}
 * section of {@code server.conf}. Parsed targets feed Milo's SDK-level {@link
 * org.eclipse.milo.opcua.sdk.server.reverse.ReverseConnectTargetManager}, which validates targets
 * at startup, schedules outbound connection attempts, and applies retry policy.
 *
 * <p>Parsing is fail-fast: any invalid target aborts configuration loading with an {@link
 * java.lang.IllegalArgumentException} that identifies the offending {@code
 * reverse-connect.target-list} index and field, along with the expected value shape.
 */
package com.digitalpetri.opcua.server.reverse;
