name := "kafka-rest-connector"
version := "0.1"
scalaVersion := "2.13.10"

libraryDependencies ++= Seq(
  "org.apache.kafka" % "connect-api" % "3.4.0",
  "org.apache.kafka" % "connect-json" % "3.4.0",
  "com.typesafe.akka" %% "akka-http" % "10.5.0",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.5.0",
  "com.typesafe.akka" %% "akka-stream" % "2.7.0",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  "ch.qos.logback" % "logback-classic" % "1.4.7"
)

// Assembly settings for creating a fat JAR
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case "reference.conf" => MergeStrategy.concat
  case x => MergeStrategy.first
}