package com.example.kafka.connect

import org.apache.kafka.common.config.ConfigDef
import org.apache.kafka.connect.connector.Task
import org.apache.kafka.connect.source.SourceConnector
import scala.jdk.CollectionConverters._
import java.util

class RestSourceConnector extends SourceConnector {
  private var configProps: util.Map[String, String] = _

  // Return the version of the connector
  override def version(): String = getClass.getPackage.getImplementationVersion

  // Initialize the connector with configuration
  override def start(props: util.Map[String, String]): Unit = {
    configProps = props
    // Log about connector starting could be added here
  }

  // Return the Task class that will be instantiated for each task
  override def taskClass(): Class[_ <: Task] = classOf[RestSourceTask]

  // Generate task configurations
  override def taskConfigs(
      maxTasks: Int
  ): util.List[util.Map[String, String]] = {
    // For simplicity, all tasks have the same config
    val configs = (0 until maxTasks).map(_ => configProps).toList
    configs.asJava
  }

  // Clean up resources when connector is stopped
  override def stop(): Unit = {
    // Cleanup code would go here
  }

  // Return the configuration definition
  override def config(): ConfigDef = RestSourceConfig.configDef
}
