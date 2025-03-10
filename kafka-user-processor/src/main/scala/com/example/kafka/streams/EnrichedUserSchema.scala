package com.example.kafka.streams

import org.apache.avro.{Schema, SchemaBuilder}

object EnrichedUserSchema {
  // Create Avro schema for each of our data structures
  // Geo schema
  val geoSchema = SchemaBuilder
    .record("Geo")
    .namespace("com.example.models")
    .fields()
    .requiredString("lat")
    .requiredString("lng")
    .endRecord()

  // Address schema
  val addressSchema = SchemaBuilder
    .record("Address")
    .namespace("com.example.models")
    .fields()
    .requiredString("street")
    .requiredString("suite")
    .requiredString("city")
    .requiredString("zipcode")
    .name("geo")
    .`type`(geoSchema)
    .noDefault()
    .endRecord()

  // Company schema
  val companySchema = SchemaBuilder
    .record("Company")
    .namespace("com.example.models")
    .fields()
    .requiredString("name")
    .requiredString("catchPhrase")
    .requiredString("bs")
    .endRecord()

  // EnrichedUser schema - this is what we'll register for the output topic
  val enrichedUserSchema = SchemaBuilder
    .record("EnrichedUser")
    .namespace("com.example.models")
    .fields()
    .requiredInt("id")
    .requiredString("name")
    .requiredString("username")
    .requiredString("email")
    .name("address")
    .`type`(addressSchema)
    .noDefault()
    .requiredString("phone")
    .requiredString("website")
    .name("company")
    .`type`(companySchema)
    .noDefault()
    .requiredString("timestamp")
    .requiredLong("count")
    .endRecord()

  val keySchema = SchemaBuilder
    .record("UserId")
    .namespace("com.example.models")
    .fields()
    .requiredInt("id")
    .endRecord()
}
