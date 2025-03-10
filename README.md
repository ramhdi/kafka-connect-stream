# Kafka Streaming Data Processing Pipeline

A production-ready data processing pipeline implemented with Apache Kafka, Schema Registry, and Kafka Streams. This project demonstrates fetching data from REST APIs, processing it through Kafka Streams, and making it available for downstream systems through Kafka Connect.

### Core Components

| Component | Description | Technology |
|-----------|-------------|------------|
| **REST Source Connector** | Fetches data from REST APIs | Custom Kafka Connect (Scala) |
| **Schema Registry** | Manages Avro schemas | Confluent Schema Registry |
| **Kafka Streams Processor** | Enriches user data | Kafka Streams (Scala) |
| **JDBC Sink Connector** | Writes to PostgreSQL | Confluent JDBC Connector |
| **Management UIs** | Monitoring and administration | AKHQ, Kafka Connect UI |

### Data Flow

```
REST API → REST Connector → Kafka (rest-data) → Kafka Streams → Kafka (processed-users) → PostgreSQL
                                     ↑                                       ↑
                                     ↓                                       ↓
                              Schema Registry ←----- Schema Evolution ----→ Schema Registry
```

## Features

- **Schema-First Design**: All data is serialized using Avro with centralized schema management
- **Stateful Processing**: Tracks and counts user appearances with persistent state stores
- **Exactly-Once Processing**: Guarantees that each record is processed exactly once
- **Schema Evolution**: Structured for seamless evolution of data models
- **Resource-Optimized Configuration**: Docker Compose with appropriate memory limits
- **Full Monitoring**: Integrated UIs for observing all components of the pipeline

## Prerequisites

- Docker and Docker Compose (version 2.0+)
- JDK 11 or later
- Scala Build Tool (SBT) 1.5+
- ~4GB available RAM for running the complete stack

## Project Structure

```
kafka-pipeline/
├── docker/                          # Docker configuration files
│   ├── docker-compose.yml           # Main Docker Compose configuration
│   ├── rest-source-config.json      # REST connector configuration
│   ├── jdbc-sink-config.json        # JDBC sink connector configuration
│   ├── deploy-connectors.sh         # Script to deploy connectors
│   └── connectors/                  # Directory for connector JARs
├── kafka-rest-connector/            # Source code for the REST connector
│   ├── src/main/scala/             
│   │   └── com/example/kafka/connect/
│   │       ├── RestSourceConnector.scala
│   │       ├── RestSourceTask.scala
│   │       ├── RestSourceConfig.scala
│   │       └── UserSchema.scala
│   └── build.sbt                    # SBT build configuration
└── kafka-user-processor/            # Source code for the Kafka Streams processor
    ├── src/main/scala/
    │   └── com/example/kafka/streams/
    │       ├── UserDataProcessor.scala
    │       └── EnrichedUserSchema.scala
    └── build.sbt                    # SBT build configuration
```

## Detailed Component Documentation

### REST Source Connector

The REST Source Connector fetches user data from configurable REST API endpoints and produces it to Kafka topics.

#### Key Features

- **Configurable Endpoints**: Can be pointed to any REST API
- **Scheduled Polling**: Configurable polling interval
- **Structured Schema**: Converts JSON responses to Avro records
- **API Authentication**: Supports API keys for authenticated endpoints
- **Asynchronous Processing**: Non-blocking I/O with Akka HTTP

#### Configuration Options

| Option | Default | Description |
|--------|---------|-------------|
| `rest.endpoint` | https://jsonplaceholder.typicode.com/users | REST API endpoint URL |
| `poll.interval.ms` | 60000 | Poll interval in milliseconds |
| `topic` | rest-data | Target Kafka topic |
| `api.key` | (empty) | API key for REST endpoint authentication |

#### Data Schema

The connector produces user records with the following schema:

```
User {
  id: Integer
  name: String
  username: String
  email: String
  address: {
    street: String
    suite: String
    city: String
    zipcode: String
    geo: {
      lat: String
      lng: String
    }
  }
  phone: String
  website: String
  company: {
    name: String
    catchPhrase: String
    bs: String
  }
}
```

### Kafka Streams Processor

The Kafka Streams Processor consumes user data, enriches it with additional metadata, and produces it to an output topic.

#### Key Features

- **Stateful Processing**: Tracks user appearance counts in a persistent store
- **Data Enrichment**: Adds timestamp and count information to records
- **Exactly-Once Semantics**: Guarantees data integrity
- **Avro Serialization**: Compatible with Schema Registry

#### Enriched Data Schema

The processor produces enriched user records with the following schema:

```
EnrichedUser {
  // All original User fields +
  timestamp: String    // ISO-8601 timestamp
  count: Long          // Number of times this user has been seen
}
```

### JDBC Sink Connector

The JDBC Sink Connector writes processed user data to PostgreSQL.

#### Key Features

- **Auto Schema Creation**: Automatically creates database tables
- **Schema Evolution**: Updates database schema as Avro schema evolves
- **Upsert Support**: Updates existing records by primary key
- **Transaction Support**: Ensures data consistency

#### Configuration Options

