package com.digitalpetri.opcua.server.namespace.demo;

import org.eclipse.milo.opcua.sdk.server.AddressSpaceComposite;
import org.eclipse.milo.opcua.sdk.server.ManagedAddressSpaceFragmentWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.sampling.ReadAccessPolicy;
import org.eclipse.milo.opcua.sdk.server.sampling.SamplingManagerConfig;

/**
 * Base class for the demo server's address space fragments.
 *
 * <p>Fragments sample with Milo's default configuration, except that the read access of each
 * monitored item is answered from the server's read access cache instead of checked again every
 * sampling cycle. Nothing a read access check depends on changes after the server starts: a
 * Session's roles are a fixed function of its identity, and every Node, with its role permissions
 * and access levels, is created at startup. The SDK drops a Session's cached answers when its
 * identity or endpoint changes, when its Subscriptions are transferred, and when it closes.
 *
 * <p>Code that changes roles, permissions, or access levels at runtime must call {@code
 * AccessControlManager.invalidateReadAccess}, or monitored items keep enforcing the old answer.
 */
public abstract class DemoAddressSpaceFragment extends ManagedAddressSpaceFragmentWithLifecycle {

  protected DemoAddressSpaceFragment(OpcUaServer server, AddressSpaceComposite composite) {
    super(server, composite);
  }

  @Override
  protected SamplingManagerConfig samplingManagerConfig() {
    return SamplingManagerConfig.defaults().withReadAccessPolicy(ReadAccessPolicy.cached());
  }
}
