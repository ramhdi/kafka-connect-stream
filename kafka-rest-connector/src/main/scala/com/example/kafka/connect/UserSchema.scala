package com.example.kafka.connect

import org.apache.kafka.connect.data.{Schema, SchemaBuilder}

object UserSchema {
  // Geo coordinates schema (nested within address)
  val geoSchema: Schema = SchemaBuilder
    .struct()
    .name("com.example.kafka.connect.Geo")
    .field("lat", Schema.STRING_SCHEMA)
    .field("lng", Schema.STRING_SCHEMA)
    .build()

  // Address schema (nested within user)
  val addressSchema: Schema = SchemaBuilder
    .struct()
    .name("com.example.kafka.connect.Address")
    .field("street", Schema.STRING_SCHEMA)
    .field("suite", Schema.STRING_SCHEMA)
    .field("city", Schema.STRING_SCHEMA)
    .field("zipcode", Schema.STRING_SCHEMA)
    .field("geo", geoSchema)
    .build()

  // Company schema (nested within user)
  val companySchema: Schema = SchemaBuilder
    .struct()
    .name("com.example.kafka.connect.Company")
    .field("name", Schema.STRING_SCHEMA)
    .field("catchPhrase", Schema.STRING_SCHEMA)
    .field("bs", Schema.STRING_SCHEMA)
    .build()

  // Main user schema
  val userSchema: Schema = SchemaBuilder
    .struct()
    .name("com.example.kafka.connect.User")
    .field("id", Schema.INT32_SCHEMA)
    .field("name", Schema.STRING_SCHEMA)
    .field("username", Schema.STRING_SCHEMA)
    .field("email", Schema.STRING_SCHEMA)
    .field("address", addressSchema)
    .field("phone", Schema.STRING_SCHEMA)
    .field("website", Schema.STRING_SCHEMA)
    .field("company", companySchema)
    .build()
}
