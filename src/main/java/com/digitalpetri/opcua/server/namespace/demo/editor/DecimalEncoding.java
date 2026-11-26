package com.digitalpetri.opcua.server.namespace.demo.editor;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;

/**
 * Binary encoder, decoder, and validator for the OPC UA built-in {@code Decimal} ({@code i=50})
 * DataType per OPC UA Part 6 §5.1.10.
 *
 * <p>Inside a {@code Variant}, a {@code Decimal} is carried as a binary {@link ExtensionObject}
 * whose {@code TypeId} is {@link NodeIds#Decimal} ({@code i=50}) and whose body contains a
 * little-endian {@code Int16} scale followed by an unprefixed two's-complement little-endian
 * integer. Bodies of length {@code <= 2} are invalid.
 */
public final class DecimalEncoding {

  private DecimalEncoding() {}

  /**
   * Encodes a {@link BigDecimal} as an OPC UA {@code Decimal} binary {@link ExtensionObject}.
   *
   * @param value the decimal value to encode; its scale must fit in a signed 16-bit integer.
   * @return a binary {@link ExtensionObject} with TypeId {@code i=50}.
   */
  public static ExtensionObject encode(BigDecimal value) {
    int scale = value.scale();
    if (scale < Short.MIN_VALUE || scale > Short.MAX_VALUE) {
      throw new IllegalArgumentException("Decimal scale out of Int16 range: " + scale);
    }
    return encode((short) scale, value.unscaledValue());
  }

  /**
   * Encodes an explicit {@code scale} and {@code unscaledValue} as an OPC UA {@code Decimal} binary
   * {@link ExtensionObject}.
   *
   * @param scale the Int16 power-of-ten scale ({@code unscaledValue * 10^(-scale)}).
   * @param unscaledValue the signed unscaled integer value.
   * @return a binary {@link ExtensionObject} with TypeId {@code i=50}.
   */
  public static ExtensionObject encode(short scale, BigInteger unscaledValue) {
    byte[] bigEndian = unscaledValue.toByteArray();
    byte[] body = new byte[2 + bigEndian.length];

    body[0] = (byte) (scale & 0xFF);
    body[1] = (byte) ((scale >>> 8) & 0xFF);

    for (int i = 0; i < bigEndian.length; i++) {
      body[2 + i] = bigEndian[bigEndian.length - 1 - i];
    }

    return ExtensionObject.of(ByteString.of(body), NodeIds.Decimal);
  }

  /**
   * Validates and decodes an OPC UA {@code Decimal} binary {@link ExtensionObject}.
   *
   * @param extensionObject the incoming {@link ExtensionObject} to validate and decode.
   * @return the decoded {@link BigDecimal}.
   * @throws UaException with {@code Bad_TypeMismatch} if the encoding is not binary or TypeId is
   *     not {@code i=50}, or {@code Bad_OutOfRange} if the body length is {@code <= 2}.
   */
  public static BigDecimal decode(ExtensionObject extensionObject) throws UaException {
    if (!(extensionObject instanceof ExtensionObject.Binary binary)) {
      throw new UaException(
          StatusCodes.Bad_TypeMismatch,
          "Decimal must be encoded as a binary ExtensionObject (Part 6 §5.1.10)");
    }

    if (!NodeIds.Decimal.equals(binary.getEncodingOrTypeId())) {
      throw new UaException(
          StatusCodes.Bad_TypeMismatch,
          "Decimal ExtensionObject TypeId must be i=50, got " + binary.getEncodingOrTypeId());
    }

    ByteString body = binary.getBody();
    if (body.isNull()) {
      throw new UaException(StatusCodes.Bad_TypeMismatch, "Decimal body must not be null");
    }

    byte[] bytes = body.bytesOrEmpty();
    if (bytes.length <= 2) {
      throw new UaException(
          StatusCodes.Bad_OutOfRange,
          "Decimal body length must be > 2 bytes (Part 6 §5.1.10), got " + bytes.length);
    }

    short scale = (short) ((bytes[0] & 0xFF) | ((bytes[1] & 0xFF) << 8));
    int valueLength = bytes.length - 2;
    byte[] bigEndian = new byte[valueLength];
    for (int i = 0; i < valueLength; i++) {
      bigEndian[i] = bytes[bytes.length - 1 - i];
    }

    return new BigDecimal(new BigInteger(bigEndian), scale);
  }
}
