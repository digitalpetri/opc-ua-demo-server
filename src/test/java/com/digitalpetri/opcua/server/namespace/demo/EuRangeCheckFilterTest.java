package com.digitalpetri.opcua.server.namespace.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.Matrix;
import org.junit.jupiter.api.Test;

class EuRangeCheckFilterTest {

  // region Scalar Validation

  @Test
  void validateScalarValue_withinRange_succeeds() throws UaException {
    // Should not throw when value is within range
    EuRangeCheckFilter.validateScalarValue(50.0, 0.0, 100.0);
    EuRangeCheckFilter.validateScalarValue(0.0, 0.0, 100.0);
    EuRangeCheckFilter.validateScalarValue(100.0, 0.0, 100.0);
  }

  @Test
  void validateScalarValue_belowRange_throws() {
    UaException ex =
        assertThrows(
            UaException.class, () -> EuRangeCheckFilter.validateScalarValue(-1.0, 0.0, 100.0));
    assertEquals(StatusCodes.Bad_OutOfRange, ex.getStatusCode().getValue());
  }

  @Test
  void validateScalarValue_aboveRange_throws() {
    UaException ex =
        assertThrows(
            UaException.class, () -> EuRangeCheckFilter.validateScalarValue(101.0, 0.0, 100.0));
    assertEquals(StatusCodes.Bad_OutOfRange, ex.getStatusCode().getValue());
  }

  // endregion

  // region Array Validation

  @Test
  void validateArrayValue_allWithinRange_succeeds() throws UaException {
    Object[] array = new Object[] {0.0, 25.0, 50.0, 75.0, 100.0};
    EuRangeCheckFilter.validateArrayValue(array, 0.0, 100.0);
  }

  @Test
  void validateArrayValue_elementBelowRange_throws() {
    Object[] array = new Object[] {0.0, -1.0, 50.0};
    UaException ex =
        assertThrows(
            UaException.class, () -> EuRangeCheckFilter.validateArrayValue(array, 0.0, 100.0));
    assertEquals(StatusCodes.Bad_OutOfRange, ex.getStatusCode().getValue());
  }

  @Test
  void validateArrayValue_elementAboveRange_throws() {
    Object[] array = new Object[] {50.0, 101.0};
    UaException ex =
        assertThrows(
            UaException.class, () -> EuRangeCheckFilter.validateArrayValue(array, 0.0, 100.0));
    assertEquals(StatusCodes.Bad_OutOfRange, ex.getStatusCode().getValue());
  }

  @Test
  void validateArrayValue_nonNumberElement_throws() {
    Object[] array = new Object[] {50.0, "invalid"};
    UaException ex =
        assertThrows(
            UaException.class, () -> EuRangeCheckFilter.validateArrayValue(array, 0.0, 100.0));
    assertEquals(StatusCodes.Bad_TypeMismatch, ex.getStatusCode().getValue());
  }

  // endregion

  // region Matrix Validation

  @Test
  void validateMatrixValue_allWithinRange_succeeds() throws UaException {
    Double[][] data = new Double[][] {{0.0, 25.0}, {50.0, 100.0}};
    Matrix matrix = new Matrix(data);
    EuRangeCheckFilter.validateMatrixValue(matrix, 0.0, 100.0);
  }

  @Test
  void validateMatrixValue_elementBelowRange_throws() {
    Double[][] data = new Double[][] {{0.0, -1.0}, {50.0, 100.0}};
    Matrix matrix = new Matrix(data);
    UaException ex =
        assertThrows(
            UaException.class, () -> EuRangeCheckFilter.validateMatrixValue(matrix, 0.0, 100.0));
    assertEquals(StatusCodes.Bad_OutOfRange, ex.getStatusCode().getValue());
  }

  @Test
  void validateMatrixValue_elementAboveRange_throws() {
    Double[][] data = new Double[][] {{0.0, 50.0}, {75.0, 101.0}};
    Matrix matrix = new Matrix(data);
    UaException ex =
        assertThrows(
            UaException.class, () -> EuRangeCheckFilter.validateMatrixValue(matrix, 0.0, 100.0));
    assertEquals(StatusCodes.Bad_OutOfRange, ex.getStatusCode().getValue());
  }

  // endregion
}
