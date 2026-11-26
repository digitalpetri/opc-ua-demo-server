package com.digitalpetri.opcua.server.namespace.demo.editor;

import static com.digitalpetri.opcua.server.DemoConfigLimits.MIN_SUPPORTED_SAMPLE_RATE;
import static com.digitalpetri.opcua.server.namespace.demo.Util.deriveChildNodeId;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Locale;
import java.util.StringJoiner;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.CefactEngineeringUnits;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.core.Reference.Direction;
import org.eclipse.milo.opcua.sdk.core.ValueRanks;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.UaNodeManager;
import org.eclipse.milo.opcua.sdk.server.methods.AbstractMethodInvocationHandler;
import org.eclipse.milo.opcua.sdk.server.methods.InvalidArgumentException;
import org.eclipse.milo.opcua.sdk.server.model.variables.AnalogItemTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.variables.MultiStateDiscreteTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.variables.TwoStateDiscreteTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode.UaMethodNodeBuilder;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNodeContext;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode.UaVariableNodeBuilder;
import org.eclipse.milo.opcua.sdk.server.nodes.instantiation.InstantiationRequest;
import org.eclipse.milo.opcua.stack.core.Diagnostic;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.ReferenceTypes;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.encoding.EncodingContext;
import org.eclipse.milo.opcua.stack.core.types.UaStructuredType;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.Matrix;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.structured.Argument;
import org.eclipse.milo.opcua.stack.core.types.structured.Range;

/**
 * Builds the {@code Demo/ValueEditorTest/Methods} folder and its eight callable test Methods, all
 * executable over {@code SecurityPolicy.None} with an anonymous identity.
 */
final class ValueEditorMethods {

  private static final String[] CONTROLLER_MODES = new String[] {"Off", "Auto", "Manual"};

  private final OpcUaServer server;
  private final UaNodeContext nodeContext;
  private final UaNodeManager nodeManager;
  private final UShort namespaceIndex;

  ValueEditorMethods(
      OpcUaServer server,
      UaNodeContext nodeContext,
      UaNodeManager nodeManager,
      UShort namespaceIndex) {

    this.server = server;
    this.nodeContext = nodeContext;
    this.nodeManager = nodeManager;
    this.namespaceIndex = namespaceIndex;
  }

  void addMethods(NodeId parentFolderId, NodeId demoRecordTypeId) {
    UaFolderNode methodsFolder =
        new UaFolderNode(
            nodeContext,
            deriveChildNodeId(parentFolderId, "Methods"),
            new QualifiedName(namespaceIndex, "Methods"),
            LocalizedText.english("Methods"));

    nodeManager.addNode(methodsFolder);
    methodsFolder.addReference(
        new Reference(
            methodsFolder.getNodeId(),
            ReferenceTypes.HasComponent,
            parentFolderId.expanded(),
            Direction.INVERSE));

    NodeId folderId = methodsFolder.getNodeId();
    addEchoRecord(folderId, demoRecordTypeId);
    addSumInt32Array(folderId);
    addGetRecords(folderId, demoRecordTypeId);
    addFormatMessage(folderId);
    addInspectAbstract(folderId);
    addDescribeRank(folderId);
    addConfigureController(folderId);
    addValidateInputs(folderId);
  }

  private void addEchoRecord(NodeId folderId, NodeId demoRecordTypeId) {
    Argument[] inputs = {
      new Argument(
          "record",
          demoRecordTypeId,
          ValueRanks.Scalar,
          null,
          LocalizedText.english("DemoRecord structure to echo back"))
    };
    Argument[] outputs = {
      new Argument(
          "record",
          demoRecordTypeId,
          ValueRanks.Scalar,
          null,
          LocalizedText.english("Echoed DemoRecord structure"))
    };

    UaMethodNode methodNode =
        createMethod(
            folderId,
            "EchoRecord",
            "Echoes a DemoRecord structure input back as output",
            inputs,
            outputs);

    methodNode.setInvocationHandler(
        new AbstractMethodInvocationHandler(methodNode) {
          @Override
          public Argument[] getInputArguments() {
            return inputs;
          }

          @Override
          public Argument[] getOutputArguments() {
            return outputs;
          }

          @Override
          protected Variant[] invoke(InvocationContext context, Variant[] inputValues)
              throws UaException {

            Object value = inputValues[0].value();
            if (!(value instanceof DemoRecord record)) {
              throw InvalidArgumentException.builder(1)
                  .reject(
                      0,
                      StatusCodes.Bad_OutOfRange,
                      Diagnostic.text("record must be a non-null DemoRecord"))
                  .build();
            }

            EncodingContext encodingContext = context.getServer().getStaticEncodingContext();
            ExtensionObject encoded = ExtensionObject.encode(encodingContext, record);
            return new Variant[] {Variant.ofExtensionObject(encoded)};
          }
        });
  }

