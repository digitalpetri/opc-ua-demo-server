package com.digitalpetri.opcua.server.namespace.demo.types;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import com.digitalpetri.opcua.server.namespace.demo.DemoNamespace;
import java.util.Arrays;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.encoding.EncodingContext;
import org.eclipse.milo.opcua.stack.core.encoding.GenericDataTypeCodec;
import org.eclipse.milo.opcua.stack.core.encoding.UaDecoder;
import org.eclipse.milo.opcua.stack.core.encoding.UaEncoder;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.structured.EnumDefinition;
import org.eclipse.milo.opcua.stack.core.types.structured.EnumField;
import org.eclipse.milo.opcua.stack.core.types.structured.OptionSet;

/**
 * A concrete two-byte subtype of OPC UA {@link OptionSet} ({@code i=12755}) with defined bits 0–3
 * and 8–9 and undefined gap bits 4–7 and 10–15.
 */
public final class DemoOptionSet extends OptionSet {

  public static final String TYPE_IDENTIFIER = "Demo.Types.DemoOptionSet";
  public static final String BINARY_ENCODING_IDENTIFIER = TYPE_IDENTIFIER + ".DefaultBinary";

  public static final ExpandedNodeId TYPE_ID =
      ExpandedNodeId.of(DemoNamespace.NAMESPACE_URI, TYPE_IDENTIFIER);
  public static final ExpandedNodeId BINARY_ENCODING_ID =
      ExpandedNodeId.of(DemoNamespace.NAMESPACE_URI, BINARY_ENCODING_IDENTIFIER);

  public static final int BYTE_LENGTH = 2;
  public static final UInteger OPTION_SET_LENGTH = uint(BYTE_LENGTH);

  /** Bit mask of all defined bits: bits 0–3 in byte 0 ({@code 0x0F}) and bits 8–9 in byte 1. */
  public static final byte[] DEFINED_VALID_BITS = new byte[] {0x0F, 0x03};

  public DemoOptionSet(ByteString value, ByteString validBits) {
    super(value, validBits);
  }

  /**
   * Creates a {@link DemoOptionSet} with all defined bits marked valid.
   *
   * @param byte0 the low byte (bits 0–7).
   * @param byte1 the high byte (bits 8–15).
   * @return a new {@link DemoOptionSet} instance.
   */
  public static DemoOptionSet of(int byte0, int byte1) {
    byte[] value =
        new byte[] {(byte) (byte0 & DEFINED_VALID_BITS[0]), (byte) (byte1 & DEFINED_VALID_BITS[1])};
    return new DemoOptionSet(
        ByteString.of(value), ByteString.of(Arrays.copyOf(DEFINED_VALID_BITS, BYTE_LENGTH)));
  }

