package com.digitalpetri.opcua.server;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.security.KeyPair;
import java.util.List;
import java.util.Set;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.junit.jupiter.api.Test;

class DemoCertificateFactoryTest {

  // Part 12 §7.10.10: exercise every certificate type offered by the demo through its
  // entropy-aware regeneration path, including provider-sensitive algorithms.
  @Test
  void additionalEntropyKeyGenerationSupportsEveryConfiguredCertificateType() throws Exception {
    var factory = new DemoCertificateFactory("urn:test:application", Set::of);

    List<NodeId> certificateTypeIds =
        List.of(
            NodeIds.RsaSha256ApplicationCertificateType,
            NodeIds.EccNistP256ApplicationCertificateType,
            NodeIds.EccNistP384ApplicationCertificateType,
            NodeIds.EccBrainpoolP256r1ApplicationCertificateType,
            NodeIds.EccBrainpoolP384r1ApplicationCertificateType,
            NodeIds.EccCurve25519ApplicationCertificateType,
            NodeIds.EccCurve448ApplicationCertificateType);

    for (NodeId certificateTypeId : certificateTypeIds) {
      KeyPair keyPair = factory.createKeyPair(certificateTypeId, new byte[32]);

      assertNotNull(keyPair.getPublic(), () -> certificateTypeId + " public key");
      assertNotNull(keyPair.getPrivate(), () -> certificateTypeId + " private key");
    }
  }
}
