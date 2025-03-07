# Kafka Streaming Data Processing Pipeline

A complete example of a data processing pipeline implemented with Apache Kafka, Schema Registry, and Kafka Streams. This project demonstrates how to fetch data from a REST API, process it through Kafka Streams, and make it available for downstream consumers like databases through Kafka Connect.

## Architecture Overview

This project implements a complete streaming data processing pipeline with the following components:

1. **REST Source Connector**: A custom Kafka Connect connector that fetches user data from REST APIs
2. **Schema Registry**: Manages and enforces Avro schemas for all data in the pipeline
3. **Kafka Streams Processor**: A stateful processor that enriches user data with additional metadata
4. **Kafka Connect Framework**: For integrating with external systems (like databases)
5. **Management UIs**: For monitoring and administering the pipeline

### Data Flow

```
REST API → REST Connector → Kafka (rest-data) → Kafka Streams → Kafka (processed-users) → Downstream Systems
                                      ↑                                        ↑
                                      ↓                                        ↓
                                Schema Registry ←----- Schema Evolution ----→ Schema Registry
```

## Features

- **Schema Registry Integration**: All data is serialized using Avro with centralized schema management
- **Stateful Processing**: Tracks and counts user appearances in the stream
- **Schema Evolution**: Structured for seamless evolution of data models
- **Resource-Optimized Configuration**: Runs efficiently on development machines
- **Full Monitoring**: Integrated UIs for observing the pipeline
- **Docker-based Deployment**: Simple setup and operation

## Prerequisites

- Docker and Docker Compose
- JDK 11 or later
- Scala Build Tool (SBT)
- ~4GB available RAM for running the complete stack

## Project Structure

```
kafka-pipeline/
├── docker/                          # Docker configuration files
│   ├── docker-compose.yml           # Main Docker Compose configuration
│   ├── rest-source-config.json      # REST connector configuration
│   └── connectors/                  # Directory for connector JARs
├── kafka-rest-connector/            # Source code for the REST connector
├── kafka-user-processor/            # Source code for the Kafka Streams processor
│   ├── src/main/scala/...           # Scala source files
│   └── build.sbt                    # SBT build configuration
└── README.md                        # This file
```

## Building the Components

### 1. Building the REST Connector

```bash
cd kafka-rest-connector
sbt clean assembly
mkdir -p ../docker/connectors
cp target/scala-2.13/kafka-rest-connector-assembly-0.1.jar ../docker/connectors/
```

### 2. Building the User Processor

```bash
cd kafka-user-processor
sbt clean assembly
mkdir -p ../docker/streams
cp target/scala-2.13/kafka-user-processor-assembly-0.1.jar ../docker/streams/
```

## Running the Pipeline

1. Start the Docker environment:
   ```bash
   cd docker
   docker-compose up -d
   ```

2. Wait about 1 minute for all services to initialize.

3. Deploy the REST connector:
   ```bash
   curl -X POST -H "Content-Type: application/json" \
     --data @rest-source-config.json \
     http://localhost:8083/connectors
   ```

4. Monitor data flow:
   - REST Connector UI: http://localhost:8000
   - AKHQ (Kafka management): http://localhost:8008
   - Schema Registry: http://localhost:8081

## Working with Schema Registry and Avro

### Schema Evolution

The pipeline uses Avro schemas with Schema Registry to ensure data compatibility. When making changes to data models:

1. For the connector, update the `UserSchema.scala` class
2. For the processor, update the Avro schemas in `UserDataProcessor.scala`

Schema Registry ensures backward compatibility by default, meaning consumers can read both old and new message formats.

### Adding a JDBC Sink

To send processed data to a database:

1. Add the JDBC connector to Kafka Connect:
   ```bash
   confluent-hub install confluentinc/kafka-connect-jdbc:latest
   ```

2. Deploy a JDBC sink connector configuration:
   ```json
   {
     "name": "jdbc-sink",
     "config": {
       "connector.class": "io.confluent.connect.jdbc.JdbcSinkConnector",
       "tasks.max": "1",
       "topics": "processed-users",
       "connection.url": "jdbc:postgresql://postgres:5432/mydb",
       "connection.user": "postgres",
       "connection.password": "postgres",
       "auto.create": true,
       "auto.evolve": true,
       "insert.mode": "upsert",
       "pk.mode": "record_key",
       "pk.fields": "id"
     }
   }
   ```

## Troubleshooting

### Schema Registry Issues

If you see errors related to schema registration:

1. Check Schema Registry is running: `curl http://localhost:8081/subjects`
2. Ensure connector and processor use compatible schemas
3. Examine Schema Registry logs: `docker logs schema-registry`

### Deserialization Errors

If the processor fails with deserialization errors:

1. Verify the key.converter and value.converter settings in connector configuration
2. Check that the processor is using the correct key/value Serde classes
3. Restart the user-processor to rebuild its state: `docker-compose restart user-processor`

### State Store Persistence

The processor maintains state in `/tmp/kafka-streams-user-processor` within the container, which is mounted to the host's `./streams/data` directory. If you need to reset the state:

```bash
docker-compose stop user-processor
rm -rf ./streams/data/*
docker-compose start user-processor
```

## Advanced Topics

### Custom Serialization

The pipeline uses Avro serialization with Schema Registry, but you can also use:

- JSON serialization (remove Schema Registry configuration)
- Protocol Buffers (requires additional configuration)
- Custom serializers/deserializers

### Scaling

For production environments:

1. Increase replication factors in docker-compose.yml
2. Adjust JVM heap settings based on workload
3. Add more partitions to topics for parallel processing
4. Deploy multiple instances of the user-processor

## License

This project is licensed under the MIT License - see the LICENSE file for details.