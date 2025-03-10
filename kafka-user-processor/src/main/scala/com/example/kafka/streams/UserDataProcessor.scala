package com.example.kafka.streams

import org.apache.kafka.streams.{KafkaStreams, StreamsConfig}
import org.apache.kafka.streams.scala.{
  StreamsBuilder,
  Serdes,
  ImplicitConversions,
  kstream
}
import org.apache.kafka.streams.scala.ImplicitConversions._
import org.apache.kafka.streams.scala.serialization.Serdes._
import org.apache.kafka.streams.kstream.{Consumed, Produced}
import org.apache.kafka.streams.state.{KeyValueStore, StoreBuilder, Stores}
import org.apache.kafka.streams.processor.ProcessorContext
import org.apache.kafka.common.serialization.{Serde, Serdes => JSerdes}
import org.apache.kafka.clients.admin.{Admin, AdminClientConfig, NewTopic}
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}

import java.time.{ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter
import java.util.{Collections, Properties}
import java.util.concurrent.CountDownLatch

// Import for Java conversions
import scala.jdk.CollectionConverters._

import spray.json._
import DefaultJsonProtocol._

// Add Avro imports
import io.confluent.kafka.streams.serdes.avro.GenericAvroSerde
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import org.apache.avro.generic.{GenericData, GenericRecord}

import com.example.kafka.streams.EnrichedUserSchema.{
  geoSchema,
  addressSchema,
  companySchema,
  enrichedUserSchema
}
import com.example.kafka.streams.EnrichedUserSchema.keySchema

object UserDataProcessor extends App {
  if (args.length < 4) {
    System.err.println(
      "Usage: UserDataProcessor <bootstrap-servers> <schema-registry-url> <input-topic> <output-topic>"
    )
    System.exit(1)
  }

  val bootstrapServers = args(0)
  // Add http:// prefix to the schema registry URL if it doesn't already have it
  val schemaRegistryUrl =
    if (args(1).startsWith("http://") || args(1).startsWith("https://")) {
      args(1)
    } else {
      s"http://${args(1)}"
    }
  val inputTopic = args(2)
  val outputTopic = args(3)

  println(s"Connecting to Kafka at $bootstrapServers")
  println(s"Schema Registry URL: $schemaRegistryUrl")
  println(s"Reading from topic: $inputTopic")
  println(s"Writing to topic: $outputTopic")

  // User data models
  case class Geo(lat: String, lng: String)
  case class Address(
      street: String,
      suite: String,
      city: String,
      zipcode: String,
      geo: Geo
  )
  case class Company(name: String, catchPhrase: String, bs: String)

  case class User(
      id: Int,
      name: String,
      username: String,
      email: String,
      address: Address,
      phone: String,
      website: String,
      company: Company
  )

  case class EnrichedUser(
      id: Int,
      name: String,
      username: String,
      email: String,
      address: Address,
      phone: String,
      website: String,
      company: Company,
      timestamp: String,
      count: Long
  )

  // JSON formats for our data models
  implicit val geoFormat: RootJsonFormat[Geo] = jsonFormat2(Geo)
  implicit val addressFormat: RootJsonFormat[Address] = jsonFormat5(Address)
  implicit val companyFormat: RootJsonFormat[Company] = jsonFormat3(Company)
  implicit val userFormat: RootJsonFormat[User] = jsonFormat8(User)
  implicit val enrichedUserFormat: RootJsonFormat[EnrichedUser] = jsonFormat10(
    EnrichedUser
  )

  // Kafka Streams configuration
  val props = new Properties()
  props.put(StreamsConfig.APPLICATION_ID_CONFIG, "user-data-processor")
  props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)

  // Configure for Avro serialization with proper URL
  props.put(
    AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG,
    schemaRegistryUrl
  )

  props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
  props.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 0)
  props.put(
    StreamsConfig.PROCESSING_GUARANTEE_CONFIG,
    StreamsConfig.EXACTLY_ONCE_V2
  )
  props.put(StreamsConfig.STATE_DIR_CONFIG, "/tmp/kafka-streams-user-processor")

  // Create the output topic if it doesn't exist
  createTopicIfNotExists(bootstrapServers, outputTopic)

  // Create the stream builder
  val builder = new StreamsBuilder()

  // Create a state store for user counts
  val storeBuilder: StoreBuilder[KeyValueStore[String, java.lang.Long]] =
    Stores.keyValueStoreBuilder(
      Stores.persistentKeyValueStore("user-counts"),
      JSerdes.String(),
      JSerdes.Long()
    )
  builder.addStateStore(storeBuilder)

  // Configure Avro SerDes with Schema Registry
  val schemaRegistryConfig = Map[String, String](
    AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG -> schemaRegistryUrl
  ).asJava

  // Input Avro SerDe
  val inputAvroSerde = new GenericAvroSerde()
  inputAvroSerde.configure(
    schemaRegistryConfig,
    false
  ) // false = for values, not keys

  val keyAvroSerde = new GenericAvroSerde()
  keyAvroSerde.configure(
    schemaRegistryConfig,
    true // true = for keys
  )

  // Output Avro SerDe - will be used for our enriched data
  val outputAvroSerde = new GenericAvroSerde()
  outputAvroSerde.configure(
    schemaRegistryConfig,
    false
  ) // false = for values, not keys

  // Process the input stream with Avro deserialization
  val userStream = builder.stream[String, GenericRecord](inputTopic)(
    Consumed.`with`(JSerdes.String(), inputAvroSerde)
  )

  // Extract key for debugging
  userStream.foreach { (key, value) =>
    println(
      s"Received message - Key: $key, Value preview: ${value.toString.take(100)}..."
    )
  }

  userStream
    .mapValues { avroRecord =>
      try {
        // Extract fields from Avro record
        val id = avroRecord.get("id").toString.toInt
        val name = avroRecord.get("name").toString
        val username = avroRecord.get("username").toString
        val email = avroRecord.get("email").toString
        val phone = avroRecord.get("phone").toString
        val website = avroRecord.get("website").toString

        // Extract address from Avro record
        val addressRecord =
          avroRecord.get("address").asInstanceOf[GenericRecord]
        val street = addressRecord.get("street").toString
        val suite = addressRecord.get("suite").toString
        val city = addressRecord.get("city").toString
        val zipcode = addressRecord.get("zipcode").toString

        // Extract geo from Avro record
        val geoRecord = addressRecord.get("geo").asInstanceOf[GenericRecord]
        val lat = geoRecord.get("lat").toString
        val lng = geoRecord.get("lng").toString
        val geo = Geo(lat, lng)

        val address = Address(street, suite, city, zipcode, geo)

        // Extract company from Avro record
        val companyRecord =
          avroRecord.get("company").asInstanceOf[GenericRecord]
        val companyName = companyRecord.get("name").toString
        val catchPhrase = companyRecord.get("catchPhrase").toString
        val bs = companyRecord.get("bs").toString
        val company = Company(companyName, catchPhrase, bs)

        // Create User object
        User(id, name, username, email, address, phone, website, company)
      } catch {
        case e: Exception =>
          println(
            s"Failed to parse Avro record: ${avroRecord.toString.take(500)}..."
          )
          println(s"Error: ${e.getMessage}")
          e.printStackTrace()
          throw e
      }
    }
    .transformValues(
      () => new UserEnricher(),
      "user-counts" // Name of the state store to connect
    )
    .mapValues { enrichedUser =>
      // Convert EnrichedUser to Avro GenericRecord instead of JSON string
      val record: GenericRecord = new GenericData.Record(enrichedUserSchema)
      record.put("id", enrichedUser.id)
      record.put("name", enrichedUser.name)
      record.put("username", enrichedUser.username)
      record.put("email", enrichedUser.email)

      // Create address record
      val addressRecord: GenericRecord = new GenericData.Record(addressSchema)
      addressRecord.put("street", enrichedUser.address.street)
      addressRecord.put("suite", enrichedUser.address.suite)
      addressRecord.put("city", enrichedUser.address.city)
      addressRecord.put("zipcode", enrichedUser.address.zipcode)

      // Create geo record
      val geoRecord: GenericRecord = new GenericData.Record(geoSchema)
      geoRecord.put("lat", enrichedUser.address.geo.lat)
      geoRecord.put("lng", enrichedUser.address.geo.lng)

      addressRecord.put("geo", geoRecord)
      record.put("address", addressRecord)

      // Add remaining flat fields
      record.put("phone", enrichedUser.phone)
      record.put("website", enrichedUser.website)

      // Create company record
      val companyRecord: GenericRecord = new GenericData.Record(companySchema)
      companyRecord.put("name", enrichedUser.company.name)
      companyRecord.put("catchPhrase", enrichedUser.company.catchPhrase)
      companyRecord.put("bs", enrichedUser.company.bs)

      record.put("company", companyRecord)
      record.put("timestamp", enrichedUser.timestamp)
      record.put("count", enrichedUser.count)

      record
    }
    .selectKey { (_, enrichedUser) =>
      val keyRecord = new GenericData.Record(keySchema)
      keyRecord.put("id", enrichedUser.get("id"))
      keyRecord
    }
    .to(outputTopic)(
      Produced.`with`(
        keyAvroSerde.asInstanceOf[Serde[GenericData.Record]],
        outputAvroSerde.asInstanceOf[Serde[GenericRecord]]
      )
    )

  // Build the topology
  val topology = builder.build()
  println(topology.describe())

  // Create and start the Kafka Streams application
  val streams = new KafkaStreams(topology, props)

  // Reset state stores
  streams.cleanUp()

  // Handle shutdown gracefully
  val latch = new CountDownLatch(1)
  Runtime.getRuntime.addShutdownHook(new Thread {
    override def run(): Unit = {
      streams.close()
      latch.countDown()
    }
  })

  // Start the stream processing
  try {
    streams.start()
    latch.await()
  } catch {
    case e: Exception =>
      System.err.println(s"Error processing streams: ${e.getMessage}")
      System.exit(1)
  }

  // User enricher with ValueTransformerWithKey interface
  class UserEnricher
      extends org.apache.kafka.streams.kstream.ValueTransformerWithKey[
        String,
        User,
        EnrichedUser
      ] {

    private var stateStore: KeyValueStore[String, java.lang.Long] = _
    private var context: ProcessorContext = _

    override def init(context: ProcessorContext): Unit = {
      this.context = context
      this.stateStore = context
        .getStateStore("user-counts")
        .asInstanceOf[KeyValueStore[String, java.lang.Long]]
    }

    override def transform(key: String, value: User): EnrichedUser = {
      // Extract user ID from the message
      val userId = value.id.toString
      val currentCount =
        Option(stateStore.get(userId)).map(_.longValue()).getOrElse(0L)

      // Increment count
      val newCount = currentCount + 1
      stateStore.put(userId, newCount)

      println(s"Processing user ${value.id} (${value.name}), count: $newCount")

      // Create enriched user with timestamp and count
      EnrichedUser(
        id = value.id,
        name = value.name,
        username = value.username,
        email = value.email,
        address = value.address,
        phone = value.phone,
        website = value.website,
        company = value.company,
        timestamp = ZonedDateTime
          .now(ZoneId.of("UTC"))
          .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        count = newCount
      )
    }

    override def close(): Unit = {}
  }

  // Create the output topic if it doesn't exist
  def createTopicIfNotExists(
      bootstrapServers: String,
      topicName: String,
      partitions: Int = 3,
      replicationFactor: Short = 1
  ): Unit = {
    val config = new java.util.Properties()
    config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)

    val adminClient = Admin.create(config)
    try {
      val existingTopics = adminClient.listTopics().names().get()
      if (!existingTopics.contains(topicName)) {
        println(
          s"Creating topic $topicName with $partitions partitions and replication factor $replicationFactor"
        )
        val newTopic = new NewTopic(topicName, partitions, replicationFactor)
        adminClient
          .createTopics(java.util.Collections.singleton(newTopic))
          .all()
          .get()
        println(s"Topic $topicName created successfully")
      } else {
        println(s"Topic $topicName already exists")
      }
    } catch {
      case e: Exception =>
        System.err.println(s"Error creating topic $topicName: ${e.getMessage}")
    } finally {
      adminClient.close()
    }
  }
}
