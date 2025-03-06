package com.example.kafka.streams

import org.apache.kafka.streams.{KafkaStreams, StreamsConfig}
import org.apache.kafka.streams.scala.{StreamsBuilder, Serdes}
import org.apache.kafka.streams.scala.ImplicitConversions._
import org.apache.kafka.streams.scala.serialization.Serdes._
import org.apache.kafka.streams.kstream.{Materialized, Produced}
import org.apache.kafka.streams.state.{KeyValueStore, StoreBuilder, Stores}
import org.apache.kafka.streams.processor.ProcessorContext
import org.apache.kafka.common.serialization.{Serde, Serdes => JSerdes}
import org.apache.kafka.clients.admin.{Admin, AdminClientConfig, NewTopic}
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}

import java.time.{ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter
import java.util.{Collections, Properties}
import java.util.concurrent.CountDownLatch

import spray.json._
import DefaultJsonProtocol._

object UserDataProcessor extends App {
  if (args.length < 3) {
    System.err.println(
      "Usage: UserDataProcessor <bootstrap-servers> <input-topic> <output-topic>"
    )
    System.exit(1)
  }

  val bootstrapServers = args(0)
  val inputTopic = args(1)
  val outputTopic = args(2)

  println(s"Connecting to Kafka at $bootstrapServers")
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
  props.put(
    StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG,
    JSerdes.String().getClass.getName
  )
  props.put(
    StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG,
    JSerdes.String().getClass.getName
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

  // Process the input stream
  val userStream = builder.stream[String, String](inputTopic)

  // Extract key for debugging
  userStream.foreach { (key, value) =>
    println(
      s"Received message - Key: $key, Value preview: ${value.take(100)}..."
    )
  }

  val enrichedStream = userStream
    .mapValues { jsonStr =>
      try {
        // Manual extraction of the payload from Kafka Connect JSON format
        val rawJson = jsonStr.parseJson.asJsObject

        // Extract the payload field which contains the actual data
        val payload = rawJson.fields("payload").asJsObject

        // Extract all the fields manually
        val id = payload.fields("id").convertTo[Int]
        val name = payload.fields("name").convertTo[String]
        val username = payload.fields("username").convertTo[String]
        val email = payload.fields("email").convertTo[String]
        val phone = payload.fields("phone").convertTo[String]
        val website = payload.fields("website").convertTo[String]

        // Extract address
        val addressObj = payload.fields("address").asJsObject
        val street = addressObj.fields("street").convertTo[String]
        val suite = addressObj.fields("suite").convertTo[String]
        val city = addressObj.fields("city").convertTo[String]
        val zipcode = addressObj.fields("zipcode").convertTo[String]

        // Extract geo
        val geoObj = addressObj.fields("geo").asJsObject
        val lat = geoObj.fields("lat").convertTo[String]
        val lng = geoObj.fields("lng").convertTo[String]
        val geo = Geo(lat, lng)

        val address = Address(street, suite, city, zipcode, geo)

        // Extract company
        val companyObj = payload.fields("company").asJsObject
        val companyName = companyObj.fields("name").convertTo[String]
        val catchPhrase = companyObj.fields("catchPhrase").convertTo[String]
        val bs = companyObj.fields("bs").convertTo[String]
        val company = Company(companyName, catchPhrase, bs)

        // Create User object
        User(id, name, username, email, address, phone, website, company)
      } catch {
        case e: Exception =>
          println(s"Failed to parse JSON: ${jsonStr.take(500)}...")
          println(s"Error: ${e.getMessage}")
          e.printStackTrace()
          throw e
      }
    }
    .transformValues(
      () => new UserEnricher(),
      "user-counts"
    )
    .mapValues { enrichedUser =>
      // Create a simple JSON without schema wrapper for output
      enrichedUser.toJson.compactPrint
    }

  // Send to output topic
  enrichedStream.to(outputTopic)

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

  // User enricher transformer with state
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
