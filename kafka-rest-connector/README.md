# Kafka REST Source Connector

A simple Kafka Connect source connector implemented in Scala that fetches data from REST APIs and forwards it to Kafka topics. This connector serves as a boilerplate for future projects requiring REST API integration with Kafka.

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Prerequisites](#prerequisites)
- [Project Structure](#project-structure)
- [Building the Connector](#building-the-connector)
- [Deployment](#deployment)
- [Configuration](#configuration)
- [Monitoring](#monitoring)
- [Architecture](#architecture)
- [Extending the Connector](#extending-the-connector)
- [Troubleshooting](#troubleshooting)

## Overview

This connector polls a REST API endpoint at configurable intervals, transforms the response into Kafka Connect records, and publishes these records to a specified Kafka topic. It's designed to be simple yet flexible, serving as a foundation for more complex REST-to-Kafka integrations.

## Features

- Configurable REST endpoint URL
- Customizable polling interval
- JSON response parsing
- Support for API key authentication
- Docker-based deployment
- Management UI integration

## Prerequisites

- Java 11 or later
- SBT (Scala Build Tool)
- Docker and Docker Compose
- Access to a REST API endpoint

## Project Structure

```
kafka-rest-connector/
  ├── build.sbt                      # Scala build configuration
  ├── project/
  │   └── plugins.sbt                # SBT plugins
  ├── src/
  │   ├── main/
  │   │   ├── scala/com/example/kafka/connect/
  │   │   │   ├── RestSourceConnector.scala
  │   │   │   ├── RestSourceTask.scala
  │   │   │   └── RestSourceConfig.scala
  │   │   └── resources/
  │   │       └── log4j.properties
  ├── docker/
  │   ├── docker-compose.yml         # Docker configuration
  │   └── rest-source-config.json    # Connector configuration
```

## Building the Connector

1. Clone this repository:

```bash
git clone https://github.com/yourusername/kafka-rest-connector.git
cd kafka-rest-connector
```

2. Build the connector using SBT:

```bash
sbt clean assembly
```

This creates a fat JAR at `target/scala-2.13/kafka-rest-connector-assembly-0.1.jar` with all dependencies included.

## Deployment

1. Create a directory for the connector JAR in your Docker setup:

```bash
mkdir -p docker/connectors
```

2. Copy the built JAR file to the connectors directory:

```bash
cp target/scala-2.13/kafka-rest-connector-assembly-0.1.jar docker/connectors/
```

3. Start the Kafka ecosystem using Docker Compose:

```bash
cd docker
docker-compose up -d
```

4. Wait approximately 30-60 seconds for all services to initialize properly.

## Configuration

Create a connector configuration file or use the provided example. Here's a sample configuration file for connecting to JSONPlaceholder:

```json
{
  "name": "rest-source-connector",
  "config": {
    "connector.class": "com.example.kafka.connect.RestSourceConnector",
    "tasks.max": "1",
    "rest.endpoint": "https://jsonplaceholder.typicode.com/users",
    "poll.interval.ms": "10000",
    "topic": "rest-data",
    "api.key": ""
  }
}
```

### Configuration Properties

| Property | Description | Default | Importance |
|----------|-------------|---------|------------|
| `rest.endpoint` | REST API endpoint URL | https://jsonplaceholder.typicode.com/users | High |
| `poll.interval.ms` | Polling interval in milliseconds | 60000 | Medium |
| `topic` | Target Kafka topic | rest-data | High |
| `api.key` | API key for authentication (if needed) | "" | Low |

### Deploying the Connector

#### Using the Connect REST API

```bash
curl -X POST -H "Content-Type: application/json" \
  --data @rest-source-config.json \
  http://localhost:8083/connectors
```

#### Using Kafka Connect UI

1. Access the Connect UI at http://localhost:8000
2. Click "New" to create a new connector
3. Paste the connector configuration JSON
4. Click "Create"

## Monitoring

### Checking Connector Status

```bash
curl -X GET http://localhost:8083/connectors/rest-source-connector/status
```

### Viewing Messages in Kafka Topic

#### Using Command Line

```bash
docker exec -it broker kafka-console-consumer \
  --bootstrap-server broker:9092 \
  --topic rest-data \
  --from-beginning
```

#### Using AKHQ (GUI)

1. Access AKHQ at http://localhost:8008
2. Navigate to "docker-kafka-server" → "Topics" → "rest-data"
3. Click on the "Data" tab to see messages

## Architecture

The connector consists of three main components:

1. **RestSourceConnector**: Manages the connector lifecycle and creates tasks
2. **RestSourceTask**: Polls the REST endpoint and converts responses to Kafka records
3. **RestSourceConfig**: Defines and validates configuration properties

### Data Flow

```
REST API → RestSourceTask → Kafka Connect framework → Kafka Topic
```

## Extending the Connector

### Custom Response Handling

To implement custom response parsing, modify the `poll()` method in `RestSourceTask.scala`. The current implementation supports JSON arrays and objects, but you can extend it for other formats.

### Adding Authentication Methods

To support additional authentication methods beyond API keys, update the `RestSourceConfig.scala` to include new configuration properties and modify the HTTP request creation in `RestSourceTask.scala`.

### Supporting Different Data Formats

Currently, the connector handles JSON. To support XML, CSV, or other formats:

1. Add appropriate libraries to `build.sbt`
2. Implement format-specific parsing in the task's `poll()` method
3. Add configuration options for format selection

## Troubleshooting

### Connector Not Starting

Check the logs for error messages:

```bash
docker logs connect
```

Common issues include:
- JAR file not found in the plugins directory
- Configuration errors
- Network connectivity issues

### No Data Being Produced

Verify that:
- The REST endpoint is accessible (test with curl or a browser)
- The polling interval is appropriate (not too long)
- The connector status is RUNNING
- Proper error handling is in place for the response format

### Connection Errors

If you see HTTP connection errors:
- Check firewall settings
- Verify the REST endpoint URL is correct
- Ensure proper API key or authentication settings
- Check proxy configuration if applicable

---

This project is intended as a starting point. Feel free to modify and extend it to meet your specific requirements.