| Option | Description |
|--------|-------------|
| `connection.url` | JDBC connection URL for PostgreSQL |
| `auto.create` | Automatically create tables if they don't exist |
| `auto.evolve` | Evolve table schema as data schema changes |
| `insert.mode` | Use upsert to update existing records |
| `pk.mode` | Use record key as primary key |
| `pk.fields` | ID field to use as primary key |

## Building the Components

### Building the REST Connector

```bash
cd kafka-rest-connector
sbt clean assembly
mkdir -p ../docker/connectors
cp target/scala-2.13/kafka-rest-connector-assembly-0.1.jar ../docker/connectors/
```

### Building the User Processor

```bash
cd kafka-user-processor
sbt clean assembly
mkdir -p ../docker/streams
cp target/scala-2.13/kafka-user-processor-assembly-0.1.jar ../docker/streams/
```

## Running the Pipeline

### Starting the Docker Environment

```bash
cd docker
docker-compose up -d
```

The Docker environment includes:

- Zookeeper and Kafka broker
- Schema Registry
- Kafka Connect
- AKHQ (Kafka management UI)
- Kafka Connect UI
- PostgreSQL
- Kafka Streams processor

### Accessing the UIs

- **AKHQ (Kafka management)**: http://localhost:8008
- **Kafka Connect UI**: http://localhost:8000
- **Schema Registry**: http://localhost:8081

### Deploying Connectors

Connectors are automatically deployed by the `connector-deploy` service. If you need to manually deploy:

```bash
curl -X POST -H "Content-Type: application/json" \
  --data @rest-source-config.json \
  http://localhost:8083/connectors

curl -X POST -H "Content-Type: application/json" \
  --data @jdbc-sink-config.json \
  http://localhost:8083/connectors
```

## Working with Schema Registry and Avro

### Schema Registry Concepts

Schema Registry maintains a versioned history of all schemas. Key features:

- **Schema Versioning**: Tracks all schema versions
- **Compatibility Checks**: Ensures schema evolution is compatible
- **Centralized Management**: Single source of truth for schemas

### Schema Evolution

Guidelines for schema evolution:

1. **Adding Fields**: Add optional fields with defaults
2. **Removing Fields**: Mark as optional first before removing in next version
3. **Changing Types**: Use compatible type changes (int → long is OK, string → int is not)
4. **Renaming Fields**: Add new field, maintain old field, then remove old field

### Implementing Schema Changes

1. For the connector, update the `UserSchema.scala` class
2. For the processor, update the Avro schemas in `EnrichedUserSchema.scala`
3. Rebuild and redeploy the components

## Monitoring and Troubleshooting

### Key Metrics to Monitor

- **Consumer Lag**: Indicates processing bottlenecks
- **Error Rates**: Track serialization or processing errors
- **Throughput**: Messages processed per second
- **State Store Size**: Growth of state stores

### Common Issues and Solutions

#### Schema Registry Issues

If you see errors related to schema registration:

1. Check Schema Registry is running: `curl http://localhost:8081/subjects`
2. Ensure connector and processor use compatible schemas
3. Examine Schema Registry logs: `docker logs schema-registry`

#### Deserialization Errors

If the processor fails with deserialization errors:

1. Verify the key.converter and value.converter settings in connector configuration
2. Check that the processor is using the correct key/value Serde classes
3. Restart the user-processor to rebuild its state: `docker-compose restart user-processor`

#### State Store Persistence

The processor maintains state in `/tmp/kafka-streams-user-processor` within the container, which is mounted to the host's `./streams/data` directory. If you need to reset the state:

```bash
docker-compose stop user-processor
rm -rf ./streams/data/*
docker-compose start user-processor
```

## Performance Tuning

### Memory Configuration

Each service has memory limits configured in Docker Compose:

| Service | Memory Limit | JVM Configuration |
|---------|--------------|------------------|
| Zookeeper | 512MB | N/A |
| Kafka Broker | 768MB | -Xmx512M -Xms256M |
| Schema Registry | 384MB | -Xmx256M -Xms128M |
| Kafka Connect | 512MB | -Xmx384M -Xms256M |
| AKHQ | 512MB | -Xms128M -Xmx384M |
| User Processor | 512MB | -Xms256m -Xmx384m |
| PostgreSQL | 512MB | N/A |

Adjust these values based on your workload requirements.

### Scaling Considerations

For production environments:

1. **Kafka Broker**: Increase partition count for parallel processing
2. **Kafka Connect**: Increase task.max for connectors
3. **Kafka Streams**: Add multiple instances of the processor
4. **Memory**: Increase JVM heap sizes based on workload

## Security Considerations

The current implementation focuses on functionality rather than security. For production deployment, consider:

### Authentication & Authorization

- Enable Kafka SASL authentication
- Configure ACLs for topic access control
- Secure Schema Registry with authentication

### Network Security

- Use SSL/TLS for all communications
- Implement network segmentation
- Use secure connection strings for JDBC

### Secrets Management

- Externalize credentials to environment variables or secure stores
- Avoid hardcoded secrets in configuration files

## Future Enhancements

Potential improvements to the pipeline:

1. **Monitoring & Metrics**: Add Prometheus and Grafana integration
2. **Data Validation**: Add input validation and error handling
3. **Dead Letter Queue**: Add DLQ for processing failures
4. **High Availability**: Configure for multi-node deployment
5. **Security**: Implement authentication and encryption