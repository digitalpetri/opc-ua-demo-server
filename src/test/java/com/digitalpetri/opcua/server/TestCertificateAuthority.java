package com.digitalpetri.opcua.server;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.pkcs.Attribute;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;
import org.jspecify.annotations.Nullable;

/**
 * A minimal certificate authority for tests that play the GDS role: it issues application instance
 * certificates from the signing requests a server produces with CreateSigningRequest.
 */
public final class TestCertificateAuthority {

  private final KeyPair keyPair;
  private final X509Certificate certificate;
  private final X500Name subject;

  private TestCertificateAuthority(KeyPair keyPair, X509Certificate certificate, X500Name subject) {
    this.keyPair = keyPair;
    this.certificate = certificate;
    this.subject = subject;
  }

  /**
   * Create an authority with a fresh RSA key and a self-signed CA certificate.
   *
   * @param commonName the CA's common name.
   * @return the authority.
   * @throws Exception if key or certificate generation fails.
   */
  public static TestCertificateAuthority create(String commonName) throws Exception {
    KeyPair keyPair = SelfSignedCertificateGenerator.generateRsaKeyPair(2048);
    var subject = new X500Name("CN=" + commonName + ",O=digitalpetri,OU=test");

    var extensionUtils = new JcaX509ExtensionUtils();

    JcaX509v3CertificateBuilder builder =
        new JcaX509v3CertificateBuilder(
            subject,
            randomSerial(),
            Date.from(Instant.now().minus(1, ChronoUnit.HOURS)),
            Date.from(Instant.now().plus(365, ChronoUnit.DAYS)),
            subject,
            keyPair.getPublic());

    builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
    builder.addExtension(
        Extension.keyUsage,
        true,
        new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign | KeyUsage.digitalSignature));
    builder.addExtension(
        Extension.subjectKeyIdentifier,
        false,
        extensionUtils.createSubjectKeyIdentifier(keyPair.getPublic()));

    X509Certificate certificate = sign(builder, keyPair);

    return new TestCertificateAuthority(keyPair, certificate, subject);
  }

  /**
   * @return the CA certificate, to be supplied as an issuer certificate.
   */
  public X509Certificate getCertificate() {
    return certificate;
  }

  /**
   * Issue an application instance certificate for a PKCS#10 signing request.
   *
   * <p>The subject and the SubjectAlternativeName extension requested by the CSR are copied into
   * the certificate. KeyUsage follows the key family so Milo's end-entity checks accept the result
   * for both RSA and ECC security policies.
   *
   * @param signingRequest the DER-encoded PKCS#10 request.
   * @return the issued certificate.
   * @throws Exception if the request is malformed or its signature is invalid.
   */
  public X509Certificate issue(byte[] signingRequest) throws Exception {
    var csr = new PKCS10CertificationRequest(signingRequest);
    PublicKey subjectPublicKey = new JcaPKCS10CertificationRequest(csr).getPublicKey();

    if (!csr.isSignatureValid(new JcaContentVerifierProviderBuilder().build(subjectPublicKey))) {
      throw new IllegalArgumentException("signing request signature is invalid");
    }

    var extensionUtils = new JcaX509ExtensionUtils();

    JcaX509v3CertificateBuilder builder =
        new JcaX509v3CertificateBuilder(
            subject,
            randomSerial(),
            Date.from(Instant.now().minus(1, ChronoUnit.HOURS)),
            Date.from(Instant.now().plus(365, ChronoUnit.DAYS)),
            csr.getSubject(),
            subjectPublicKey);

    builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
    builder.addExtension(Extension.keyUsage, true, keyUsageFor(subjectPublicKey));
    builder.addExtension(
        Extension.extendedKeyUsage,
        false,
        new ExtendedKeyUsage(
            new KeyPurposeId[] {KeyPurposeId.id_kp_serverAuth, KeyPurposeId.id_kp_clientAuth}));
    builder.addExtension(
        Extension.subjectKeyIdentifier,
        false,
        extensionUtils.createSubjectKeyIdentifier(subjectPublicKey));
    builder.addExtension(
        Extension.authorityKeyIdentifier,
        false,
        extensionUtils.createAuthorityKeyIdentifier(certificate));

    GeneralNames requestedNames = requestedSubjectAlternativeNames(csr);
    if (requestedNames != null) {
      builder.addExtension(Extension.subjectAlternativeName, false, requestedNames);
    }

    return sign(builder, keyPair);
  }

  private static KeyUsage keyUsageFor(PublicKey publicKey) {
    if ("RSA".equals(publicKey.getAlgorithm())) {
      return new KeyUsage(
          KeyUsage.digitalSignature
              | KeyUsage.nonRepudiation
              | KeyUsage.keyEncipherment
              | KeyUsage.dataEncipherment);
    } else {
      return new KeyUsage(KeyUsage.digitalSignature);
    }
  }

  private static @Nullable GeneralNames requestedSubjectAlternativeNames(
      PKCS10CertificationRequest csr) {
    for (Attribute attribute :
        csr.getAttributes(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest)) {
      for (ASN1Encodable value : attribute.getAttrValues()) {
        Extensions extensions = Extensions.getInstance(value);
        GeneralNames names =
            GeneralNames.fromExtensions(extensions, Extension.subjectAlternativeName);
        if (names != null) {
          return names;
        }
      }
    }
    return null;
  }

  private static X509Certificate sign(JcaX509v3CertificateBuilder builder, KeyPair signingKey)
      throws Exception {

    ContentSigner signer =
        new JcaContentSignerBuilder("SHA256withRSA").build(signingKey.getPrivate());

    X509CertificateHolder holder = builder.build(signer);

    return new JcaX509CertificateConverter().getCertificate(holder);
  }

  private static BigInteger randomSerial() {
    return new BigInteger(64, new SecureRandom()).abs().add(BigInteger.ONE);
  }
}
