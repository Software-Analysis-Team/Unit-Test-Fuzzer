package randoop.operation;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Objects;
import randoop.ExecutionOutcome;
import randoop.NormalExecution;
import randoop.sequence.Variable;
import randoop.types.ArrayType;
import randoop.types.Type;
import randoop.types.TypeTuple;

/**
 * InitializedArrayCreation is an {@link Operation} representing the construction of a
 * one-dimensional array with a given element type and length. The InitializedArrayCreation
 * operation requires a list of elements in an initializer. For instance, {@code new int[2]} is the
 * {@code InitializedArrayCreation} in the initialization<br>
 * {@code int[] x = new int[2] { 3, 7 }; }<br>
 * with the initializer list as inputs.
 *
 * <p>In terms of the notation used for the {@link Operation} class, a creation of an array of
 * elements of type <i>e</i> with length <i>n</i> has a signature [ <i>e,...,e</i>] &rarr; <i>t</i>,
 * where [<i>e,...,e</i>] is a list of length <i>n</i>, and <i>t</i> is the array type.
 *
 * <p>InitializedArrayCreation objects are immutable.
 */
public final class InitializedArrayCreation extends CallableOperation {

  // State variables.
  private final int length;
  private final Type elementType;

  /**
   * Creates an object representing the construction of an array that holds values of the element
   * type and has the given length.
   *
   * @param length number of objects allowed in the array
   * @param arrayType the type of array this operation creates
   */
  InitializedArrayCreation(ArrayType arrayType, int length) {
    assert length >= 0 : "array length may not be negative: " + length;

    this.elementType = arrayType.getComponentType();
    this.length = length;
  }

  /**
   * Returns the length of created array.
   *
   * @return length of array created by this object
   */
  public int getLength() {
    return this.length;
  }

  /**
   * {@inheritDoc}
   *
   * @return {@link NormalExecution} object containing constructed array
   */
  @Override
  public ExecutionOutcome execute(Object[] statementInput) {
    if (statementInput.length > length) {
      throw new IllegalArgumentException(
          "Too many arguments: " + statementInput.length + ", capacity: " + length);
    }
    long startTime = System.currentTimeMillis();
    assert statementInput.length == this.length;
    Object theArray = Array.newInstance(this.elementType.getRuntimeClass(), this.length);
    for (int i = 0; i < statementInput.length; i++) {
      Array.set(theArray, i, statementInput[i]);
    }
    long totalTime = System.currentTimeMillis() - startTime;
    return new NormalExecution(theArray, totalTime);
  }

  @Override
  public String toString() {
    return elementType.getBinaryName() + "[" + length + "]";
  }

  /** {@inheritDoc} */
  @Override
  public void appendCode(
      Type declaringType,
      TypeTuple inputTypes,
      Type outputType,
      List<Variable> inputVars,
      StringBuilder b) {
    if (inputVars.size() > length) {
      throw new IllegalArgumentException(
          "Too many arguments: " + inputVars.size() + ", capacity: " + length);
    }

    String arrayTypeName = this.elementType.getFqName();

    b.append("new ").append(arrayTypeName).append("[] { ");
    for (int i = 0; i < inputVars.size(); i++) {
      if (i > 0) {
        b.append(", ");
      }

      String param = getArgumentString(inputVars.get(i));
      b.append(param);
    }
    b.append(" }");
  }

  @Override
  public int hashCode() {
    return Objects.hash(elementType, length);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof InitializedArrayCreation)) {
      return false;
    }
    InitializedArrayCreation otherArrayDecl = (InitializedArrayCreation) o;
    return this.elementType.equals(otherArrayDecl.elementType)
        && this.length == otherArrayDecl.length;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Creates string of the form TYPE[NUMELEMS] where TYPE is the type of the array, and NUMELEMS
   * is the number of elements.
   *
   * <p>Example: int[3]
   *
   * @return string descriptor for array creation
   */
  @Override
  public String toParsableString(Type declaringType, TypeTuple inputTypes, Type outputType) {
    return elementType.getBinaryName() + "[" + Integer.toString(length) + "]";
  }

  @Override
  public String getName() {
    return this.toString();
  }

  /**
   * Parses an array declaration in a string descriptor in the form generated by {@link
   * InitializedArrayCreation#toParsableString(Type, TypeTuple, Type)}.
   *
   * @param str the string to be parsed for the {@code InitializedArrayCreation}
   * @return the array creation for the given string descriptor
   * @throws OperationParseException if string does not have expected form
   * @see OperationParser#parse(String)
   */
  @SuppressWarnings("signature") // parsing
  public static TypedOperation parse(String str) throws OperationParseException {
    int openBr = str.indexOf('[');
    int closeBr = str.indexOf(']');
    String elementTypeName = str.substring(0, openBr);
    String lengthStr = str.substring(openBr + 1, closeBr);

    int length = Integer.parseInt(lengthStr);

    Type elementType;
    try {
      elementType = Type.forName(elementTypeName);
    } catch (ClassNotFoundException e) {
      throw new OperationParseException("Type not found for array element type " + elementTypeName);
    }

    if (elementType.isGeneric()) {
      throw new OperationParseException("Array element type may not be generic " + elementTypeName);
    }

    return TypedOperation.createInitializedArrayCreation(
        ArrayType.ofComponentType(elementType), length);
  }
}