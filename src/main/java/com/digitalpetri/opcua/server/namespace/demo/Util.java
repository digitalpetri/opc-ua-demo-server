package com.digitalpetri.opcua.server.namespace.demo;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ulong;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;

import java.lang.reflect.Array;
import java.util.UUID;
import org.eclipse.milo.opcua.stack.core.OpcUaDataType;
import org.eclipse.milo.opcua.stack.core.encoding.DefaultEncodingContext;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.DiagnosticInfo;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.Matrix;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.XmlElement;
import org.eclipse.milo.opcua.stack.core.types.structured.XVType;

public class Util {

  private Util() {}

  /**
   * Derives a child NodeId from a parent NodeId and a name.
   *
   * <p>The derived NodeId will have the same namespace index as the parent NodeId, and its
   * identifier will be a concatenation of the parent's identifier and the provided name, separated
   * by a dot.
   *
   * @param parentNodeId the parent NodeId.
   * @param name the name to derive the child NodeId from.
   * @return the derived child NodeId.
   */
  public static NodeId deriveChildNodeId(NodeId parentNodeId, String name) {
    return new NodeId(
        parentNodeId.getNamespaceIndex(), "%s.%s".formatted(parentNodeId.getIdentifier(), name));
  }

  static Object getDefaultScalarValue(OpcUaDataType dataType) {
    return switch (dataType) {
      case Boolean -> Boolean.FALSE;
      case SByte -> (byte) 0;
      case Int16 -> (short) 0;
      case Int32 -> 0;
      case Int64 -> 0L;
      case Byte -> ubyte(0);
      case UInt16 -> ushort(0);
      case UInt32 -> uint(0);
      case UInt64 -> ulong(0);
      case Float -> 0f;
      case Double -> 0.0;
      case String -> "hello";
      case DateTime -> DateTime.now();
      case Guid -> UUID.randomUUID();
      case ByteString -> ByteString.of(new byte[] {1, 2, 3, 4});
      case XmlElement -> new XmlElement("<xml></xml>");
      case NodeId -> new NodeId(1, "DoesNotExist");
      case ExpandedNodeId -> ExpandedNodeId.of(DemoNamespace.NAMESPACE_URI, "DoesNotExist");
      case StatusCode -> StatusCode.GOOD;
      case QualifiedName -> QualifiedName.parse("1:QualifiedName");
      case LocalizedText -> LocalizedText.english("hello");
      case ExtensionObject ->
          ExtensionObject.encode(DefaultEncodingContext.INSTANCE, new XVType(1.0, 2.0f));
      case DataValue -> new DataValue(Variant.ofInt32(42));
      case Variant -> Variant.ofInt32(42);
      case DiagnosticInfo -> DiagnosticInfo.NULL_VALUE;
    };
  }

  static Object getDefaultArrayValue(OpcUaDataType dataType) {
    Object value = getDefaultScalarValue(dataType);
    Object array = Array.newInstance(value.getClass(), 5);
    for (int i = 0; i < 5; i++) {
      Array.set(array, i, value);
    }
    return array;
  }

  static Matrix getDefaultMatrixValue(OpcUaDataType dataType) {
    Object value = getDefaultScalarValue(dataType);
    Object array = Array.newInstance(value.getClass(), 5, 5);
    for (int i = 0; i < 5; i++) {
      Object innerArray = Array.newInstance(value.getClass(), 5);
      for (int j = 0; j < 5; j++) {
        Array.set(innerArray, j, value);
      }
      Array.set(array, i, innerArray);
    }
    return new Matrix(array);
  }
}
