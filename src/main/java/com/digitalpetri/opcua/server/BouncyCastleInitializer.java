package com.digitalpetri.opcua.server;

import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/** This class is used to register BouncyCastle as a security provider with GraalVM builder. */
public class BouncyCastleInitializer {

  static {
    // Force provider registration during the native image build
    Security.addProvider(new BouncyCastleProvider());
  }
}
