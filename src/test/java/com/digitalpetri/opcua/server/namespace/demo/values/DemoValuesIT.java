package com.digitalpetri.opcua.server.namespace.demo.values;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.opcua.server.OpcUaDemoServer;
import com.digitalpetri.opcua.server.OpcUaTestClient;
import com.digitalpetri.opcua.server.OpcUaTestServerBuilder;
import com.digitalpetri.opcua.server.namespace.demo.DemoNamespace;
import com.digitalpetri.opcua.server.namespace.demo.DemoValueFilters;
import com.digitalpetri.opcua.server.namespace.demo.types.DecimalEncoding;
import com.digitalpetri.opcua.server.namespace.demo.types.DemoOptionSet;
import com.digitalpetri.opcua.server.namespace.demo.types.DemoRecord;
import com.digitalpetri.opcua.server.namespace.demo.types.DemoTypesFragment;
import com.digitalpetri.opcua.server.namespace.demo.types.StructWithOptionSet;
import com.digitalpetri.opcua.test.types.ConcreteTestType;
import com.digitalpetri.opcua.test.types.ConcreteTestTypeEx;
import com.digitalpetri.opcua.test.types.UnionOfScalar;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.NamespaceTable;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.OpcUaDataType;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.encoding.EncodingContext;
import org.eclipse.milo.opcua.stack.core.types.UaStructuredType;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.Matrix;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseResultMask;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.AxisInformation;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.EnumDefinition;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteResponse;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;
import org.eclipse.milo.opcua.stack.core.types.structured.XVType;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end integration tests for {@link DemoValuesFragment} with only {@code
 * address-space.values.enabled = true}, verifying type × arity coverage, Decimal encoding,
 * OptionSet Part 3 §8.40 merging, EnumValues enforcement, dynamic client decoding, and DataAccess
 * VariableTypes.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DemoValuesIT {

  private OpcUaDemoServer server;
  private OpcUaClient client;
  private UShort demoNsIndex;
  private EncodingContext encodingContext;

  @BeforeAll
  void setUp(@TempDir Path tempDir) throws Exception {
    Config customConfig = ConfigFactory.parseMap(Map.of("address-space.values.enabled", true));

    server = OpcUaTestServerBuilder.builder().withDataDir(tempDir).withConfig(customConfig).build();
    server.startup();

    client = OpcUaTestClient.create(server.getServer());
    client.connect();

    NamespaceTable nsTable = server.getServer().getNamespaceTable();
    demoNsIndex = nsTable.getIndex(DemoNamespace.NAMESPACE_URI);
    assertNotNull(demoNsIndex);
    encodingContext = server.getServer().getStaticEncodingContext();
  }

  @AfterAll
  void tearDown() throws Exception {
    Throwable disconnectFailure = null;
    try {
      if (client != null) {
        client.disconnect();
      }
    } catch (Throwable t) {
      disconnectFailure = t;
    } finally {
      if (server != null) {
        try {
          server.shutdown();
        } catch (Throwable t) {
          if (disconnectFailure != null) {
            disconnectFailure.addSuppressed(t);
          } else {
            throw t;
          }
        }
      }
    }

    if (disconnectFailure instanceof Exception e) {
      throw e;
    } else if (disconnectFailure instanceof Error e) {
      throw e;
    } else if (disconnectFailure != null) {
      throw new RuntimeException(disconnectFailure);
    }
  }

  @Nested
  class BuiltinAndVariant {

    @Test
    void allBuiltinsAndVariantsReadGoodAcrossScalarArrayAndMatrix() throws Exception {
      for (OpcUaDataType dataType : OpcUaDataType.values()) {
        if (dataType == OpcUaDataType.DiagnosticInfo) {
          continue;
        }

        for (String arity : List.of("Scalar", "Array", "Matrix")) {
          NodeId builtinId = demoNode("Demo.Values.Builtin." + arity + "." + dataType.name());
          DataValue dv = client.readValue(0.0, TimestampsToReturn.Neither, builtinId);
          assertTrue(dv.statusCode().isGood(), () -> builtinId + " failed: " + dv.statusCode());
          assertNotNull(dv.value().value(), () -> builtinId + " value should not be null");
        }

        if (dataType != OpcUaDataType.Variant) {
          for (String arity : List.of("Scalar", "Array", "Matrix")) {
            NodeId variantId = demoNode("Demo.Values.Variant." + arity + "." + dataType.name());
            DataValue dv = client.readValue(0.0, TimestampsToReturn.Neither, variantId);
            assertTrue(dv.statusCode().isGood(), () -> variantId + " failed: " + dv.statusCode());
            assertNotNull(dv.value().value(), () -> variantId + " value should not be null");
          }
        }
      }

      DataValue mixedArray =
          client.readValue(
              0.0, TimestampsToReturn.Neither, demoNode("Demo.Values.Variant.Array.Mixed"));
      assertTrue(mixedArray.statusCode().isGood());
      assertEquals(23, assertInstanceOf(Variant[].class, mixedArray.value().value()).length);

      DataValue mixedMatrix =
          client.readValue(
              0.0, TimestampsToReturn.Neither, demoNode("Demo.Values.Variant.Matrix.Mixed"));
      assertTrue(mixedMatrix.statusCode().isGood());
      assertArrayEquals(
          new int[] {23, 3},
          assertInstanceOf(Matrix.class, mixedMatrix.value().value()).getDimensions());
    }

    // Part 6 §5.1.10: Decimal values use TypeId i=50 binary ExtensionObjects across all arities.
    @Test
    void decimalScalarArrayAndMatrixRoundTripAndRejectInvalidPayloads() throws Exception {
      NodeId scalarId = demoNode("Demo.Values.Builtin.Scalar.Decimal");
      BigDecimal next = new BigDecimal("-4200.750");
      assertTrue(
          writeValue(scalarId, Variant.ofExtensionObject(DecimalEncoding.encode(next))).isGood());
      assertEquals(
          next,
          DecimalEncoding.decode(
              (ExtensionObject)
                  client.readValue(0.0, TimestampsToReturn.Neither, scalarId).value().value()));

      // Body length <= 2 fails with Bad_OutOfRange; wrong Java type fails with Bad_TypeMismatch.
      ExtensionObject tooShort =
          ExtensionObject.of(ByteString.of(new byte[] {0x02, 0x00}), NodeIds.Decimal);
      assertEquals(
          StatusCodes.Bad_OutOfRange,
          writeValue(scalarId, Variant.ofExtensionObject(tooShort)).getValue());
      assertEquals(
          StatusCodes.Bad_TypeMismatch, writeValue(scalarId, Variant.ofDouble(1.5)).getValue());

      // 1-D array write and validation
      NodeId arrayId = demoNode("Demo.Values.Builtin.Array.Decimal");
      ExtensionObject[] nextArray = {
        DecimalEncoding.encode(new BigDecimal("1.25")),
        DecimalEncoding.encode(new BigDecimal("-99.99"))
      };
      assertTrue(writeValue(arrayId, Variant.ofExtensionObjectArray(nextArray)).isGood());
      ExtensionObject[] readArray =
          (ExtensionObject[])
              client.readValue(0.0, TimestampsToReturn.Neither, arrayId).value().value();
      assertEquals(new BigDecimal("-99.99"), DecimalEncoding.decode(readArray[1]));
      assertEquals(
          StatusCodes.Bad_OutOfRange,
          writeValue(arrayId, Variant.ofExtensionObjectArray(new ExtensionObject[] {tooShort}))
              .getValue());
      assertEquals(
          StatusCodes.Bad_WriteNotSupported,
          writeValueWithIndexRange(
                  arrayId,
                  "0",
                  Variant.ofExtensionObjectArray(
                      new ExtensionObject[] {DecimalEncoding.encode(BigDecimal.ONE)}))
              .getValue());

      // 2-D matrix write, empty wire representation, and validation
      NodeId matrixId = demoNode("Demo.Values.Builtin.Matrix.Decimal");
      Matrix nextMatrix =
          Matrix.ofExtensionObject(
              new ExtensionObject[][] {
                {
                  DecimalEncoding.encode(new BigDecimal("10.5")),
                  DecimalEncoding.encode(new BigDecimal("20.5"))
                },
                {
                  DecimalEncoding.encode(new BigDecimal("30.5")),
                  DecimalEncoding.encode(new BigDecimal("40.5"))
                }
              });
      assertTrue(writeValue(matrixId, Variant.ofMatrix(nextMatrix)).isGood());
      Matrix readMatrix =
          assertInstanceOf(
              Matrix.class,
              client.readValue(0.0, TimestampsToReturn.Neither, matrixId).value().value());
      ExtensionObject[] flat = (ExtensionObject[]) readMatrix.getElements();
      assertEquals(new BigDecimal("40.5"), DecimalEncoding.decode(flat[3]));

      // Empty 2-D arrays arrive as ExtensionObject[0] on the wire; nonempty 1-D arrays and null
      // remain rejected.
      assertTrue(
          writeValue(matrixId, Variant.ofExtensionObjectArray(new ExtensionObject[0])).isGood());
      assertEquals(
          StatusCodes.Bad_TypeMismatch,
          writeValue(
                  matrixId,
                  Variant.ofExtensionObjectArray(
                      new ExtensionObject[] {DecimalEncoding.encode(BigDecimal.ONE)}))
              .getValue());
      assertEquals(
          StatusCodes.Bad_TypeMismatch, writeValue(matrixId, Variant.NULL_VALUE).getValue());
      assertTrue(writeValue(matrixId, Variant.ofMatrix(nextMatrix)).isGood());
    }
  }

  @Nested
  class Enumerations {

    @Test
    void sparseAndRestrictedEnumsEnforceMembershipAcrossScalarArrayAndMatrix() throws Exception {
      NodeId typeId = demoNode(DemoTypesFragment.SPARSE_STATUS_ENUM_IDENTIFIER);
      DataValue defVal = readAttribute(typeId, AttributeId.DataTypeDefinition);
      assertTrue(defVal.statusCode().isGood());
      EnumDefinition def =
          (EnumDefinition) ((ExtensionObject) defVal.value().value()).decode(encodingContext);
      assertEquals(5, def.getFields().length);

      // TestEnumType enforces membership (0, 1, 2) across scalar, array, and matrix fixtures.
      NodeId testEnumScalar = demoNode("Demo.Values.Enumeration.Scalar.TestEnumType");
      assertTrue(writeValue(testEnumScalar, Variant.ofInt32(2)).isGood());
      assertEquals(
          StatusCodes.Bad_OutOfRange, writeValue(testEnumScalar, Variant.ofInt32(3)).getValue());

      NodeId testEnumArray = demoNode("Demo.Values.Enumeration.Array.TestEnumType");
      assertTrue(writeValue(testEnumArray, Variant.ofInt32Array(new Integer[] {0, 1, 2})).isGood());
      assertEquals(
          StatusCodes.Bad_OutOfRange,
          writeValue(testEnumArray, Variant.ofInt32Array(new Integer[] {0, 3})).getValue());

      NodeId testEnumMatrix = demoNode("Demo.Values.Enumeration.Matrix.TestEnumType");
      assertEquals(
          StatusCodes.Bad_OutOfRange,
          writeValue(
                  testEnumMatrix,
                  Variant.ofMatrix(Matrix.ofInt32(new Integer[][] {{0, 1}, {2, 3}})))
              .getValue());

      // SparseStatusEnum accepts defined values (1,2,4,8,16) and rejects gap value 3 across
      // arities.
      NodeId sparseScalar = demoNode("Demo.Values.Enumeration.Scalar.SparseStatusEnum");
      assertTrue(writeValue(sparseScalar, Variant.ofInt32(16)).isGood());
      assertEquals(
          StatusCodes.Bad_OutOfRange, writeValue(sparseScalar, Variant.ofInt32(3)).getValue());

      NodeId sparseMatrix = demoNode("Demo.Values.Enumeration.Matrix.SparseStatusEnum");
      assertTrue(
          writeValue(
                  sparseMatrix, Variant.ofMatrix(Matrix.ofInt32(new Integer[][] {{1, 8}, {4, 16}})))
              .isGood());
      assertEquals(
          StatusCodes.Bad_OutOfRange,
          writeValue(
                  sparseMatrix, Variant.ofMatrix(Matrix.ofInt32(new Integer[][] {{1, 3}, {4, 16}})))
              .getValue());

      // OperationalStatusEnum subtype rejects values (8, 16) removed from supertype.
      NodeId opScalar = demoNode("Demo.Values.Enumeration.Scalar.OperationalStatusEnum");
      assertTrue(writeValue(opScalar, Variant.ofInt32(4)).isGood());
      assertEquals(StatusCodes.Bad_OutOfRange, writeValue(opScalar, Variant.ofInt32(8)).getValue());

      NodeId opArray = demoNode("Demo.Values.Enumeration.Array.OperationalStatusEnum");
      assertEquals(
          StatusCodes.Bad_OutOfRange,
          writeValue(opArray, Variant.ofInt32Array(new Integer[] {1, 16})).getValue());
    }
  }

  @Nested
  class OptionSets {

    // Part 3 §8.40: partial ValidBits masks must merge into stored values and reject undefined bits
    // across Scalar, Array, and Matrix variables.
    @Test
    void demoOptionSetAppliesValidBitsMergeAcrossScalarArrayAndMatrix() throws Exception {
      // Scalar merge: establish a clean full-mask baseline so this test does not depend on
      // execution order relative to the concurrent OptionSet test.
      NodeId scalarId = demoNode("Demo.Values.OptionSet.Scalar.DemoOptionSet");
      assertTrue(
          writeValue(scalarId, Variant.ofExtensionObject(encode(DemoOptionSet.of(0x03, 0x01))))
              .isGood());
      DemoOptionSet partial =
          new DemoOptionSet(
              ByteString.of(new byte[] {0x04, 0x02}), ByteString.of(new byte[] {0x05, 0x02}));
      assertTrue(writeValue(scalarId, Variant.ofExtensionObject(encode(partial))).isGood());

      DemoOptionSet after =
          (DemoOptionSet)
              ((ExtensionObject)
                      client.readValue(0.0, TimestampsToReturn.Neither, scalarId).value().value())
                  .decode(encodingContext);
      assertArrayEquals(new byte[] {0x06, 0x03}, after.getValue().bytesOrEmpty());
      assertArrayEquals(DemoOptionSet.DEFINED_VALID_BITS, after.getValidBits().bytesOrEmpty());

      DemoOptionSet invalidBit =
          new DemoOptionSet(
              ByteString.of(new byte[] {0x10, 0x00}), ByteString.of(new byte[] {0x10, 0x00}));
      assertEquals(
          StatusCodes.Bad_OutOfRange,
          writeValue(scalarId, Variant.ofExtensionObject(encode(invalidBit))).getValue());

      // Matrix merge: establish 2x2 baseline, verify equal-width merge, then verify width change
      // (2x2 -> 2x3) preserves unselected bits by coordinate ([1,0] keeps old [1,0], not [1,1]).
      NodeId matrixId = demoNode("Demo.Values.OptionSet.Matrix.DemoOptionSet");
      Matrix baseline2x2 =
          Matrix.ofExtensionObject(
              new ExtensionObject[][] {
                {encode(DemoOptionSet.of(0x01, 0x00)), encode(DemoOptionSet.of(0x02, 0x01))},
                {encode(DemoOptionSet.of(0x04, 0x02)), encode(DemoOptionSet.of(0x0F, 0x03))}
              });
      assertTrue(writeValue(matrixId, Variant.ofMatrix(baseline2x2)).isGood());

      DemoOptionSet m00 =
          new DemoOptionSet(
              ByteString.of(new byte[] {0x02, 0x00}), ByteString.of(new byte[] {0x02, 0x00}));
      Matrix update =
          Matrix.ofExtensionObject(
              new ExtensionObject[][] {
                {encode(m00), encode(DemoOptionSet.of(0x04, 0x01))},
                {encode(DemoOptionSet.of(0x08, 0x02)), encode(DemoOptionSet.of(0x01, 0x03))}
              });
      assertTrue(writeValue(matrixId, Variant.ofMatrix(update)).isGood());

      Matrix readBack =
          assertInstanceOf(
              Matrix.class,
              client.readValue(0.0, TimestampsToReturn.Neither, matrixId).value().value());
      ExtensionObject[] flat = (ExtensionObject[]) readBack.getElements();
      DemoOptionSet merged00 = (DemoOptionSet) flat[0].decode(encodingContext);
      assertArrayEquals(new byte[] {0x03, 0x00}, merged00.getValue().bytesOrEmpty());

      // Resize 2x2 -> 2x3 with ValidBits=0 at [1,0] and [0,2]: [1,0] (flat 3) must keep old [1,0]
      // (0x08, 0x02) rather than old [1,1] (0x01, 0x03), and new column [0,2] starts at 0x00,0x00.
      DemoOptionSet keepAll =
          new DemoOptionSet(
              ByteString.of(new byte[] {0x00, 0x00}), ByteString.of(new byte[] {0x00, 0x00}));
      Matrix wider =
          Matrix.ofExtensionObject(
              new ExtensionObject[][] {
                {encode(keepAll), encode(keepAll), encode(keepAll)},
                {encode(keepAll), encode(keepAll), encode(DemoOptionSet.of(0x05, 0x01))}
              });
      assertTrue(writeValue(matrixId, Variant.ofMatrix(wider)).isGood());

      Matrix widerRead =
          assertInstanceOf(
              Matrix.class,
              client.readValue(0.0, TimestampsToReturn.Neither, matrixId).value().value());
      assertArrayEquals(new int[] {2, 3}, widerRead.getDimensions());
      ExtensionObject[] widerFlat = (ExtensionObject[]) widerRead.getElements();
      assertArrayEquals(
          new byte[] {0x00, 0x00},
          ((DemoOptionSet) widerFlat[2].decode(encodingContext)).getValue().bytesOrEmpty());
      assertArrayEquals(
          new byte[] {0x08, 0x02},
          ((DemoOptionSet) widerFlat[3].decode(encodingContext)).getValue().bytesOrEmpty());
      assertArrayEquals(
          new byte[] {0x01, 0x03},
          ((DemoOptionSet) widerFlat[4].decode(encodingContext)).getValue().bytesOrEmpty());
    }

    @Test
    void concurrentPartialOptionSetWritesDoNotLoseUpdates() throws Exception {
      NodeId scalarId = demoNode("Demo.Values.OptionSet.Scalar.DemoOptionSet");
      DemoOptionSet clearAll = DemoOptionSet.of(0x00, 0x00);
      DemoOptionSet setBit0 =
          new DemoOptionSet(
              ByteString.of(new byte[] {0x01, 0x00}), ByteString.of(new byte[] {0x01, 0x00}));
      DemoOptionSet setBit2 =
          new DemoOptionSet(
              ByteString.of(new byte[] {0x04, 0x00}), ByteString.of(new byte[] {0x04, 0x00}));

      NodeId arrayId = demoNode("Demo.Values.OptionSet.Array.DemoOptionSet");
      ExtensionObject[] clearedArray = {encode(clearAll), encode(clearAll)};

      ExecutorService executor = Executors.newFixedThreadPool(2);
      try {
        for (int round = 0; round < 15; round++) {
          assertTrue(writeValue(scalarId, Variant.ofExtensionObject(encode(clearAll))).isGood());

          CyclicBarrier barrier = new CyclicBarrier(2);
          Future<StatusCode> first =
              executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return writeValue(scalarId, Variant.ofExtensionObject(encode(setBit0)));
              });
          Future<StatusCode> second =
              executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return writeValue(scalarId, Variant.ofExtensionObject(encode(setBit2)));
              });

          assertTrue(first.get(10, TimeUnit.SECONDS).isGood());
          assertTrue(second.get(10, TimeUnit.SECONDS).isGood());

          DemoOptionSet merged =
              (DemoOptionSet)
                  ((ExtensionObject)
                          client
                              .readValue(0.0, TimestampsToReturn.Neither, scalarId)
                              .value()
                              .value())
                      .decode(encodingContext);
          assertArrayEquals(new byte[] {0x05, 0x00}, merged.getValue().bytesOrEmpty());

          // Concurrent indexed writes to distinct array elements must be serialized before Milo
          // reads the current array so neither write overwrites the other with a stale element.
          assertTrue(writeValue(arrayId, Variant.ofExtensionObjectArray(clearedArray)).isGood());
          CyclicBarrier indexBarrier = new CyclicBarrier(2);
          Future<StatusCode> idx0 =
              executor.submit(() -> {
                indexBarrier.await(5, TimeUnit.SECONDS);
                return writeValueWithIndexRange(
                    arrayId,
                    "0",
                    Variant.ofExtensionObjectArray(new ExtensionObject[] {encode(setBit0)}));
              });
          Future<StatusCode> idx1 =
              executor.submit(() -> {
                indexBarrier.await(5, TimeUnit.SECONDS);
                return writeValueWithIndexRange(
                    arrayId,
                    "1",
                    Variant.ofExtensionObjectArray(new ExtensionObject[] {encode(setBit2)}));
              });

          assertTrue(idx0.get(10, TimeUnit.SECONDS).isGood());
          assertTrue(idx1.get(10, TimeUnit.SECONDS).isGood());

          ExtensionObject[] arrayRead =
              assertInstanceOf(
                  ExtensionObject[].class,
                  client.readValue(0.0, TimestampsToReturn.Neither, arrayId).value().value());
          assertArrayEquals(
              new byte[] {0x01, 0x00},
              ((DemoOptionSet) arrayRead[0].decode(encodingContext)).getValue().bytesOrEmpty());
          assertArrayEquals(
              new byte[] {0x04, 0x00},
              ((DemoOptionSet) arrayRead[1].decode(encodingContext)).getValue().bytesOrEmpty());
        }
      } finally {
        executor.shutdownNow();
      }
    }

    @Test
    void structWithOptionSetAndIntegerOptionSetsReadAndWriteAcrossArities() throws Exception {
      NodeId structMatrixId = demoNode("Demo.Values.OptionSet.Matrix.StructWithOptionSet");
      Matrix baselineStruct2x2 =
          Matrix.ofExtensionObject(
              new ExtensionObject[][] {
                {
                  encode(new StructWithOptionSet("P00", DemoOptionSet.of(0x01, 0x00))),
                  encode(new StructWithOptionSet("P01", DemoOptionSet.of(0x02, 0x01)))
                },
                {
                  encode(new StructWithOptionSet("P10", DemoOptionSet.of(0x04, 0x02))),
                  encode(new StructWithOptionSet("P11", DemoOptionSet.of(0x0F, 0x03)))
                }
              });
      assertTrue(writeValue(structMatrixId, Variant.ofMatrix(baselineStruct2x2)).isGood());

      StructWithOptionSet partial =
          new StructWithOptionSet(
              "Updated",
              new DemoOptionSet(
                  ByteString.of(new byte[] {0x02, 0x00}), ByteString.of(new byte[] {0x02, 0x00})));
      Matrix matrixWrite =
          Matrix.ofExtensionObject(
              new ExtensionObject[][] {
                {encode(partial), encode(new StructWithOptionSet("B", DemoOptionSet.of(0x01, 0)))},
                {
                  encode(new StructWithOptionSet("C", DemoOptionSet.of(0x02, 0))),
                  encode(new StructWithOptionSet("D", DemoOptionSet.of(0x04, 0)))
                }
              });
      assertTrue(writeValue(structMatrixId, Variant.ofMatrix(matrixWrite)).isGood());

      Matrix readMatrix =
          assertInstanceOf(
              Matrix.class,
              client.readValue(0.0, TimestampsToReturn.Neither, structMatrixId).value().value());
      StructWithOptionSet merged =
          (StructWithOptionSet)
              ((ExtensionObject[]) readMatrix.getElements())[0].decode(encodingContext);
      // Initial P00 had 0x01,0x00; ValidBits 0x02,0x00 preserved bit 0 and set bit 1 -> 0x03,0x00.
      assertArrayEquals(new byte[] {0x03, 0x00}, merged.getOptions().getValue().bytesOrEmpty());

      // Width-changing matrix write (2x2 -> 2x3) preserves [1,0] (0x02, 0x00) at [1,0] (flat 3),
      // not [1,1] (0x04, 0x00).
      DemoOptionSet keepAll =
          new DemoOptionSet(
              ByteString.of(new byte[] {0x00, 0x00}), ByteString.of(new byte[] {0x00, 0x00}));
      Matrix widerStruct =
          Matrix.ofExtensionObject(
              new ExtensionObject[][] {
                {
                  encode(new StructWithOptionSet("W00", keepAll)),
                  encode(new StructWithOptionSet("W01", keepAll)),
                  encode(new StructWithOptionSet("W02", keepAll))
                },
                {
                  encode(new StructWithOptionSet("W10", keepAll)),
                  encode(new StructWithOptionSet("W11", keepAll)),
                  encode(new StructWithOptionSet("W12", DemoOptionSet.of(0x08, 0x00)))
                }
              });
      assertTrue(writeValue(structMatrixId, Variant.ofMatrix(widerStruct)).isGood());

      Matrix widerStructRead =
          assertInstanceOf(
              Matrix.class,
              client.readValue(0.0, TimestampsToReturn.Neither, structMatrixId).value().value());
      ExtensionObject[] widerStructFlat = (ExtensionObject[]) widerStructRead.getElements();
      assertArrayEquals(
          new byte[] {0x02, 0x00},
          ((StructWithOptionSet) widerStructFlat[3].decode(encodingContext))
              .getOptions()
              .getValue()
              .bytesOrEmpty());
      assertArrayEquals(
          new byte[] {0x04, 0x00},
          ((StructWithOptionSet) widerStructFlat[4].decode(encodingContext))
              .getOptions()
              .getValue()
              .bytesOrEmpty());

      // Integer OptionSets across arities (UInt32 + Byte)
      assertTrue(
          writeValue(
                  demoNode("Demo.Values.OptionSet.Scalar.DemoOptionSetUInt32"),
                  Variant.ofUInt32(uint(0b11001)))
              .isGood());
      assertTrue(
          writeValue(
                  demoNode("Demo.Values.OptionSet.Matrix.PermissionType"),
                  Variant.ofMatrix(
                      Matrix.ofUInt32(
                          new UInteger[][] {
                            {uint(0x0001), uint(0x0003)},
                            {uint(0x0005), uint(0x0011)}
                          })))
              .isGood());
      assertTrue(
          writeValue(
                  demoNode("Demo.Values.OptionSet.Scalar.AccessLevelType"),
                  Variant.ofByte(ubyte(0x07)))
              .isGood());
      assertTrue(
          writeValue(
                  demoNode("Demo.Values.OptionSet.Array.AccessLevelType"),
                  Variant.ofByteArray(new UByte[] {ubyte(0x01), ubyte(0x03)}))
              .isGood());
      assertTrue(
          writeValue(
                  demoNode("Demo.Values.OptionSet.Matrix.AccessLevelType"),
                  Variant.ofMatrix(
                      Matrix.ofByte(
                          new UByte[][] {
                            {ubyte(0x01), ubyte(0x03)},
                            {ubyte(0x02), ubyte(0x07)}
                          })))
              .isGood());
    }
  }

  @Nested
  class UnionsStructuresAndAbstract {

    // Generic OPC UA clients have no compiled codecs for server types and rely solely on
    // DataTypeDefinition and HasEncoding references to decode every Union and Structure.
    @Test
    void unionsAndStructuresDecodeDynamicallyAcrossAllArities() throws Exception {
      EncodingContext dynamicContext = client.getDynamicEncodingContext();

      List<String> unionNames = List.of("UnionOfScalar", "UnionOfArray", "UnionOfMatrix");
      for (String name : unionNames) {
        for (String arity : List.of("Scalar", "Array", "Matrix")) {
          assertDecodesDynamically(
              dynamicContext, demoNode("Demo.Values.Union." + arity + "." + name), arity);
        }
      }

      // Verify explicit SwitchField=0 null union
      DataValue nullUnionDv =
          client.readValue(
              0.0,
              TimestampsToReturn.Neither,
              demoNode("Demo.Values.Union.Scalar.UnionOfScalarNull"));
      UnionOfScalar decodedNullUnion =
          (UnionOfScalar) ((ExtensionObject) nullUnionDv.value().value()).decode(encodingContext);
      assertTrue(DemoValueFilters.isNullUnion(decodedNullUnion));

      List<String> structNames =
          List.of(
              "ConcreteTestType",
              "ConcreteTestTypeEx",
              "StructWithBuiltinScalarFields",
              "StructWithBuiltinScalarFieldsEx",
              "StructWithBuiltinArrayFields",
              "StructWithBuiltinArrayFieldsEx",
              "StructWithBuiltinMatrixFields",
              "StructWithBuiltinMatrixFieldsEx",
              "StructWithOptionalScalarFields",
              "StructWithOptionalArrayFields",
              "StructWithOptionalMatrixFields",
              "StructWithAbstractScalarFields",
              "StructWithAbstractArrayFields",
              "StructWithAbstractMatrixFields",
              "StructWithStructureScalarFields",
              "StructWithStructureArrayFields",
              "StructWithStructureMatrixFields",
              "DemoRecord",
              "NullableFieldsStruct");
      for (String name : structNames) {
        for (String arity : List.of("Scalar", "Array", "Matrix")) {
          assertDecodesDynamically(
              dynamicContext, demoNode("Demo.Values.Structure." + arity + "." + name), arity);
        }
      }

      // OpaqueStruct intentionally omits DataTypeDefinition; UnknownEncoding uses unregistered ID.
      DataValue opaqueDef =
          readAttribute(
              demoNode(DemoTypesFragment.OPAQUE_STRUCT_IDENTIFIER), AttributeId.DataTypeDefinition);
      assertEquals(StatusCodes.Bad_AttributeIdInvalid, opaqueDef.statusCode().getValue());

      ExtensionObject.Binary unknownXo =
          (ExtensionObject.Binary)
              client
                  .readValue(
                      0.0,
                      TimestampsToReturn.Neither,
                      demoNode("Demo.Values.Structure.Scalar.UnknownEncoding"))
                  .value()
                  .value();
      assertEquals(
          demoNode(DemoTypesFragment.UNKNOWN_ENCODING_IDENTIFIER), unknownXo.getEncodingOrTypeId());
    }

    @Test
    void emptyAbstractVariablesStartNullAndAcceptConcreteSubtypesAcrossArities() throws Exception {
      NodeId emptyNumScalar = demoNode("Demo.Values.Abstract.Scalar.NumberEmpty");
      assertNull(client.readValue(0.0, TimestampsToReturn.Neither, emptyNumScalar).value().value());
      assertTrue(writeValue(emptyNumScalar, Variant.ofDouble(3.14)).isGood());
      assertTrue(writeValue(emptyNumScalar, Variant.NULL_VALUE).isGood());

      NodeId emptyStructArray = demoNode("Demo.Values.Abstract.Array.StructureEmpty");
      assertNull(
          client.readValue(0.0, TimestampsToReturn.Neither, emptyStructArray).value().value());
      ExtensionObject[] records = {
        encode(new DemoRecord(1, "A", 1.0, true)),
        encode(new ConcreteTestType((short) 2, 2.0, "B", false))
      };
      assertTrue(writeValue(emptyStructArray, Variant.ofExtensionObjectArray(records)).isGood());
      assertTrue(writeValue(emptyStructArray, Variant.NULL_VALUE).isGood());

      NodeId emptyAbstractMatrix = demoNode("Demo.Values.Abstract.Matrix.AbstractTestTypeEmpty");
      assertNull(
          client.readValue(0.0, TimestampsToReturn.Neither, emptyAbstractMatrix).value().value());
      Matrix subtypes =
          Matrix.ofExtensionObject(
              new ExtensionObject[][] {
                {
                  encode(new ConcreteTestType((short) 1, 1.0, "a", true)),
                  encode(new ConcreteTestTypeEx((short) 2, 2.0, "b", false, uint(5)))
                },
                {
                  encode(new ConcreteTestTypeEx((short) 3, 3.0, "c", true, uint(6))),
                  encode(new ConcreteTestType((short) 4, 4.0, "d", false))
                }
              });
      assertTrue(writeValue(emptyAbstractMatrix, Variant.ofMatrix(subtypes)).isGood());
      assertTrue(writeValue(emptyAbstractMatrix, Variant.NULL_VALUE).isGood());
    }
  }

  @Nested
  class DataAccess {

    @Test
    void dataAccessItemsEnforceRangesAndSynchronizeValueAsText() throws Exception {
      // AnalogItem enforces EURange [0, 100]
      NodeId analogItem = demoNode("Demo.Values.DataAccess.AnalogItem");
      assertTrue(writeValue(analogItem, Variant.ofDouble(75.5)).isGood());
      assertEquals(
          StatusCodes.Bad_OutOfRange, writeValue(analogItem, Variant.ofDouble(150.0)).getValue());

      // AnalogUnit, TwoStateDiscrete, MultiStateDiscrete accept valid writes
      assertTrue(
          writeValue(demoNode("Demo.Values.DataAccess.AnalogUnit"), Variant.ofDouble(2.5))
              .isGood());
      assertTrue(
          writeValue(demoNode("Demo.Values.DataAccess.TwoStateDiscrete"), Variant.ofBoolean(true))
              .isGood());
      assertTrue(
          writeValue(
                  demoNode("Demo.Values.DataAccess.MultiStateDiscrete"), Variant.ofUInt32(uint(2)))
              .isGood());

      // MultiStateValueDiscrete rejects unlisted values and updates ValueAsText on valid writes.
      NodeId msv = demoNode("Demo.Values.DataAccess.MultiStateValueDiscrete");
      NodeId valueAsText = findPropertyNodeId(msv, "ValueAsText");
      DataValue initialText = client.readValue(0.0, TimestampsToReturn.Neither, valueAsText);
      assertTrue(initialText.statusCode().isGood());
      assertEquals(LocalizedText.english("Running"), initialText.value().value());

      assertEquals(StatusCodes.Bad_OutOfRange, writeValue(msv, Variant.ofInt32(15)).getValue());
      assertTrue(writeValue(msv, Variant.ofInt32(30)).isGood());
      DataValue updatedText = client.readValue(0.0, TimestampsToReturn.Neither, valueAsText);
      assertTrue(updatedText.statusCode().isGood());
      assertEquals(LocalizedText.english("Stopping"), updatedText.value().value());

      // ArrayItem subtypes enforce EURange [0, 100]; XYArrayItem uses XVType[] with null axisSteps.
      assertTrue(
          writeValue(
                  demoNode("Demo.Values.DataAccess.YArrayItem"),
                  Variant.ofDoubleArray(new Double[] {5.0, 15.0, 25.0}))
              .isGood());

      NodeId xyArrayItem = demoNode("Demo.Values.DataAccess.XYArrayItem");
      assertEquals(
          NodeIds.XVType, readAttribute(xyArrayItem, AttributeId.DataType).value().value());
      NodeId xAxisDefId = findPropertyNodeId(xyArrayItem, "XAxisDefinition");
      AxisInformation xAxisDef =
          (AxisInformation)
              ((ExtensionObject)
                      client.readValue(0.0, TimestampsToReturn.Neither, xAxisDefId).value().value())
                  .decode(encodingContext);
      assertNull(xAxisDef.getAxisSteps());

      assertTrue(
          writeValue(
                  xyArrayItem,
                  Variant.ofExtensionObjectArray(
                      ExtensionObject.encodeArray(
                          encodingContext,
                          new XVType[] {new XVType(1.5, 25.0f), new XVType(2.5, 75.0f)})))
              .isGood());
      assertEquals(
          StatusCodes.Bad_OutOfRange,
          writeValue(
                  xyArrayItem,
                  Variant.ofExtensionObjectArray(
                      ExtensionObject.encodeArray(
                          encodingContext, new XVType[] {new XVType(1.0, 200.0f)})))
              .getValue());

      assertTrue(
          writeValue(
                  demoNode("Demo.Values.DataAccess.ImageItem"),
                  Variant.ofMatrix(
                      Matrix.ofDouble(
                          new Double[][] {
                            {1.0, 2.0, 3.0},
                            {4.0, 5.0, 6.0},
                            {7.0, 8.0, 9.0}
                          })))
              .isGood());
    }
  }

  // region Helpers

  private void assertDecodesDynamically(
      EncodingContext dynamicContext, NodeId nodeId, String expectedArity) throws Exception {

    DataValue dv = client.readValue(0.0, TimestampsToReturn.Neither, nodeId);
    assertTrue(dv.statusCode().isGood(), () -> nodeId + " read failed: " + dv.statusCode());
    Object raw = dv.value().value();
    assertNotNull(raw, () -> nodeId + " value should not be null");

    switch (expectedArity) {
      case "Scalar" -> {
        ExtensionObject xo =
            assertInstanceOf(
                ExtensionObject.class, raw, () -> nodeId + " expected scalar ExtensionObject");
        assertNotNull(xo.decode(dynamicContext), () -> nodeId + " scalar decode failed");
      }
      case "Array" -> {
        ExtensionObject[] array =
            assertInstanceOf(
                ExtensionObject[].class, raw, () -> nodeId + " expected 1-D ExtensionObject[]");
        assertTrue(array.length > 0, () -> nodeId + " array should not be empty");
        for (ExtensionObject xo : array) {
          assertNotNull(xo.decode(dynamicContext), () -> nodeId + " array element decode failed");
        }
      }
      case "Matrix" -> {
        Matrix matrix =
            assertInstanceOf(Matrix.class, raw, () -> nodeId + " expected Matrix payload");
        assertEquals(
            2,
            matrix.getValueRank(),
            () -> nodeId + " expected matrix rank 2, got " + matrix.getValueRank());
        ExtensionObject[] flat =
            assertInstanceOf(
                ExtensionObject[].class,
                matrix.getElements(),
                () -> nodeId + " expected ExtensionObject[] matrix elements");
        assertTrue(flat.length > 0, () -> nodeId + " matrix should not be empty");
        for (ExtensionObject xo : flat) {
          assertNotNull(xo.decode(dynamicContext), () -> nodeId + " matrix element decode failed");
        }
      }
      default -> throw new IllegalArgumentException("Unexpected arity: " + expectedArity);
    }
  }

  private NodeId demoNode(String identifier) {
    return new NodeId(demoNsIndex, identifier);
  }

  private ExtensionObject encode(UaStructuredType struct) {
    return ExtensionObject.encode(encodingContext, struct);
  }

  private DataValue readAttribute(NodeId nodeId, AttributeId attributeId) throws Exception {
    ReadValueId rvid = new ReadValueId(nodeId, attributeId.uid(), null, QualifiedName.NULL_VALUE);
    return client.read(0.0, TimestampsToReturn.Neither, List.of(rvid)).getResults()[0];
  }

  private StatusCode writeValue(NodeId nodeId, Variant value) throws Exception {
    return writeValueWithIndexRange(nodeId, null, value);
  }

  private StatusCode writeValueWithIndexRange(
      NodeId nodeId, @Nullable String indexRange, Variant value) throws Exception {

    WriteValue writeValue =
        new WriteValue(nodeId, AttributeId.Value.uid(), indexRange, DataValue.valueOnly(value));
    WriteResponse response = client.write(List.of(writeValue));
    return response.getResults()[0];
  }

  private NodeId findPropertyNodeId(NodeId ownerId, String propertyBrowseName) throws Exception {
    BrowseDescription bd =
        new BrowseDescription(
            ownerId,
            BrowseDirection.Forward,
            NodeIds.HasProperty,
            true,
            uint(0),
            uint(BrowseResultMask.All.getValue()));

    BrowseResult result = client.browse(bd);
    assertTrue(result.getStatusCode().isGood());
    ReferenceDescription[] refs = result.getReferences();
    assertNotNull(refs);

    NamespaceTable nsTable = client.getNamespaceTable();
    for (ReferenceDescription ref : refs) {
      if (propertyBrowseName.equals(ref.getBrowseName().getName())) {
        return ref.getNodeId().toNodeId(nsTable).orElseThrow();
      }
    }
    throw new AssertionError("Property not found on " + ownerId + ": " + propertyBrowseName);
  }

  // endregion
}
