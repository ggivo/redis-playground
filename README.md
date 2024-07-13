# redis-playground

## Description
redis-playground is a console application that simulates scalable consumer group processing messages from a Redis Pub/Sub channel.

## How to Build
To build the project, use Maven:
```bash
mvn clean install
```

## How to Run
### Prerequisites
Make sure you have a Redis server installed and running.

### Running the Application

1. **Using Maven**: Run the application directly using Maven:
    ```bash
    mvn spring-boot:run -Dspring-boot.run.arguments="--spring.redis.host=localhost --spring.redis.port=6379"
    ```

2. **As a Java Service**: Build a JAR file and run it as a standalone Java service:
    ```bash
    java -jar target/redis-playground-0.0.1-SNAPSHOT.jar --spring.redis.host=localhost --spring.redis.port=6379
    ```

## Configuration
### Available Configurable Properties

#### Redis Connection Settings
- `spring.redis.host`: Hostname of the Redis server (default: `localhost`)
- `spring.redis.port`: Port number of the Redis server (default: `6379`)
- `spring.redis.username`: Username for Redis authentication (if applicable)
- `spring.redis.password`: Password for Redis authentication (if applicable)

#### Heartbeat Service Configuration
- `heartbeat.interval`: Interval in milliseconds between two heartbeat signals (default: `2000`)
- `allowed.missed.heartbeats`: Number of missed heartbeats after which a consumer is considered inactive (default: `3`)

#### Message Processing
- `redis.lock.expiration.seconds`: Lease period in seconds to prevent other consumers from processing the same message (default: `10`)

## Metrics
### Message Processing Rate Reporting
The application monitors and reports the rate at which messages are processed per second for each consumer node.
- Message processing rate is reported to:
    - the application log.
    - Redis TimeSeries with key `metrics:messages:processed:rate:{consumerId}`

The default interval for reporting metrics is 3 seconds, which can be configured using the `metrics.report.period.seconds` property in the application's configuration.