  private void addSumInt32Array(NodeId folderId) {
    Argument[] inputs = {
      new Argument(
          "values",
          NodeIds.Int32,
          ValueRanks.OneDimension,
          new UInteger[] {uint(0)},
          LocalizedText.english("One-dimensional Int32 array to sum"))
    };
    Argument[] outputs = {
      new Argument(
          "sum",
          NodeIds.Int64,
          ValueRanks.Scalar,
          null,
          LocalizedText.english("Sum of the supplied elements")),
      new Argument(
          "count",
          NodeIds.Int32,
          ValueRanks.Scalar,
          null,
          LocalizedText.english("Number of elements summed"))
    };

    UaMethodNode methodNode =
        createMethod(
            folderId,
            "SumInt32Array",
            "Sums an Int32 array and returns the Int64 sum and element count",
            inputs,
            outputs);

    methodNode.setInvocationHandler(
        new AbstractMethodInvocationHandler(methodNode) {
          @Override
          public Argument[] getInputArguments() {
            return inputs;
          }

          @Override
          public Argument[] getOutputArguments() {
            return outputs;
          }

          @Override
          protected Variant[] invoke(InvocationContext context, Variant[] inputValues) {
            Object raw = inputValues[0].value();
            long sum = 0L;
            int count = 0;

            if (raw instanceof Integer[] values) {
              count = values.length;
              for (Integer v : values) {
                if (v != null) {
                  sum += v;
                }
              }
            } else if (raw instanceof int[] values) {
              count = values.length;
              for (int v : values) {
                sum += v;
              }
            }

            return new Variant[] {Variant.ofInt64(sum), Variant.ofInt32(count)};
          }
        });
  }

  private void addGetRecords(NodeId folderId, NodeId demoRecordTypeId) {
    Argument[] inputs = {
      new Argument(
          "count",
          NodeIds.Int32,
          ValueRanks.Scalar,
          null,
          LocalizedText.english("Number of records to generate in [0, 10]"))
    };
    Argument[] outputs = {
      new Argument(
          "primary",
          demoRecordTypeId,
          ValueRanks.Scalar,
          null,
          LocalizedText.english("Primary DemoRecord structure")),
      new Argument(
          "records",
          demoRecordTypeId,
          ValueRanks.OneDimension,
          new UInteger[] {uint(0)},
          LocalizedText.english("Generated array of DemoRecord structures"))
    };

    UaMethodNode methodNode =
        createMethod(
            folderId,
            "GetRecords",
            "Returns a server-defined DemoRecord and an array of count DemoRecord structures",
            inputs,
            outputs);

    methodNode.setInvocationHandler(
        new AbstractMethodInvocationHandler(methodNode) {
          @Override
          public Argument[] getInputArguments() {
            return inputs;
          }

          @Override
          public Argument[] getOutputArguments() {
            return outputs;
          }

          @Override
          protected Variant[] invoke(InvocationContext context, Variant[] inputValues)
              throws UaException {

            Object raw = inputValues[0].value();
            if (!(raw instanceof Integer count) || count < 0 || count > 10) {
              throw InvalidArgumentException.builder(1)
                  .reject(
                      0, StatusCodes.Bad_OutOfRange, Diagnostic.text("count must be within 0..10"))
                  .build();
            }

            EncodingContext encodingContext = context.getServer().getStaticEncodingContext();
            DemoRecord primary = new DemoRecord(0, "Primary", 100.0, true);
            DemoRecord[] records = new DemoRecord[count];
            for (int i = 0; i < count; i++) {
              records[i] = new DemoRecord(i + 1, "Record-" + (i + 1), (i + 1) * 10.5, i % 2 == 0);
            }

            return new Variant[] {
              Variant.ofExtensionObject(ExtensionObject.encode(encodingContext, primary)),
              Variant.ofExtensionObjectArray(ExtensionObject.encodeArray(encodingContext, records))
            };
          }
        });
  }

