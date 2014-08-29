/**
 * Autogenerated by Thrift Compiler (0.9.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package org.apache.blur.thrift.generated;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */



import org.apache.blur.thirdparty.thrift_0_9_0.scheme.IScheme;
import org.apache.blur.thirdparty.thrift_0_9_0.scheme.SchemeFactory;
import org.apache.blur.thirdparty.thrift_0_9_0.scheme.StandardScheme;

import org.apache.blur.thirdparty.thrift_0_9_0.scheme.TupleScheme;
import org.apache.blur.thirdparty.thrift_0_9_0.protocol.TTupleProtocol;
import org.apache.blur.thirdparty.thrift_0_9_0.protocol.TProtocolException;
import org.apache.blur.thirdparty.thrift_0_9_0.EncodingUtils;
import org.apache.blur.thirdparty.thrift_0_9_0.TException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.Set;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.Collections;
import java.util.BitSet;
import java.nio.ByteBuffer;
import java.util.Arrays;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

public class Response extends org.apache.blur.thirdparty.thrift_0_9_0.TUnion<Response, Response._Fields> {
  private static final org.apache.blur.thirdparty.thrift_0_9_0.protocol.TStruct STRUCT_DESC = new org.apache.blur.thirdparty.thrift_0_9_0.protocol.TStruct("Response");
  private static final org.apache.blur.thirdparty.thrift_0_9_0.protocol.TField SHARD_TO_VALUE_FIELD_DESC = new org.apache.blur.thirdparty.thrift_0_9_0.protocol.TField("shardToValue", org.apache.blur.thirdparty.thrift_0_9_0.protocol.TType.MAP, (short)1);
  private static final org.apache.blur.thirdparty.thrift_0_9_0.protocol.TField VALUE_FIELD_DESC = new org.apache.blur.thirdparty.thrift_0_9_0.protocol.TField("value", org.apache.blur.thirdparty.thrift_0_9_0.protocol.TType.STRUCT, (short)2);

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.blur.thirdparty.thrift_0_9_0.TFieldIdEnum {
    SHARD_TO_VALUE((short)1, "shardToValue"),
    VALUE((short)2, "value");

    private static final Map<String, _Fields> byName = new HashMap<String, _Fields>();

    static {
      for (_Fields field : EnumSet.allOf(_Fields.class)) {
        byName.put(field.getFieldName(), field);
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, or null if its not found.
     */
    public static _Fields findByThriftId(int fieldId) {
      switch(fieldId) {
        case 1: // SHARD_TO_VALUE
          return SHARD_TO_VALUE;
        case 2: // VALUE
          return VALUE;
        default:
          return null;
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, throwing an exception
     * if it is not found.
     */
    public static _Fields findByThriftIdOrThrow(int fieldId) {
      _Fields fields = findByThriftId(fieldId);
      if (fields == null) throw new IllegalArgumentException("Field " + fieldId + " doesn't exist!");
      return fields;
    }

    /**
     * Find the _Fields constant that matches name, or null if its not found.
     */
    public static _Fields findByName(String name) {
      return byName.get(name);
    }

    private final short _thriftId;
    private final String _fieldName;

    _Fields(short thriftId, String fieldName) {
      _thriftId = thriftId;
      _fieldName = fieldName;
    }

    public short getThriftFieldId() {
      return _thriftId;
    }

    public String getFieldName() {
      return _fieldName;
    }
  }

  public static final Map<_Fields, org.apache.blur.thirdparty.thrift_0_9_0.meta_data.FieldMetaData> metaDataMap;
  static {
    Map<_Fields, org.apache.blur.thirdparty.thrift_0_9_0.meta_data.FieldMetaData> tmpMap = new EnumMap<_Fields, org.apache.blur.thirdparty.thrift_0_9_0.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.SHARD_TO_VALUE, new org.apache.blur.thirdparty.thrift_0_9_0.meta_data.FieldMetaData("shardToValue", org.apache.blur.thirdparty.thrift_0_9_0.TFieldRequirementType.DEFAULT, 
        new org.apache.blur.thirdparty.thrift_0_9_0.meta_data.MapMetaData(org.apache.blur.thirdparty.thrift_0_9_0.protocol.TType.MAP, 
            new org.apache.blur.thirdparty.thrift_0_9_0.meta_data.FieldValueMetaData(org.apache.blur.thirdparty.thrift_0_9_0.protocol.TType.STRING), 
            new org.apache.blur.thirdparty.thrift_0_9_0.meta_data.StructMetaData(org.apache.blur.thirdparty.thrift_0_9_0.protocol.TType.STRUCT, Value.class))));
    tmpMap.put(_Fields.VALUE, new org.apache.blur.thirdparty.thrift_0_9_0.meta_data.FieldMetaData("value", org.apache.blur.thirdparty.thrift_0_9_0.TFieldRequirementType.DEFAULT, 
        new org.apache.blur.thirdparty.thrift_0_9_0.meta_data.StructMetaData(org.apache.blur.thirdparty.thrift_0_9_0.protocol.TType.STRUCT, Value.class)));
    metaDataMap = Collections.unmodifiableMap(tmpMap);
    org.apache.blur.thirdparty.thrift_0_9_0.meta_data.FieldMetaData.addStructMetaDataMap(Response.class, metaDataMap);
  }

  public Response() {
    super();
  }

  public Response(_Fields setField, Object value) {
    super(setField, value);
  }

  public Response(Response other) {
    super(other);
  }
  public Response deepCopy() {
    return new Response(this);
  }

  public static Response shardToValue(Map<String,Value> value) {
    Response x = new Response();
    x.setShardToValue(value);
    return x;
  }

  public static Response value(Value value) {
    Response x = new Response();
    x.setValue(value);
    return x;
  }


  @Override
  protected void checkType(_Fields setField, Object value) throws ClassCastException {
    switch (setField) {
      case SHARD_TO_VALUE:
        if (value instanceof Map) {
          break;
        }
        throw new ClassCastException("Was expecting value of type Map<String,Value> for field 'shardToValue', but got " + value.getClass().getSimpleName());
      case VALUE:
        if (value instanceof Value) {
          break;
        }
        throw new ClassCastException("Was expecting value of type Value for field 'value', but got " + value.getClass().getSimpleName());
      default:
        throw new IllegalArgumentException("Unknown field id " + setField);
    }
  }

  @Override
  protected Object standardSchemeReadValue(org.apache.blur.thirdparty.thrift_0_9_0.protocol.TProtocol iprot, org.apache.blur.thirdparty.thrift_0_9_0.protocol.TField field) throws org.apache.blur.thirdparty.thrift_0_9_0.TException {
    _Fields setField = _Fields.findByThriftId(field.id);
    if (setField != null) {
      switch (setField) {
        case SHARD_TO_VALUE:
          if (field.type == SHARD_TO_VALUE_FIELD_DESC.type) {
            Map<String,Value> shardToValue;
            {
              org.apache.blur.thirdparty.thrift_0_9_0.protocol.TMap _map232 = iprot.readMapBegin();
              shardToValue = new HashMap<String,Value>(2*_map232.size);
              for (int _i233 = 0; _i233 < _map232.size; ++_i233)
              {
                String _key234; // optional
                Value _val235; // required
                _key234 = iprot.readString();
                _val235 = new Value();
                _val235.read(iprot);
                shardToValue.put(_key234, _val235);
              }
              iprot.readMapEnd();
            }
            return shardToValue;
          } else {
            org.apache.blur.thirdparty.thrift_0_9_0.protocol.TProtocolUtil.skip(iprot, field.type);
            return null;
          }
        case VALUE:
          if (field.type == VALUE_FIELD_DESC.type) {
            Value value;
            value = new Value();
            value.read(iprot);
            return value;
          } else {
            org.apache.blur.thirdparty.thrift_0_9_0.protocol.TProtocolUtil.skip(iprot, field.type);
            return null;
          }
        default:
          throw new IllegalStateException("setField wasn't null, but didn't match any of the case statements!");
      }
    } else {
      return null;
    }
  }

  @Override
  protected void standardSchemeWriteValue(org.apache.blur.thirdparty.thrift_0_9_0.protocol.TProtocol oprot) throws org.apache.blur.thirdparty.thrift_0_9_0.TException {
    switch (setField_) {
      case SHARD_TO_VALUE:
        Map<String,Value> shardToValue = (Map<String,Value>)value_;
        {
          oprot.writeMapBegin(new org.apache.blur.thirdparty.thrift_0_9_0.protocol.TMap(org.apache.blur.thirdparty.thrift_0_9_0.protocol.TType.STRING, org.apache.blur.thirdparty.thrift_0_9_0.protocol.TType.STRUCT, shardToValue.size()));
          for (Map.Entry<String, Value> _iter236 : shardToValue.entrySet())
          {
            oprot.writeString(_iter236.getKey());
            _iter236.getValue().write(oprot);
          }
          oprot.writeMapEnd();
        }
        return;
      case VALUE:
        Value value = (Value)value_;
        value.write(oprot);
        return;
      default:
        throw new IllegalStateException("Cannot write union with unknown field " + setField_);
    }
  }

  @Override
  protected Object tupleSchemeReadValue(org.apache.blur.thirdparty.thrift_0_9_0.protocol.TProtocol iprot, short fieldID) throws org.apache.blur.thirdparty.thrift_0_9_0.TException {
    _Fields setField = _Fields.findByThriftId(fieldID);
    if (setField != null) {
      switch (setField) {
        case SHARD_TO_VALUE:
          Map<String,Value> shardToValue;
          {
            org.apache.blur.thirdparty.thrift_0_9_0.protocol.TMap _map237 = iprot.readMapBegin();
            shardToValue = new HashMap<String,Value>(2*_map237.size);
            for (int _i238 = 0; _i238 < _map237.size; ++_i238)
            {
              String _key239; // optional
              Value _val240; // required
              _key239 = iprot.readString();
              _val240 = new Value();
              _val240.read(iprot);
              shardToValue.put(_key239, _val240);
            }
            iprot.readMapEnd();
          }
          return shardToValue;
        case VALUE:
          Value value;
          value = new Value();
          value.read(iprot);
          return value;
        default:
          throw new IllegalStateException("setField wasn't null, but didn't match any of the case statements!");
      }
    } else {
      throw new TProtocolException("Couldn't find a field with field id " + fieldID);
    }
  }

  @Override
  protected void tupleSchemeWriteValue(org.apache.blur.thirdparty.thrift_0_9_0.protocol.TProtocol oprot) throws org.apache.blur.thirdparty.thrift_0_9_0.TException {
    switch (setField_) {
      case SHARD_TO_VALUE:
        Map<String,Value> shardToValue = (Map<String,Value>)value_;
        {
          oprot.writeMapBegin(new org.apache.blur.thirdparty.thrift_0_9_0.protocol.TMap(org.apache.blur.thirdparty.thrift_0_9_0.protocol.TType.STRING, org.apache.blur.thirdparty.thrift_0_9_0.protocol.TType.STRUCT, shardToValue.size()));
          for (Map.Entry<String, Value> _iter241 : shardToValue.entrySet())
          {
            oprot.writeString(_iter241.getKey());
            _iter241.getValue().write(oprot);
          }
          oprot.writeMapEnd();
        }
        return;
      case VALUE:
        Value value = (Value)value_;
        value.write(oprot);
        return;
      default:
        throw new IllegalStateException("Cannot write union with unknown field " + setField_);
    }
  }

  @Override
  protected org.apache.blur.thirdparty.thrift_0_9_0.protocol.TField getFieldDesc(_Fields setField) {
    switch (setField) {
      case SHARD_TO_VALUE:
        return SHARD_TO_VALUE_FIELD_DESC;
      case VALUE:
        return VALUE_FIELD_DESC;
      default:
        throw new IllegalArgumentException("Unknown field id " + setField);
    }
  }

  @Override
  protected org.apache.blur.thirdparty.thrift_0_9_0.protocol.TStruct getStructDesc() {
    return STRUCT_DESC;
  }

  @Override
  protected _Fields enumForId(short id) {
    return _Fields.findByThriftIdOrThrow(id);
  }

  public _Fields fieldForId(int fieldId) {
    return _Fields.findByThriftId(fieldId);
  }


  public Map<String,Value> getShardToValue() {
    if (getSetField() == _Fields.SHARD_TO_VALUE) {
      return (Map<String,Value>)getFieldValue();
    } else {
      throw new RuntimeException("Cannot get field 'shardToValue' because union is currently set to " + getFieldDesc(getSetField()).name);
    }
  }

  public void setShardToValue(Map<String,Value> value) {
    if (value == null) throw new NullPointerException();
    setField_ = _Fields.SHARD_TO_VALUE;
    value_ = value;
  }

  public Value getValue() {
    if (getSetField() == _Fields.VALUE) {
      return (Value)getFieldValue();
    } else {
      throw new RuntimeException("Cannot get field 'value' because union is currently set to " + getFieldDesc(getSetField()).name);
    }
  }

  public void setValue(Value value) {
    if (value == null) throw new NullPointerException();
    setField_ = _Fields.VALUE;
    value_ = value;
  }

  public boolean isSetShardToValue() {
    return setField_ == _Fields.SHARD_TO_VALUE;
  }


  public boolean isSetValue() {
    return setField_ == _Fields.VALUE;
  }


  public boolean equals(Object other) {
    if (other instanceof Response) {
      return equals((Response)other);
    } else {
      return false;
    }
  }

  public boolean equals(Response other) {
    return other != null && getSetField() == other.getSetField() && getFieldValue().equals(other.getFieldValue());
  }

  @Override
  public int compareTo(Response other) {
    int lastComparison = org.apache.blur.thirdparty.thrift_0_9_0.TBaseHelper.compareTo(getSetField(), other.getSetField());
    if (lastComparison == 0) {
      return org.apache.blur.thirdparty.thrift_0_9_0.TBaseHelper.compareTo(getFieldValue(), other.getFieldValue());
    }
    return lastComparison;
  }


  /**
   * If you'd like this to perform more respectably, use the hashcode generator option.
   */
  @Override
  public int hashCode() {
    return 0;
  }

  private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
    try {
      write(new org.apache.blur.thirdparty.thrift_0_9_0.protocol.TCompactProtocol(new org.apache.blur.thirdparty.thrift_0_9_0.transport.TIOStreamTransport(out)));
    } catch (org.apache.blur.thirdparty.thrift_0_9_0.TException te) {
      throw new java.io.IOException(te);
    }
  }


  private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
    try {
      read(new org.apache.blur.thirdparty.thrift_0_9_0.protocol.TCompactProtocol(new org.apache.blur.thirdparty.thrift_0_9_0.transport.TIOStreamTransport(in)));
    } catch (org.apache.blur.thirdparty.thrift_0_9_0.TException te) {
      throw new java.io.IOException(te);
    }
  }


}