name := "kafka-user-processor"
version := "0.1"
scalaVersion := "2.13.10"

val kafkaVersion = "3.4.0"

libraryDependencies ++= Seq(
  "org.apache.kafka" % "kafka-streams" % kafkaVersion,
  "org.apache.kafka" % "kafka-clients" % kafkaVersion,
  "org.apache.kafka" %% "kafka-streams-scala" % kafkaVersion,
  "io.spray" %%  "spray-json" % "1.3.6",
  "org.slf4j" % "slf4j-api" % "2.0.7",
  "org.slf4j" % "slf4j-simple" % "2.0.7"
)

// Assembly settings for creating a fat JAR
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case "reference.conf" => MergeStrategy.concat
  case x => MergeStrategy.first
}

// Add the assembly plugin for packaging
assembly / assemblyJarName := s"${name.value}-assembly-${version.value}.jar"
