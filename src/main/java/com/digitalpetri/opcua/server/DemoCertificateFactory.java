package com.digitalpetri.opcua.server;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.eclipse.milo.opcua.stack.core.security.AbstractCertificateFactory;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;

/** Creates the RSA and ECC application certificates used by the demo server. */
public class DemoCertificateFactory extends AbstractCertificateFactory {

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

  /**
   * Create a demo certificate factory.
   *
   * @param applicationUri the application URI placed in generated certificates.
   * @param hostnames the DNS names and IP addresses placed in generated certificates.
   */
  public DemoCertificateFactory(String applicationUri, Supplier<Set<String>> hostnames) {
    this.applicationUri = Objects.requireNonNull(applicationUri, "applicationUri must not be null");
    this.hostnames = Objects.requireNonNull(hostnames, "hostnames must not be null");
  }

  @Override
  protected KeyPair createRsaSha256KeyPair() throws NoSuchAlgorithmException {
    return SelfSignedCertificateGenerator.generateRsaKeyPair(RSA_KEY_LENGTH);
  }

  @Override
  protected X509Certificate[] createRsaSha256CertificateChain(KeyPair keyPair) throws Exception {
    return buildChain(new SelfSignedCertificateBuilder(keyPair));
  }

  @Override
  protected X509Certificate[] createEccNistP256CertificateChain(KeyPair keyPair) throws Exception {
    return buildChain(SelfSignedCertificateBuilder.forEccApplicationCertificate(keyPair));
  }

  @Override
  protected X509Certificate[] createEccNistP384CertificateChain(KeyPair keyPair) throws Exception {
    return buildChain(SelfSignedCertificateBuilder.forEccApplicationCertificate(keyPair));
  }

  @Override
  protected X509Certificate[] createEccBrainpoolP256r1CertificateChain(KeyPair keyPair)
      throws Exception {

    return buildChain(SelfSignedCertificateBuilder.forEccApplicationCertificate(keyPair));
  }

  @Override
  protected X509Certificate[] createEccBrainpoolP384r1CertificateChain(KeyPair keyPair)
      throws Exception {

    return buildChain(SelfSignedCertificateBuilder.forEccApplicationCertificate(keyPair));
  }

  @Override
  protected X509Certificate[] createEccCurve25519CertificateChain(KeyPair keyPair)
      throws Exception {
    return buildChain(SelfSignedCertificateBuilder.forEccApplicationCertificate(keyPair));
  }

  @Override
  protected X509Certificate[] createEccCurve448CertificateChain(KeyPair keyPair) throws Exception {
    return buildChain(SelfSignedCertificateBuilder.forEccApplicationCertificate(keyPair));
  }

  private X509Certificate[] buildChain(SelfSignedCertificateBuilder builder) throws Exception {
    builder
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
