/*
 * Copyright (c) 2024 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.cdk.load.data.avro

import io.airbyte.cdk.load.data.AirbyteValue
import io.airbyte.cdk.load.data.ArrayValue
import io.airbyte.cdk.load.data.BooleanValue
import io.airbyte.cdk.load.data.DateValue
import io.airbyte.cdk.load.data.IntValue
import io.airbyte.cdk.load.data.IntegerValue
import io.airbyte.cdk.load.data.NullValue
import io.airbyte.cdk.load.data.NumberValue
import io.airbyte.cdk.load.data.ObjectValue
import io.airbyte.cdk.load.data.StringValue
import io.airbyte.cdk.load.data.TimeValue
import io.airbyte.cdk.load.data.TimestampValue
import io.airbyte.cdk.load.data.UnknownValue
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord

class AirbyteValueToAvroRecord {
    fun convert(airbyteValue: AirbyteValue, schema: Schema): Any? {
        when (airbyteValue) {
            is ObjectValue -> {
                val recordSchema =
                    if (schema.type == Schema.Type.UNION) {
                        schema.types.find { it.type == Schema.Type.RECORD }
                            ?: throw IllegalArgumentException("Union must contain a record type")
                    } else {
                        schema
                    }
                val record = GenericData.Record(recordSchema)
                airbyteValue.values.forEach { (name, value) ->
                    recordSchema.getField(name)?.let { field ->
                        record.put(name, convert(value, field.schema()))
                    }
                }
                return record
            }
            is ArrayValue -> {
                val arraySchema =
                    if (schema.type == Schema.Type.UNION) {
                        schema.types.find { it.type == Schema.Type.ARRAY }
                            ?: throw IllegalArgumentException("Union must contain an array type")
                    } else {
                        schema
                    }
                val array = GenericData.Array<Any>(airbyteValue.values.size, arraySchema)
                airbyteValue.values.forEach { value ->
                    array.add(convert(value, arraySchema.elementType))
                }
                return array
            }
            is BooleanValue -> return airbyteValue.value
            is DateValue ->
                throw IllegalArgumentException("String-based date types are not supported")
            is IntegerValue -> return airbyteValue.value.toLong()
            is IntValue -> return airbyteValue.value
            is NullValue -> return null
            is NumberValue -> return airbyteValue.value.toDouble()
            is StringValue -> return airbyteValue.value
            is TimeValue ->
                throw IllegalArgumentException("String-based time types are not supported")
            is TimestampValue ->
                throw IllegalArgumentException("String-based timestamp types are not supported")
            is UnknownValue -> throw IllegalArgumentException("Unknown type is not supported")
        }
    }
}

fun ObjectValue.toAvroRecord(schema: Schema): GenericRecord {
    return AirbyteValueToAvroRecord().convert(this, schema) as GenericRecord
}
