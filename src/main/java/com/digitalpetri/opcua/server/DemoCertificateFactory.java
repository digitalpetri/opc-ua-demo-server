package com.digitalpetri.opcua.server;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.NamedParameterSpec;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.security.AbstractCertificateFactory;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
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

  /**
   * Create a key pair using system entropy supplemented by caller-provided entropy.
   *
   * <p>This overload supports the {@code Nonce} supplied to the Part 12 CreateSigningRequest Method
   * without requiring a change to Milo's {@code CertificateFactory} interface. The {@link
   * SecureRandom} is forced to seed itself from its configured system source before {@link
   * SecureRandom#setSeed(byte[])} mixes in the additional entropy, so caller-controlled bytes never
   * replace system entropy.
   *
   * @param certificateTypeId the certificate type for the new key pair.
   * @param additionalEntropy at least 32 bytes of additional entropy.
   * @return the generated key pair.
   * @throws GeneralSecurityException if the key pair cannot be generated.
   */
  public KeyPair createKeyPair(NodeId certificateTypeId, byte[] additionalEntropy)
      throws GeneralSecurityException {

    if (additionalEntropy == null || additionalEntropy.length < 32) {
      throw new IllegalArgumentException("additionalEntropy must contain at least 32 bytes");
    }

    SecureRandom secureRandom = createSecureRandom(additionalEntropy);

    if (NodeIds.RsaSha256ApplicationCertificateType.equals(certificateTypeId)) {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(RSA_KEY_LENGTH, secureRandom);
      return generator.generateKeyPair();
    } else if (NodeIds.EccNistP256ApplicationCertificateType.equals(certificateTypeId)) {
      return generateEcKeyPair("secp256r1", secureRandom, false);
    } else if (NodeIds.EccNistP384ApplicationCertificateType.equals(certificateTypeId)) {
      return generateEcKeyPair("secp384r1", secureRandom, false);
    } else if (NodeIds.EccBrainpoolP256r1ApplicationCertificateType.equals(certificateTypeId)) {
      return generateEcKeyPair("brainpoolP256r1", secureRandom, true);
    } else if (NodeIds.EccBrainpoolP384r1ApplicationCertificateType.equals(certificateTypeId)) {
      return generateEcKeyPair("brainpoolP384r1", secureRandom, true);
    } else if (NodeIds.EccCurve25519ApplicationCertificateType.equals(certificateTypeId)) {
      return generateEdKeyPair("Ed25519", NamedParameterSpec.ED25519, secureRandom);
    } else if (NodeIds.EccCurve448ApplicationCertificateType.equals(certificateTypeId)) {
      return generateEdKeyPair("Ed448", NamedParameterSpec.ED448, secureRandom);
    } else {
      throw new UnsupportedOperationException("certificateTypeId: " + certificateTypeId);
    }
  }

  private static SecureRandom createSecureRandom(byte[] additionalEntropy) {
    var secureRandom = new SecureRandom();
    byte[] systemSeedProbe = new byte[32];
    byte[] entropyCopy = Arrays.copyOf(additionalEntropy, additionalEntropy.length);

    try {
      // Force platform self-seeding before setSeed() supplements that state with caller entropy.
      secureRandom.nextBytes(systemSeedProbe);
      secureRandom.setSeed(entropyCopy);
    } finally {
      Arrays.fill(systemSeedProbe, (byte) 0);
      Arrays.fill(entropyCopy, (byte) 0);
    }

    return secureRandom;
  }

  private static KeyPair generateEcKeyPair(
      String curveName, SecureRandom secureRandom, boolean useBouncyCastle)
      throws GeneralSecurityException {

    KeyPairGenerator generator =
        useBouncyCastle
            ? KeyPairGenerator.getInstance("EC", new BouncyCastleProvider())
            : KeyPairGenerator.getInstance("EC");

    generator.initialize(new ECGenParameterSpec(curveName), secureRandom);
    return generator.generateKeyPair();
  }

  private static KeyPair generateEdKeyPair(
      String algorithm, NamedParameterSpec parameters, SecureRandom secureRandom)
      throws GeneralSecurityException {

    KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm);
    generator.initialize(parameters, secureRandom);
    return generator.generateKeyPair();
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
