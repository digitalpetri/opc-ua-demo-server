package com.digitalpetri.opcua.server;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.security.RsaSha256CertificateFactory;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;

public class RsaSha256CertificateFactoryImpl extends RsaSha256CertificateFactory {

  private static final Pattern IP_ADDR_PATTERN =
      Pattern.compile("^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");

  /**
   * Default RSA key length.
   *
   * <p>A key length of 2048 is required to support both deprecated and non-deprecated security
   * policies. Applications that don't need to support the old security policies can use a larger
   * key length, e.g. 4096.
   */
  private static final int RSA_KEY_LENGTH = 2048;

  private final String applicationUri;
  private final Supplier<Set<String>> hostnames;

  public RsaSha256CertificateFactoryImpl(String applicationUri, Supplier<Set<String>> hostnames) {
    this.applicationUri = applicationUri;
    this.hostnames = hostnames;
  }

  @Override
  public KeyPair createKeyPair(NodeId certificateTypeId) {
    if (!certificateTypeId.equals(NodeIds.RsaSha256ApplicationCertificateType)) {
      throw new UnsupportedOperationException("certificateTypeId: " + certificateTypeId);
    }

    try {
      return SelfSignedCertificateGenerator.generateRsaKeyPair(RSA_KEY_LENGTH);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected X509Certificate[] createRsaSha256CertificateChain(KeyPair keyPair) throws Exception {
    SelfSignedCertificateBuilder builder =
        new SelfSignedCertificateBuilder(keyPair)
            .setCommonName("Eclipse Milo OPC UA Demo Server")
            .setOrganization("digitalpetri")
            .setOrganizationalUnit("dev")
            .setLocalityName("Folsom")
            .setStateName("CA")
            .setCountryCode("US")
            .setApplicationUri(applicationUri);

    for (String hostname : hostnames.get()) {
      if (IP_ADDR_PATTERN.matcher(hostname).matches()) {
        builder.addIpAddress(hostname);
      } else {
        builder.addDnsName(hostname);
      }
    }

    return new X509Certificate[] {builder.build()};
  }
}