  private void addFormatMessage(NodeId folderId) {
    Argument[] inputs = {
      new Argument(
          "message",
          NodeIds.String,
          ValueRanks.Scalar,
          null,
          LocalizedText.english("Base message text (required)")),
      new Argument(
          "repeatCount",
          NodeIds.Int32,
          ValueRanks.Scalar,
          null,
          LocalizedText.english("Optional repeat count in [1, 20], default 1")),
      new Argument(
          "uppercase",
          NodeIds.Boolean,
          ValueRanks.Scalar,
          null,
          LocalizedText.english("Optional uppercase flag, default false"))
    };
    Argument[] outputs = {
      new Argument(
          "formatted",
          NodeIds.String,
          ValueRanks.Scalar,
          null,
          LocalizedText.english("Formatted message"))
    };

    UaMethodNode methodNode =
        createMethod(
            folderId,
            "FormatMessage",
            "Formats a message with two optional trailing input arguments",
            inputs,
            outputs);

    addArgumentDescriptionVariable(
        methodNode.getNodeId(),
        NodeIds.HasOptionalInputArgumentDescription,
        "repeatCount",
        NodeIds.Int32,
        Variant.ofInt32(1),
        "Default repeat count (1)");

    addArgumentDescriptionVariable(
        methodNode.getNodeId(),
        NodeIds.HasOptionalInputArgumentDescription,
        "uppercase",
        NodeIds.Boolean,
        Variant.ofBoolean(false),
        "Default uppercase flag (false)");

    methodNode.setInvocationHandler(
        new AbstractMethodInvocationHandler(methodNode) {
          @Override
          public Argument[] getInputArguments() {
            return inputs;
          }

          @Override
          public Argument[] getOutputArguments() {
            return outputs;
          }

          @Override
          protected int getRequiredInputArgumentCount(Argument[] inputArguments) {
            return 1;
          }

          @Override
          protected Variant[] invoke(InvocationContext context, Variant[] inputValues)
              throws UaException {

            String message = (String) inputValues[0].value();
            int repeatCount = 1;
            boolean uppercase = false;

            InvalidArgumentException.Builder errors =
                InvalidArgumentException.builder(inputValues.length);
            boolean hasErrors = false;

            if (message == null) {
              errors.reject(
                  0, StatusCodes.Bad_OutOfRange, Diagnostic.text("message must not be null"));
              hasErrors = true;
            }

            if (inputValues.length >= 2 && inputValues[1].value() != null) {
              repeatCount = (Integer) inputValues[1].value();
              if (repeatCount < 1 || repeatCount > 20) {
                errors.reject(
                    1,
                    StatusCodes.Bad_OutOfRange,
                    Diagnostic.text("repeatCount must be within 1..20"));
                hasErrors = true;
              }
            }

            if (inputValues.length >= 3 && inputValues[2].value() != null) {
              uppercase = (Boolean) inputValues[2].value();
            }

            if (hasErrors) {
              throw errors.build();
            }

            StringJoiner joiner = new StringJoiner(" ");
            for (int i = 0; i < repeatCount; i++) {
              joiner.add(message);
            }
            String result = joiner.toString();
            if (uppercase) {
              result = result.toUpperCase(Locale.ROOT);
            }

            return new Variant[] {Variant.ofString(result)};
          }
        });
  }

