name := "kafka-user-processor"
version := "0.1"
scalaVersion := "2.13.10"

// Align all versions
val kafkaVersion = "3.3.2" // Match with Confluent 7.3.2
val confluentVersion = "7.3.2" // Match Docker image version

libraryDependencies ++= Seq(
  "org.apache.kafka" % "kafka-streams" % kafkaVersion,
  "org.apache.kafka" % "kafka-clients" % kafkaVersion,
  "org.apache.kafka" %% "kafka-streams-scala" % kafkaVersion,
  "io.spray" %% "spray-json" % "1.3.6",
  "org.slf4j" % "slf4j-api" % "2.0.7",
  "org.slf4j" % "slf4j-simple" % "2.0.7",

  // Add Avro and Schema Registry dependencies
  "io.confluent" % "kafka-avro-serializer" % confluentVersion,
  "io.confluent" % "kafka-streams-avro-serde" % confluentVersion,
  "org.apache.avro" % "avro" % "1.11.1"
)

// Force specific versions to avoid conflicts
dependencyOverrides ++= Seq(
  "org.apache.kafka" % "kafka-clients" % kafkaVersion,
  "org.apache.kafka" % "kafka-streams" % kafkaVersion
)

// Add Confluent Maven repository
resolvers += "Confluent Maven Repository" at "https://packages.confluent.io/maven/"

// Assembly settings
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", "services", xs @ _*) => MergeStrategy.concat
  case PathList("META-INF", xs @ _*)             => MergeStrategy.discard
  case "reference.conf"                          => MergeStrategy.concat
  case x                                         => MergeStrategy.first
}

// Add the assembly plugin for packaging
assembly / assemblyJarName := s"${name.value}-assembly-${version.value}.jar"
