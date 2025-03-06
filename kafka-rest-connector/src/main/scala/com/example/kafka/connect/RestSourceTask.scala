package com.example.kafka.connect

import org.apache.kafka.connect.source.{SourceRecord, SourceTask}
import org.apache.kafka.connect.data.{Schema, SchemaBuilder, Struct}
import scala.jdk.CollectionConverters._
import java.util
import java.util.concurrent.ConcurrentLinkedQueue
import scala.util.{Failure, Success, Try}
import scala.concurrent.{ExecutionContext, Future}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.SystemMaterializer
import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._

class RestSourceTask extends SourceTask {
  private var config: RestSourceConfig = _
  private var actorSystem: ActorSystem = _
  private implicit var executionContext: ExecutionContext = _
  private implicit var materializer: Materializer = _
  private var sourcePartition: util.Map[String, String] = _
  private var lastOffset: util.Map[String, Object] = _
  private var lastPollTime: Long = 0L

  // Queue to store records between poll cycles
  private val recordQueue = new ConcurrentLinkedQueue[SourceRecord]()

  // Flag to track if a request is in progress
  private var requestInProgress = false

  override def version(): String = getClass.getPackage.getImplementationVersion

  // Initialize the task with configuration
  override def start(props: util.Map[String, String]): Unit = {
    config = new RestSourceConfig(props)

    // Initialize Akka HTTP components for making REST calls
    actorSystem = ActorSystem("RestSourceSystem")
    executionContext = actorSystem.dispatcher
    materializer = SystemMaterializer(actorSystem).materializer

    // Initialize state tracking
    sourcePartition = Map("endpoint" -> config.restEndpoint).asJava

    // Create Map with explicit Object type
    val offsetMap = new util.HashMap[String, Object]()
    offsetMap.put("timestamp", Long.box(System.currentTimeMillis()))
    lastOffset = offsetMap

    lastPollTime = 0 // Ensure first poll happens immediately
  }

  // Create a structured record from JSON
  private def createUserRecord(
      json: JsValue,
      sourcePartition: util.Map[String, String],
      offset: util.Map[String, Object],
      topic: String
  ): SourceRecord = {
    try {
      val userObj = json.asJsObject

      // Build the user struct with all nested elements
      val userStruct = new Struct(UserSchema.userSchema)
        .put("id", userObj.fields("id").convertTo[Int])
        .put("name", userObj.fields("name").convertTo[String])
        .put("username", userObj.fields("username").convertTo[String])
        .put("email", userObj.fields("email").convertTo[String])
        .put("phone", userObj.fields("phone").convertTo[String])
        .put("website", userObj.fields("website").convertTo[String])

      // Create and populate address struct
      val addressObj = userObj.fields("address").asJsObject
      val addressStruct = new Struct(UserSchema.addressSchema)
        .put("street", addressObj.fields("street").convertTo[String])
        .put("suite", addressObj.fields("suite").convertTo[String])
        .put("city", addressObj.fields("city").convertTo[String])
        .put("zipcode", addressObj.fields("zipcode").convertTo[String])

      // Create and populate geo struct
      val geoObj = addressObj.fields("geo").asJsObject
      val geoStruct = new Struct(UserSchema.geoSchema)
        .put("lat", geoObj.fields("lat").convertTo[String])
        .put("lng", geoObj.fields("lng").convertTo[String])

      // Add geo to address
      addressStruct.put("geo", geoStruct)

      // Add address to user
      userStruct.put("address", addressStruct)

      // Create and populate company struct
      val companyObj = userObj.fields("company").asJsObject
      val companyStruct = new Struct(UserSchema.companySchema)
        .put("name", companyObj.fields("name").convertTo[String])
        .put("catchPhrase", companyObj.fields("catchPhrase").convertTo[String])
        .put("bs", companyObj.fields("bs").convertTo[String])

      // Add company to user
      userStruct.put("company", companyStruct)

      // Create the source record - using ID as the key for partitioning
      new SourceRecord(
        sourcePartition,
        offset,
        topic,
        null, // Partition determined by Kafka
        Schema.INT32_SCHEMA, // Key schema
        userObj.fields("id").convertTo[Int], // Key value is the user ID
        UserSchema.userSchema, // Value schema
        userStruct // Value is the full user struct
      )
    } catch {
      case ex: Exception =>
        // On error, fall back to raw JSON string
        System.err.println(s"Error creating user record: ${ex.getMessage}")
        new SourceRecord(
          sourcePartition,
          offset,
          topic,
          null,
          Schema.STRING_SCHEMA,
          json.toString(),
          Schema.STRING_SCHEMA,
          json.toString()
        )
    }
  }

  // Make the asynchronous HTTP request and enqueue responses as they arrive
  private def fetchData(): Unit = {
    if (requestInProgress) {
      return
    }

    requestInProgress = true

    // Create HTTP request
    val request = HttpRequest(
      method = HttpMethods.GET,
      uri = config.restEndpoint,
      headers =
        if (config.apiKey.nonEmpty)
          List(headers.RawHeader("X-API-Key", config.apiKey))
        else
          List.empty
    )

    // Execute HTTP request asynchronously
    val responseFuture = Http()(actorSystem).singleRequest(request)

    responseFuture
      .flatMap { response =>
        if (response.status.isSuccess()) {
          // Parse response body asynchronously
          Unmarshal(response.entity).to[String].map { body =>
            try {
              // Parse JSON response
              val jsonData = body.parseJson

              // Create offset map
              val currentOffsetMap = new util.HashMap[String, Object]()
              currentOffsetMap
                .put("timestamp", Long.box(System.currentTimeMillis()))
              val currentOffset = currentOffsetMap

              // Create records based on JSON structure
              jsonData match {
                case JsArray(elements) =>
                  // Process JSON array elements individually
                  elements.foreach { element =>
                    val record = createUserRecord(
                      element,
                      sourcePartition,
                      currentOffset,
                      config.topicName
                    )
                    recordQueue.add(record)
                  }

                case jsObj: JsObject =>
                  // Process single JSON object
                  val record = createUserRecord(
                    jsObj,
                    sourcePartition,
                    currentOffset,
                    config.topicName
                  )
                  recordQueue.add(record)

                case _ =>
                  // Handle unexpected structure with raw JSON
                  System.err.println("Unexpected JSON format, unable to parse")
              }

              lastOffset = currentOffset
            } catch {
              case ex: Exception =>
                System.err
                  .println(s"Error processing response: ${ex.getMessage}")
            }
          }
        } else {
          // Handle HTTP error
          Future.successful(())
        }
      }
      .onComplete { _ =>
        // Mark request as complete regardless of success/failure
        requestInProgress = false
      }
  }

  // Non-blocking poll method that returns available records
  override def poll(): util.List[SourceRecord] = {
    // Check if it's time to poll again
    val now = System.currentTimeMillis()
    if (now - lastPollTime >= config.pollInterval) {
      lastPollTime = now
      fetchData() // Trigger async fetch
    }

    // Return any available records without waiting
    if (recordQueue.isEmpty) {
      return util.Collections.emptyList[SourceRecord]()
    }

    // Get up to 100 records from the queue
    val records = new util.ArrayList[SourceRecord]()
    var record = recordQueue.poll()
    var count = 0
    val batchSize = 100

    while (record != null && count < batchSize) {
      records.add(record)
      record = recordQueue.poll()
      count += 1
    }

    records
  }

  // Clean up resources when task is stopped
  override def stop(): Unit = {
    if (actorSystem != null) {
      actorSystem.terminate()
    }
  }
}
