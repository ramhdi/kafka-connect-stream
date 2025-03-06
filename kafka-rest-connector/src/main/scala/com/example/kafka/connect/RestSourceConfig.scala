package com.example.kafka.connect

import org.apache.kafka.common.config.{AbstractConfig, ConfigDef}
import org.apache.kafka.common.config.ConfigDef.{Importance, Type}
import java.util

class RestSourceConfig(props: util.Map[String, String])
    extends AbstractConfig(RestSourceConfig.configDef, props) {
  // Configuration properties extracted from the props map
  val restEndpoint: String = getString(RestSourceConfig.REST_ENDPOINT_CONFIG)
  val pollInterval: Int = getInt(RestSourceConfig.POLL_INTERVAL_CONFIG)
  val topicName: String = getString(RestSourceConfig.TOPIC_CONFIG)
  val apiKey: String = getString(RestSourceConfig.API_KEY_CONFIG)
}

object RestSourceConfig {
  // Configuration keys
  val REST_ENDPOINT_CONFIG = "rest.endpoint"
  val POLL_INTERVAL_CONFIG = "poll.interval.ms"
  val TOPIC_CONFIG = "topic"
  val API_KEY_CONFIG = "api.key"

  // Define the configuration with default values and documentation
  val configDef: ConfigDef = new ConfigDef()
    .define(
      REST_ENDPOINT_CONFIG,
      Type.STRING,
      "https://jsonplaceholder.typicode.com/users",
      Importance.HIGH,
      "REST API endpoint URL"
    )
    .define(
      POLL_INTERVAL_CONFIG,
      Type.INT,
      60000,
      Importance.MEDIUM,
      "Poll interval in milliseconds"
    )
    .define(
      TOPIC_CONFIG,
      Type.STRING,
      "rest-data",
      Importance.HIGH,
      "Target Kafka topic"
    )
    .define(
      API_KEY_CONFIG,
      Type.STRING,
      "",
      Importance.LOW,
      "API key for REST endpoint authentication (if needed)"
    )
}