  private void addInspectAbstract(NodeId folderId) {
    Argument[] inputs = {
      new Argument(
          "number",
          NodeIds.Number,
          ValueRanks.Scalar,
          null,
          LocalizedText.english("Any concrete Number subtype")),
      new Argument(
          "payload",
          NodeIds.Structure,
          ValueRanks.Scalar,
          null,
          LocalizedText.english("Any concrete Structure subtype"))
    };
    Argument[] outputs = {
      new Argument(
          "numberType",
          NodeIds.String,
          ValueRanks.Scalar,
          null,
          LocalizedText.english("Runtime type name of the supplied number")),
      new Argument(
          "payloadType",
          NodeIds.String,
          ValueRanks.Scalar,
          null,
          LocalizedText.english("DataType NodeId of the supplied structure payload")),
      new Argument(
          "echoedNumber",
          NodeIds.Number,
          ValueRanks.Scalar,
          null,
          LocalizedText.english("The supplied number echoed back"))
    };

    UaMethodNode methodNode =
        createMethod(
            folderId,
            "InspectAbstract",
            "Accepts abstract Number and Structure inputs and reports their concrete types",
            inputs,
            outputs);

    methodNode.setInvocationHandler(
        new AbstractMethodInvocationHandler(methodNode) {
          @Override
          public Argument[] getInputArguments() {
            return inputs;
          }

          @Override
          public Argument[] getOutputArguments() {
            return outputs;
          }

          @Override
          protected Variant[] invoke(InvocationContext context, Variant[] inputValues) {
            Object number = inputValues[0].value();
            Object payload = inputValues[1].value();

            String numberType = number == null ? "null" : number.getClass().getSimpleName();
            String payloadType;
            if (payload == null) {
              payloadType = "null";
            } else if (payload instanceof UaStructuredType struct) {
              payloadType = struct.getTypeId().toParseableString();
            } else {
              payloadType = payload.getClass().getSimpleName();
            }

            Variant echoedNumber = number != null ? inputValues[0] : Variant.NULL_VALUE;

            return new Variant[] {
              Variant.ofString(numberType), Variant.ofString(payloadType), echoedNumber
            };
          }
        });
  }

  private void addDescribeRank(NodeId folderId) {
    Argument[] inputs = {
      new Argument(
          "anyValue",
          NodeIds.BaseDataType,
          ValueRanks.Any,
          null,
          LocalizedText.english("Value of any DataType and any ValueRank (-2)")),
      new Argument(
          "scalarOrArray",
          NodeIds.Int32,
          ValueRanks.ScalarOrOneDimension,
          null,
          LocalizedText.english("Scalar Int32 or one-dimensional Int32 array (-3)"))
    };
    Argument[] outputs = {
      new Argument(
          "anyShape",
          NodeIds.String,
          ValueRanks.Scalar,
          null,
          LocalizedText.english("Summary of anyValue's rank, dimensions, and element type")),
      new Argument(
          "intCount",
          NodeIds.Int32,
          ValueRanks.Scalar,
          null,
          LocalizedText.english("1 for a scalar Int32, array length for an array, or 0 for null"))
    };

    UaMethodNode methodNode =
        createMethod(
            folderId,
            "DescribeRank",
            "Accepts ValueRank Any (-2) and ScalarOrOneDimension (-3) inputs",
            inputs,
            outputs);

    methodNode.setInvocationHandler(
        new AbstractMethodInvocationHandler(methodNode) {
          @Override
          public Argument[] getInputArguments() {
            return inputs;
          }

          @Override
          public Argument[] getOutputArguments() {
            return outputs;
          }

          @Override
          protected Variant[] invoke(InvocationContext context, Variant[] inputValues) {
            String anyShape = describeShape(inputValues[0].value());

            Object scalarOrArray = inputValues[1].value();
            int intCount;
            if (scalarOrArray == null) {
              intCount = 0;
            } else if (scalarOrArray.getClass().isArray()) {
              intCount = Array.getLength(scalarOrArray);
            } else {
              intCount = 1;
            }

            return new Variant[] {Variant.ofString(anyShape), Variant.ofInt32(intCount)};
          }
        });
  }

