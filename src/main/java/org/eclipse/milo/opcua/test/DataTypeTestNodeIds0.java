package org.eclipse.milo.opcua.test;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;

abstract class DataTypeTestNodeIds0 extends DataTypeTestNodeIdsBase {
  public static final ExpandedNodeId DataTypeTestType =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(1003));

  public static final ExpandedNodeId AbstractTestType =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(3003));

  public static final ExpandedNodeId StructWithBuiltinScalarFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(3004));

  public static final ExpandedNodeId StructWithAbstractArrayFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(3005));

  public static final ExpandedNodeId ConcreteTestType =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(3006));

  public static final ExpandedNodeId StructWithOptionalArrayFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(3007));

  public static final ExpandedNodeId StructWithBuiltinArrayFieldsEx =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(3008));

  public static final ExpandedNodeId ConcreteTestTypeEx =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(3009));

  public static final ExpandedNodeId StructWithBuiltinMatrixFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(3010));

  public static final ExpandedNodeId TestEnumType =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(3011));

  public static final ExpandedNodeId StructWithAbstractScalarFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(3012));

  public static final ExpandedNodeId StructWithBuiltinScalarFieldsEx =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(3015));

  public static final ExpandedNodeId StructWithOptionalScalarFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(3016));

  public static final ExpandedNodeId StructWithBuiltinMatrixFieldsEx =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(3017));

  public static final ExpandedNodeId StructWithBuiltinArrayFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(3019));

  public static final ExpandedNodeId UnionOfScalar =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(3020));

  public static final ExpandedNodeId StructWithAbstractMatrixFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(3022));

  public static final ExpandedNodeId UnionOfArray =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(3023));

  public static final ExpandedNodeId StructWithStructureScalarFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(3026));

  public static final ExpandedNodeId StructWithStructureArrayFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(3029));

  public static final ExpandedNodeId StructWithStructureMatrixFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(3032));

  public static final ExpandedNodeId StructWithOptionalMatrixFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(3035));

  public static final ExpandedNodeId UnionOfMatrix =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(3038));

  public static final ExpandedNodeId ConcreteTestType_Encoding_DefaultBinary =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5001));

  public static final ExpandedNodeId ConcreteTestType_Encoding_DefaultXml =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5002));

  public static final ExpandedNodeId ConcreteTestType_Encoding_DefaultJson =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5003));

  public static final ExpandedNodeId ConcreteTestTypeEx_Encoding_DefaultBinary =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5004));

  public static final ExpandedNodeId ConcreteTestTypeEx_Encoding_DefaultXml =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5005));

  public static final ExpandedNodeId ConcreteTestTypeEx_Encoding_DefaultJson =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5006));

  public static final ExpandedNodeId StructWithAbstractScalarFields_Encoding_DefaultBinary =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5007));

  public static final ExpandedNodeId StructWithAbstractScalarFields_Encoding_DefaultXml =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5008));

  public static final ExpandedNodeId StructWithAbstractScalarFields_Encoding_DefaultJson =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5009));

  public static final ExpandedNodeId
      Server_Namespaces_https___github_com_eclipse_milo_DataTypeTest =
          new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5010));

  public static final ExpandedNodeId StructWithOptionalScalarFields_Encoding_DefaultBinary =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5011));

  public static final ExpandedNodeId StructWithOptionalScalarFields_Encoding_DefaultXml =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5012));

  public static final ExpandedNodeId StructWithOptionalScalarFields_Encoding_DefaultJson =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5013));

  public static final ExpandedNodeId StructWithBuiltinArrayFields_Encoding_DefaultBinary =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5014));

  public static final ExpandedNodeId StructWithBuiltinArrayFields_Encoding_DefaultXml =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5015));

  public static final ExpandedNodeId StructWithBuiltinArrayFields_Encoding_DefaultJson =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5016));

  public static final ExpandedNodeId StructWithOptionalArrayFields_Encoding_DefaultBinary =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5017));

  public static final ExpandedNodeId StructWithOptionalArrayFields_Encoding_DefaultXml =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5018));

  public static final ExpandedNodeId StructWithOptionalArrayFields_Encoding_DefaultJson =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5019));

  public static final ExpandedNodeId StructWithBuiltinScalarFields_Encoding_DefaultBinary =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5020));

  public static final ExpandedNodeId StructWithBuiltinScalarFields_Encoding_DefaultXml =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5021));

  public static final ExpandedNodeId StructWithBuiltinScalarFields_Encoding_DefaultJson =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5022));

  public static final ExpandedNodeId StructWithAbstractArrayFields_Encoding_DefaultBinary =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5023));

  public static final ExpandedNodeId StructWithAbstractArrayFields_Encoding_DefaultXml =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5024));

  public static final ExpandedNodeId StructWithAbstractArrayFields_Encoding_DefaultJson =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5025));

  public static final ExpandedNodeId StructWithBuiltinScalarFieldsEx_Encoding_DefaultBinary =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5026));

  public static final ExpandedNodeId StructWithBuiltinScalarFieldsEx_Encoding_DefaultXml =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5027));

  public static final ExpandedNodeId StructWithBuiltinScalarFieldsEx_Encoding_DefaultJson =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5028));

  public static final ExpandedNodeId UnionOfScalar_Encoding_DefaultBinary =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5029));

  public static final ExpandedNodeId UnionOfScalar_Encoding_DefaultXml =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5030));

  public static final ExpandedNodeId UnionOfScalar_Encoding_DefaultJson =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5031));

  public static final ExpandedNodeId UnionOfArray_Encoding_DefaultBinary =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5032));

  public static final ExpandedNodeId UnionOfArray_Encoding_DefaultXml =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5033));

  public static final ExpandedNodeId UnionOfArray_Encoding_DefaultJson =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5034));

  public static final ExpandedNodeId StructWithBuiltinArrayFieldsEx_Encoding_DefaultBinary =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5035));

  public static final ExpandedNodeId StructWithBuiltinArrayFieldsEx_Encoding_DefaultXml =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5036));

  public static final ExpandedNodeId DataTypeTest =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5037));

  public static final ExpandedNodeId DataTypeTestType_Scalar =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5038));

  public static final ExpandedNodeId DataTypeTest_Scalar =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5039));

  public static final ExpandedNodeId StructWithBuiltinArrayFieldsEx_Encoding_DefaultJson =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5040));

  public static final ExpandedNodeId StructWithBuiltinMatrixFields_Encoding_DefaultBinary =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5041));

  public static final ExpandedNodeId StructWithBuiltinMatrixFields_Encoding_DefaultXml =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5042));

  public static final ExpandedNodeId StructWithBuiltinMatrixFields_Encoding_DefaultJson =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5043));

  public static final ExpandedNodeId StructWithBuiltinMatrixFieldsEx_Encoding_DefaultBinary =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5044));

  public static final ExpandedNodeId StructWithBuiltinMatrixFieldsEx_Encoding_DefaultXml =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5045));

  public static final ExpandedNodeId StructWithBuiltinMatrixFieldsEx_Encoding_DefaultJson =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5046));

  public static final ExpandedNodeId StructWithAbstractMatrixFields_Encoding_DefaultBinary =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5047));

  public static final ExpandedNodeId StructWithAbstractMatrixFields_Encoding_DefaultXml =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5048));

  public static final ExpandedNodeId StructWithAbstractMatrixFields_Encoding_DefaultJson =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5049));

  public static final ExpandedNodeId StructWithStructureScalarFields_Encoding_DefaultBinary =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5050));

  public static final ExpandedNodeId StructWithStructureScalarFields_Encoding_DefaultXml =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5051));

  public static final ExpandedNodeId StructWithStructureScalarFields_Encoding_DefaultJson =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5052));

  public static final ExpandedNodeId StructWithStructureArrayFields_Encoding_DefaultBinary =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5053));

  public static final ExpandedNodeId StructWithStructureArrayFields_Encoding_DefaultXml =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5054));

  public static final ExpandedNodeId StructWithStructureArrayFields_Encoding_DefaultJson =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5055));

  public static final ExpandedNodeId StructWithStructureMatrixFields_Encoding_DefaultBinary =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5056));

  public static final ExpandedNodeId StructWithStructureMatrixFields_Encoding_DefaultXml =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5057));

  public static final ExpandedNodeId StructWithStructureMatrixFields_Encoding_DefaultJson =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5058));

  public static final ExpandedNodeId StructWithOptionalMatrixFields_Encoding_DefaultBinary =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5059));

  public static final ExpandedNodeId StructWithOptionalMatrixFields_Encoding_DefaultXml =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5060));

  public static final ExpandedNodeId StructWithOptionalMatrixFields_Encoding_DefaultJson =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5061));

  public static final ExpandedNodeId UnionOfMatrix_Encoding_DefaultBinary =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5062));

  public static final ExpandedNodeId UnionOfMatrix_Encoding_DefaultXml =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5063));

  public static final ExpandedNodeId UnionOfMatrix_Encoding_DefaultJson =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(5064));

  public static final ExpandedNodeId TypeDictionary_BinarySchema =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6001));

  public static final ExpandedNodeId TypeDictionary_BinarySchema_NamespaceUri =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6002));

  public static final ExpandedNodeId TypeDictionary_XmlSchema =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6003));

  public static final ExpandedNodeId TypeDictionary_XmlSchema_NamespaceUri =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6004));

  public static final ExpandedNodeId TypeDictionary_BinarySchema_ConcreteTestType =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6005));

  public static final ExpandedNodeId TypeDictionary_XmlSchema_ConcreteTestType =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6006));

  public static final ExpandedNodeId TypeDictionary_BinarySchema_ConcreteTestTypeEx =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6007));

  public static final ExpandedNodeId TypeDictionary_XmlSchema_ConcreteTestTypeEx =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6008));

  public static final ExpandedNodeId TypeDictionary_BinarySchema_StructWithAbstractScalarFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6009));

  public static final ExpandedNodeId TypeDictionary_XmlSchema_StructWithAbstractScalarFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6010));

  public static final ExpandedNodeId
      Server_Namespaces_https___github_com_eclipse_milo_DataTypeTest_IsNamespaceSubset =
          new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6011));

  public static final ExpandedNodeId
      Server_Namespaces_https___github_com_eclipse_milo_DataTypeTest_NamespacePublicationDate =
          new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6012));

  public static final ExpandedNodeId
      Server_Namespaces_https___github_com_eclipse_milo_DataTypeTest_NamespaceUri =
          new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6013));

  public static final ExpandedNodeId
      Server_Namespaces_https___github_com_eclipse_milo_DataTypeTest_NamespaceVersion =
          new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6014));

  public static final ExpandedNodeId
      Server_Namespaces_https___github_com_eclipse_milo_DataTypeTest_StaticNodeIdTypes =
          new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6015));

  public static final ExpandedNodeId
      Server_Namespaces_https___github_com_eclipse_milo_DataTypeTest_StaticNumericNodeIdRange =
          new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6016));

  public static final ExpandedNodeId
      Server_Namespaces_https___github_com_eclipse_milo_DataTypeTest_StaticStringNodeIdPattern =
          new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6017));

  public static final ExpandedNodeId TypeDictionary_BinarySchema_StructWithOptionalScalarFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6018));

  public static final ExpandedNodeId TypeDictionary_XmlSchema_StructWithOptionalScalarFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6019));

  public static final ExpandedNodeId TypeDictionary_BinarySchema_StructWithBuiltinArrayFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6020));

  public static final ExpandedNodeId TypeDictionary_XmlSchema_StructWithBuiltinArrayFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6021));

  public static final ExpandedNodeId TypeDictionary_BinarySchema_StructWithOptionalArrayFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6022));

  public static final ExpandedNodeId TypeDictionary_XmlSchema_StructWithOptionalArrayFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6023));

  public static final ExpandedNodeId TypeDictionary_BinarySchema_StructWithBuiltinScalarFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6024));

  public static final ExpandedNodeId TypeDictionary_XmlSchema_StructWithBuiltinScalarFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6025));

  public static final ExpandedNodeId TypeDictionary_BinarySchema_StructWithAbstractArrayFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6026));

  public static final ExpandedNodeId TypeDictionary_XmlSchema_StructWithAbstractArrayFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6027));

  public static final ExpandedNodeId TestEnumType_EnumStrings =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6028));

  public static final ExpandedNodeId TypeDictionary_BinarySchema_StructWithBuiltinScalarFieldsEx =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6029));

  public static final ExpandedNodeId TypeDictionary_XmlSchema_StructWithBuiltinScalarFieldsEx =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6030));

  public static final ExpandedNodeId TypeDictionary_BinarySchema_UnionOfScalar =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6031));

  public static final ExpandedNodeId TypeDictionary_XmlSchema_UnionOfScalar =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6032));

  public static final ExpandedNodeId TypeDictionary_BinarySchema_UnionOfArray =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6033));

  public static final ExpandedNodeId TypeDictionary_XmlSchema_UnionOfArray =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6034));

  public static final ExpandedNodeId DataTypeTestType_Scalar_StructWithArrayFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6035));

  public static final ExpandedNodeId TypeDictionary_BinarySchema_StructWithBuiltinArrayFieldsEx =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6036));

  public static final ExpandedNodeId DataTypeTestType_Scalar_StructWithOptionalScalarFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6037));

  public static final ExpandedNodeId DataTypeTestType_Scalar_StructWithOptionalArrayFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6038));

  public static final ExpandedNodeId TypeDictionary_BinarySchema_StructWithBuiltinMatrixFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6039));

  public static final ExpandedNodeId TypeDictionary_XmlSchema_StructWithBuiltinMatrixFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6040));

  public static final ExpandedNodeId DataTypeTest_Scalar_StructWithOptionalArrayFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6041));

  public static final ExpandedNodeId DataTypeTest_Scalar_StructWithOptionalScalarFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6042));

  public static final ExpandedNodeId TypeDictionary_XmlSchema_StructWithBuiltinArrayFieldsEx =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6043));

  public static final ExpandedNodeId TypeDictionary_BinarySchema_StructWithBuiltinMatrixFieldsEx =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6044));

  public static final ExpandedNodeId TypeDictionary_XmlSchema_StructWithBuiltinMatrixFieldsEx =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6045));

  public static final ExpandedNodeId DataTypeTest_Scalar_StructWithArrayFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6046));

  public static final ExpandedNodeId DataTypeTestType_Scalar_StructWithScalarFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6047));

  public static final ExpandedNodeId DataTypeTestType_Scalar_StructWithScalarFieldsEx =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6048));

  public static final ExpandedNodeId DataTypeTest_Scalar_StructWithScalarFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6049));

  public static final ExpandedNodeId DataTypeTest_Scalar_StructWithScalarFieldsEx =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6050));

  public static final ExpandedNodeId DataTypeTestType_Scalar_StructWithArrayFieldsEx =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6051));

  public static final ExpandedNodeId DataTypeTest_Scalar_StructWithArrayFieldsEx =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6052));

  public static final ExpandedNodeId DataTypeTestType_Scalar_StructWithAbstractScalarFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6053));

  public static final ExpandedNodeId DataTypeTestType_Scalar_StructWithAbstractArrayFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6054));

  public static final ExpandedNodeId DataTypeTest_Scalar_StructWithAbstractArrayFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6055));

  public static final ExpandedNodeId DataTypeTest_Scalar_StructWithAbstractScalarFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6056));

  public static final ExpandedNodeId TypeDictionary_BinarySchema_StructWithAbstractMatrixFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6057));

  public static final ExpandedNodeId TypeDictionary_XmlSchema_StructWithAbstractMatrixFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6058));

  public static final ExpandedNodeId TypeDictionary_BinarySchema_StructWithStructureScalarFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6059));

  public static final ExpandedNodeId TypeDictionary_XmlSchema_StructWithStructureScalarFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6060));

  public static final ExpandedNodeId TypeDictionary_BinarySchema_StructWithStructureArrayFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6061));

  public static final ExpandedNodeId TypeDictionary_XmlSchema_StructWithStructureArrayFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6062));

  public static final ExpandedNodeId TypeDictionary_BinarySchema_StructWithStructureMatrixFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6063));

  public static final ExpandedNodeId TypeDictionary_XmlSchema_StructWithStructureMatrixFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6064));

  public static final ExpandedNodeId TypeDictionary_BinarySchema_StructWithOptionalMatrixFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6065));

  public static final ExpandedNodeId TypeDictionary_XmlSchema_StructWithOptionalMatrixFields =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6066));

  public static final ExpandedNodeId TypeDictionary_BinarySchema_UnionOfMatrix =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6067));

  public static final ExpandedNodeId TypeDictionary_XmlSchema_UnionOfMatrix =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6068));

  public static final ExpandedNodeId TypeDictionary_BinarySchema_Deprecated =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6069));

  public static final ExpandedNodeId TypeDictionary_XmlSchema_Deprecated =
      new ExpandedNodeId(UShort.MIN, NAMESPACE_URI, uint(6070));
}