  /**
   * Validates and merges a client-supplied {@link DemoOptionSet} against {@code current} per OPC UA
   * Part 3 §8.40.
   *
   * @param current the currently stored option set value.
   * @param written the incoming client-supplied option set value.
   * @return the merged option set value with all defined bits marked valid.
   * @throws UaException with {@code Bad_OutOfRange} if lengths mismatch or invalid bits are set.
   */
  public static DemoOptionSet mergeWrite(DemoOptionSet current, DemoOptionSet written)
      throws UaException {

    byte[] valueBytes = written.getValue() != null ? written.getValue().bytes() : null;
    byte[] validBitBytes = written.getValidBits() != null ? written.getValidBits().bytes() : null;

    if (valueBytes == null || validBitBytes == null) {
      throw new UaException(
          StatusCodes.Bad_OutOfRange, "OptionSet Value and ValidBits must not be null");
    }
    if (valueBytes.length != validBitBytes.length) {
      throw new UaException(
          StatusCodes.Bad_OutOfRange,
          "OptionSet Value length (%d) must equal ValidBits length (%d)"
              .formatted(valueBytes.length, validBitBytes.length));
    }
    if (valueBytes.length != BYTE_LENGTH) {
      throw new UaException(
          StatusCodes.Bad_OutOfRange,
          "OptionSet length (%d) must equal declared OptionSetLength (%d)"
              .formatted(valueBytes.length, BYTE_LENGTH));
    }

    for (int i = 0; i < BYTE_LENGTH; i++) {
      if ((validBitBytes[i] & ~DEFINED_VALID_BITS[i]) != 0) {
        throw new UaException(
            StatusCodes.Bad_OutOfRange,
            "OptionSet ValidBits byte[%d] (0x%02X) sets an undefined bit"
                .formatted(i, validBitBytes[i] & 0xFF));
      }
    }

    byte[] currentBytes =
        current.getValue() != null && current.getValue().length() == BYTE_LENGTH
            ? current.getValue().bytesOrEmpty()
            : new byte[BYTE_LENGTH];

    byte[] merged = new byte[BYTE_LENGTH];
    for (int i = 0; i < BYTE_LENGTH; i++) {
      merged[i] =
          (byte) ((valueBytes[i] & validBitBytes[i]) | (currentBytes[i] & ~validBitBytes[i]));
    }

    return new DemoOptionSet(
        ByteString.of(merged), ByteString.of(Arrays.copyOf(DEFINED_VALID_BITS, BYTE_LENGTH)));
  }

  public static LocalizedText[] optionSetValues() {
    return new LocalizedText[] {
      LocalizedText.english("Read"),
      LocalizedText.english("Write"),
      LocalizedText.english("Execute"),
      LocalizedText.english("Archive"),
      LocalizedText.NULL_VALUE,
      LocalizedText.NULL_VALUE,
      LocalizedText.NULL_VALUE,
      LocalizedText.NULL_VALUE,
      LocalizedText.english("Audit"),
      LocalizedText.english("Diagnostics")
    };
  }

  public static EnumDefinition definition() {
    return new EnumDefinition(
        new EnumField[] {
          new EnumField(
              0L, LocalizedText.english("Read"), LocalizedText.english("Allow reading"), "Read"),
          new EnumField(
              1L, LocalizedText.english("Write"), LocalizedText.english("Allow writing"), "Write"),
          new EnumField(
              2L,
              LocalizedText.english("Execute"),
              LocalizedText.english("Allow executing"),
              "Execute"),
          new EnumField(
              3L,
              LocalizedText.english("Archive"),
              LocalizedText.english("Allow archiving"),
              "Archive"),
          new EnumField(
              8L,
              LocalizedText.english("Audit"),
              LocalizedText.english("Enable audit trail"),
              "Audit"),
          new EnumField(
              9L,
              LocalizedText.english("Diagnostics"),
              LocalizedText.english("Enable diagnostics"),
              "Diagnostics")
        });
  }

  @Override
  public ExpandedNodeId getTypeId() {
    return TYPE_ID;
  }

  @Override
  public ExpandedNodeId getBinaryEncodingId() {
    return BINARY_ENCODING_ID;
  }

  @Override
  public ExpandedNodeId getXmlEncodingId() {
    return ExpandedNodeId.NULL_VALUE;
  }

  @Override
  public ExpandedNodeId getJsonEncodingId() {
    return ExpandedNodeId.NULL_VALUE;
  }

  public static final class Codec extends GenericDataTypeCodec<DemoOptionSet> {

    @Override
    public Class<DemoOptionSet> getType() {
      return DemoOptionSet.class;
    }

    @Override
    public DemoOptionSet decodeType(EncodingContext context, UaDecoder decoder) {
      ByteString value = decoder.decodeByteString("Value");
      ByteString validBits = decoder.decodeByteString("ValidBits");
      return new DemoOptionSet(value, validBits);
    }

    @Override
    public void encodeType(EncodingContext context, UaEncoder encoder, DemoOptionSet value) {
      encoder.encodeByteString("Value", value.getValue());
      encoder.encodeByteString("ValidBits", value.getValidBits());
    }
  }
}