  private void addConfigureController(NodeId folderId) {
    Argument[] inputs = {
      new Argument(
          "setpoint",
          NodeIds.Double,
          ValueRanks.Scalar,
          null,
          LocalizedText.english("Target temperature setpoint in [0, 100] °C")),
      new Argument(
          "retries",
          NodeIds.Int32,
          ValueRanks.Scalar,
          null,
          LocalizedText.english("Non-negative retry count")),
      new Argument(
          "enabled",
          NodeIds.Boolean,
          ValueRanks.Scalar,
          null,
          LocalizedText.english("Controller enable state")),
      new Argument(
          "mode",
          NodeIds.UInt32,
          ValueRanks.Scalar,
          null,
          LocalizedText.english("Controller mode index: 0=Off, 1=Auto, 2=Manual"))
    };
    Argument[] outputs = {
      new Argument(
          "summary",
          NodeIds.String,
          ValueRanks.Scalar,
          null,
          LocalizedText.english("Applied controller configuration summary"))
    };

    UaMethodNode methodNode =
        createMethod(
            folderId,
            "ConfigureController",
            "Exposes HasArgumentDescription nodes carrying defaults, EURange, units, and state"
                + " names",
            inputs,
            outputs);

    try {
      AnalogItemTypeNode setpointDesc =
          instantiate(
              InstantiationRequest.of(AnalogItemTypeNode.class, NodeIds.AnalogItemType)
                  .nodeId(deriveChildNodeId(methodNode.getNodeId(), "setpoint"))
                  .browseName(new QualifiedName(namespaceIndex, "setpoint"))
                  .displayName(LocalizedText.english("setpoint"))
                  .description(LocalizedText.english("Default setpoint 50.0 °C, EURange 0..100"))
                  .parent(methodNode.getNodeId(), NodeIds.HasArgumentDescription));

      setpointDesc.setDataType(NodeIds.Double);
      setpointDesc.setValueRank(ValueRanks.Scalar);
      setpointDesc.setAccessLevel(AccessLevel.toValue(AccessLevel.CurrentRead));
      setpointDesc.setUserAccessLevel(AccessLevel.toValue(AccessLevel.CurrentRead));
      setpointDesc.setMinimumSamplingInterval(MIN_SUPPORTED_SAMPLE_RATE);
      setpointDesc.setEuRange(new Range(0.0, 100.0));
      setpointDesc.setEngineeringUnits(CefactEngineeringUnits.CODE_CEL);
      setpointDesc.setValue(new DataValue(Variant.ofDouble(50.0)));

      addArgumentDescriptionVariable(
          methodNode.getNodeId(),
          NodeIds.HasArgumentDescription,
          "retries",
          NodeIds.Int32,
          Variant.ofInt32(3),
          "Default retry count (3)");

      TwoStateDiscreteTypeNode enabledDesc =
          instantiate(
              InstantiationRequest.of(TwoStateDiscreteTypeNode.class, NodeIds.TwoStateDiscreteType)
                  .nodeId(deriveChildNodeId(methodNode.getNodeId(), "enabled"))
                  .browseName(new QualifiedName(namespaceIndex, "enabled"))
                  .displayName(LocalizedText.english("enabled"))
                  .description(LocalizedText.english("Default enabled=true (Enabled / Disabled)"))
                  .parent(methodNode.getNodeId(), NodeIds.HasArgumentDescription));

      enabledDesc.setDataType(NodeIds.Boolean);
      enabledDesc.setValueRank(ValueRanks.Scalar);
      enabledDesc.setAccessLevel(AccessLevel.toValue(AccessLevel.CurrentRead));
      enabledDesc.setUserAccessLevel(AccessLevel.toValue(AccessLevel.CurrentRead));
      enabledDesc.setMinimumSamplingInterval(MIN_SUPPORTED_SAMPLE_RATE);
      enabledDesc.setTrueState(LocalizedText.english("Enabled"));
      enabledDesc.setFalseState(LocalizedText.english("Disabled"));
      enabledDesc.setValue(new DataValue(Variant.ofBoolean(true)));

      MultiStateDiscreteTypeNode modeDesc =
          instantiate(
              InstantiationRequest.of(
                      MultiStateDiscreteTypeNode.class, NodeIds.MultiStateDiscreteType)
                  .nodeId(deriveChildNodeId(methodNode.getNodeId(), "mode"))
                  .browseName(new QualifiedName(namespaceIndex, "mode"))
                  .displayName(LocalizedText.english("mode"))
                  .description(LocalizedText.english("Default mode=1 (Off / Auto / Manual)"))
                  .parent(methodNode.getNodeId(), NodeIds.HasArgumentDescription));

      modeDesc.setDataType(NodeIds.UInt32);
      modeDesc.setValueRank(ValueRanks.Scalar);
      modeDesc.setAccessLevel(AccessLevel.toValue(AccessLevel.CurrentRead));
      modeDesc.setUserAccessLevel(AccessLevel.toValue(AccessLevel.CurrentRead));
      modeDesc.setMinimumSamplingInterval(MIN_SUPPORTED_SAMPLE_RATE);
      modeDesc.setEnumStrings(
          new LocalizedText[] {
            LocalizedText.english("Off"),
            LocalizedText.english("Auto"),
            LocalizedText.english("Manual")
          });
      modeDesc.setValue(new DataValue(Variant.ofUInt32(uint(1))));
    } catch (UaException e) {
      throw new IllegalStateException("failed to instantiate ConfigureController descriptions", e);
    }

    methodNode.setInvocationHandler(
        new AbstractMethodInvocationHandler(methodNode) {
          @Override
          public Argument[] getInputArguments() {
            return inputs;
          }

          @Override
          public Argument[] getOutputArguments() {
            return outputs;
          }

          @Override
          protected Variant[] invoke(InvocationContext context, Variant[] inputValues)
              throws UaException {

            Double setpoint = (Double) inputValues[0].value();
            Integer retries = (Integer) inputValues[1].value();
            Boolean enabled = (Boolean) inputValues[2].value();
            UInteger mode = (UInteger) inputValues[3].value();

            InvalidArgumentException.Builder errors = InvalidArgumentException.builder(4);
            boolean hasErrors = false;

            if (setpoint == null || setpoint.isNaN() || setpoint < 0.0 || setpoint > 100.0) {
              errors.reject(
                  0,
                  StatusCodes.Bad_OutOfRange,
                  Diagnostic.text("setpoint must be within [0.0, 100.0] °C"));
              hasErrors = true;
            }
            if (retries == null || retries < 0) {
              errors.reject(1, StatusCodes.Bad_OutOfRange, Diagnostic.text("retries must be >= 0"));
              hasErrors = true;
            }
            if (enabled == null) {
              errors.reject(
                  2, StatusCodes.Bad_OutOfRange, Diagnostic.text("enabled must not be null"));
              hasErrors = true;
            }
            if (mode == null || mode.longValue() >= CONTROLLER_MODES.length) {
              errors.reject(
                  3,
                  StatusCodes.Bad_OutOfRange,
                  Diagnostic.text("mode must be 0 (Off), 1 (Auto), or 2 (Manual)"));
              hasErrors = true;
            }

            if (hasErrors) {
              throw errors.build();
            }

            String summary =
                String.format(
                    Locale.ROOT,
                    "setpoint=%.1f °C, retries=%d, enabled=%s, mode=%s",
                    setpoint,
                    retries,
                    enabled ? "Enabled" : "Disabled",
                    CONTROLLER_MODES[mode.intValue()]);

            return new Variant[] {Variant.ofString(summary)};
          }
        });
  }

