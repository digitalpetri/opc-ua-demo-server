package com.digitalpetri.opcua.server.namespace.demo;

import org.eclipse.milo.opcua.sdk.server.model.variables.ArrayItemType;
import org.eclipse.milo.opcua.sdk.server.model.variables.BaseAnalogType;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilter;
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilterContext;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.Matrix;
import org.eclipse.milo.opcua.stack.core.types.structured.Range;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EuRangeCheckFilter implements AttributeFilter {

  private static final Logger LOGGER = LoggerFactory.getLogger(EuRangeCheckFilter.class);

  public static final EuRangeCheckFilter INSTANCE = new EuRangeCheckFilter();

  @Override
  public void writeAttribute(
      AttributeFilterContext ctx, AttributeId attributeId, @Nullable Object value)
      throws UaException {

    UaNode node = ctx.getNode();

    if (attributeId == AttributeId.Value && value instanceof DataValue dataValue) {
      Range euRange = null;

      if (node instanceof BaseAnalogType baseAnalog) {
        euRange = baseAnalog.getEuRange();
      } else if (node instanceof ArrayItemType arrayItem) {
        euRange = arrayItem.getEuRange();
      }

      if (euRange != null) {
        Object v = dataValue.getValue().getValue();
        Double low = euRange.getLow();
        Double high = euRange.getHigh();

        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace(
              "Validating EU Range: node={}, value={}, low={}, high={}",
              node.getNodeId().toParseableString(),
              v,
              low,
              high);
        }

        switch (v) {
          case Number n -> validateScalarValue(n, low, high);
          case Object[] array -> validateArrayValue(array, low, high);
          case Matrix matrix -> validateMatrixValue(matrix, low, high);
          case null, default ->
              throw new UaException(
                  StatusCodes.Bad_TypeMismatch,
                  "value %s is not a number, array, or matrix".formatted(v));
        }
      }
    }

    ctx.writeAttribute(attributeId, value);
  }

  static void validateScalarValue(Number n, Double low, Double high) throws UaException {
    if (n.doubleValue() < low || n.doubleValue() > high) {
      throw new UaException(
          StatusCodes.Bad_OutOfRange,
          "value %s is out of range [%s, %s]".formatted(n, low, high));
    }
  }

  static void validateArrayValue(Object[] array, Double low, Double high) throws UaException {
    for (int i = 0; i < array.length; i++) {
      Object element = array[i];
      if (element instanceof Number n) {
        if (n.doubleValue() < low || n.doubleValue() > high) {
          throw new UaException(
              StatusCodes.Bad_OutOfRange,
              "array element [%d] value %s is out of range [%s, %s]".formatted(i, n, low, high));
        }
      } else {
        throw new UaException(
            StatusCodes.Bad_TypeMismatch,
            "array element [%d] value %s is not a number".formatted(i, element));
      }
    }
  }

  static void validateMatrixValue(Matrix matrix, Double low, Double high) throws UaException {
    Object elements = matrix.getElements();
    if (elements instanceof Object[] array) {
      for (int i = 0; i < array.length; i++) {
        Object element = array[i];
        if (element instanceof Number n) {
          if (n.doubleValue() < low || n.doubleValue() > high) {
            throw new UaException(
                StatusCodes.Bad_OutOfRange,
                "matrix element [%d] value %s is out of range [%s, %s]"
                    .formatted(i, n, low, high));
          }
        } else {
          throw new UaException(
              StatusCodes.Bad_TypeMismatch,
              "matrix element [%d] value %s is not a number".formatted(i, element));
        }
      }
    } else {
      throw new UaException(
          StatusCodes.Bad_TypeMismatch,
          "matrix elements are not an array: %s".formatted(elements));
    }
  }
}