  private void addValidateInputs(NodeId folderId) {
    Argument[] inputs = {
      new Argument(
          "positiveInt",
          NodeIds.Int32,
          ValueRanks.Scalar,
          null,
          LocalizedText.english("Must be strictly greater than 0")),
      new Argument(
          "nonEmptyLabel",
          NodeIds.String,
          ValueRanks.Scalar,
          null,
          LocalizedText.english("Must be non-blank and at most 32 characters")),
      new Argument(
          "percent",
          NodeIds.Double,
          ValueRanks.Scalar,
          null,
          LocalizedText.english("Must be within [0.0, 100.0]"))
    };
    Argument[] outputs = {
      new Argument(
          "summary",
          NodeIds.String,
          ValueRanks.Scalar,
          null,
          LocalizedText.english("Validated values summary"))
    };

    UaMethodNode methodNode =
        createMethod(
            folderId,
            "ValidateInputs",
            "Returns Bad_InvalidArgument with per-argument StatusCodes and diagnostics on invalid"
                + " inputs",
            inputs,
            outputs);

    methodNode.setInvocationHandler(
        new AbstractMethodInvocationHandler(methodNode) {
          @Override
          public Argument[] getInputArguments() {
            return inputs;
          }

          @Override
          public Argument[] getOutputArguments() {
            return outputs;
          }

          @Override
          protected Variant[] invoke(InvocationContext context, Variant[] inputValues)
              throws UaException {

            Integer positiveInt = (Integer) inputValues[0].value();
            String label = (String) inputValues[1].value();
            Double percent = (Double) inputValues[2].value();

            InvalidArgumentException.Builder errors = InvalidArgumentException.builder(3);
            boolean hasErrors = false;

            if (positiveInt == null || positiveInt <= 0) {
              errors.reject(
                  0, StatusCodes.Bad_OutOfRange, Diagnostic.text("positiveInt must be > 0"));
              hasErrors = true;
            }

            if (label == null || label.isBlank() || label.length() > 32) {
              errors.reject(
                  1,
                  StatusCodes.Bad_OutOfRange,
                  Diagnostic.text("nonEmptyLabel must be non-blank and at most 32 characters"));
              hasErrors = true;
            }

            if (percent == null || percent.isNaN() || percent < 0.0 || percent > 100.0) {
              errors.reject(
                  2,
                  StatusCodes.Bad_OutOfRange,
                  Diagnostic.text("percent must be within [0.0, 100.0]"));
              hasErrors = true;
            }

            if (hasErrors) {
              throw errors.build();
            }

            String summary =
                String.format(Locale.ROOT, "%s: %d (%.1f%%)", label, positiveInt, percent);
            return new Variant[] {Variant.ofString(summary)};
          }
        });
  }

  private UaMethodNode createMethod(
      NodeId parentFolderId,
      String name,
      String description,
      Argument[] inputs,
      Argument[] outputs) {

    UaMethodNode methodNode =
        new UaMethodNodeBuilder(nodeContext)
            .setNodeId(deriveChildNodeId(parentFolderId, name))
            .setBrowseName(new QualifiedName(namespaceIndex, name))
            .setDisplayName(LocalizedText.english(name))
            .setDescription(LocalizedText.english(description))
            .setExecutable(true)
            .setUserExecutable(true)
            .build();

    if (inputs.length > 0) {
      methodNode.setInputArguments(inputs);
    }
    if (outputs.length > 0) {
      methodNode.setOutputArguments(outputs);
    }

    nodeManager.addNode(methodNode);
    methodNode.addReference(
        new Reference(
            methodNode.getNodeId(),
            ReferenceTypes.HasComponent,
            parentFolderId.expanded(),
            Direction.INVERSE));

    return methodNode;
  }

  private void addArgumentDescriptionVariable(
      NodeId methodNodeId,
      NodeId referenceTypeId,
      String argumentName,
      NodeId dataTypeId,
      Variant defaultValue,
      String description) {

    UaVariableNode variableNode =
        new UaVariableNodeBuilder(nodeContext)
            .setNodeId(deriveChildNodeId(methodNodeId, argumentName))
            .setBrowseName(new QualifiedName(namespaceIndex, argumentName))
            .setDisplayName(LocalizedText.english(argumentName))
            .setDescription(LocalizedText.english(description))
            .setDataType(dataTypeId)
            .setValueRank(ValueRanks.Scalar)
            .setAccessLevel(AccessLevel.toValue(AccessLevel.CurrentRead))
            .setUserAccessLevel(AccessLevel.toValue(AccessLevel.CurrentRead))
            .setMinimumSamplingInterval(MIN_SUPPORTED_SAMPLE_RATE)
            .setValue(new DataValue(defaultValue))
            .build();

    nodeManager.addNode(variableNode);
    variableNode.addReference(
        new Reference(
            variableNode.getNodeId(), referenceTypeId, methodNodeId.expanded(), Direction.INVERSE));
  }

  private <T extends org.eclipse.milo.opcua.sdk.server.nodes.UaNode> T instantiate(
      InstantiationRequest.Builder<T> request) throws UaException {

    return server
        .getNodeInstantiator()
        .instantiate(request.legacyPathStrings().target(nodeManager).build())
        .root();
  }

  private static String describeShape(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof Matrix matrix) {
      String elemType = matrix.getElementType().map(Class::getSimpleName).orElse("Object");
      return "matrix" + Arrays.toString(matrix.getDimensions()) + ":" + elemType;
    }
    if (value.getClass().isArray()) {
      int length = Array.getLength(value);
      String elemType = value.getClass().getComponentType().getSimpleName();
      return "array[" + length + "]:" + elemType;
    }
    return "scalar:" + value.getClass().getSimpleName();
  }
}
