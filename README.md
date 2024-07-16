# redis-playground
**Table of content:**
* [Description](#description)
* [How to Build](#how-to-build)
* [How to Run](#how-to-run)
* [Configuration](#configuration)
* [Metrics](#metrics)
* [Alternative Approaches](#alternative-approaches)
  
## Description
The redis-playground is a console application that simulates scalable consumer group processing messages from a Redis Pub/Sub channel.

Multiple instances of this app can run in parallel, consuming messages from a configured pub/sub channel. Each message will be processed only once.

The list of active service instances is stored in the Redis server under the key `consumer:ids`.

### Message Processing
Messages are distributed across consumers using a consistent hashing-like algorithm.

- **Virtual Slot Mapping**: A map of virtual slots is generated. Each slot is assigned one or more actual consumers.

- **Configurable Replicas**: Each slot is served by a configurable number of consumers (`hashslot.replica.count`).

- **Message Processing Guarantee**: Consumers serving the same slot utilize explicit locking based on the message ID to ensure each message is processed only once.

This approach ensures that each message is processed by only one consumer, thereby preventing duplication, while also facilitating horizontal scaling by reducing the number of calls to the Redis server to acquire a lease for processing a given message.

## How to Build
To build the project, use Maven. Execute the following command:
```bash
mvn clean install
```

## How to Run
### Prerequisites
Make sure you have a Redis server installed and running.

### Running the Application
To run instances of the application, use one of the following methods:

1. **Using Maven**: Run the application directly using Maven:
    ```bash
    mvn spring-boot:run -Dspring-boot.run.arguments="--spring.redis.host=localhost --spring.redis.port=6379"
    ```

2. **As a Java Service**: Build a JAR file and run it as a standalone Java service using the following command:
    ```bash
    java -jar consumer-0.0.1-SNAPSHOT.jar --spring.redis.host=localhost --spring.redis.port=6379
    ```
   
### Running the Example Publisher
Publisher is available in `publisher\pub.py`
```bash
python publisher\pub.py
```

## Configuration
### Redis Connection Settings
- `spring.redis.host`: Hostname of the Redis server (default: `localhost`)
- `spring.redis.port`: Port number of the Redis server (default: `6379`)
- `spring.redis.username`: Username for Redis authentication (if applicable)
- `spring.redis.password`: Password for Redis authentication (if applicable)

### Heartbeat Service Configuration
- `heartbeat.interval`: Interval in milliseconds between two heartbeat signals (default: `2000`)
- `allowed.missed.heartbeats`: Number of missed heartbeats after which a consumer is considered inactive (default: `3`)

### Message Processing
- `redis.lock.expiration.seconds`: Lease period in seconds to prevent other consumers from processing the same message (default: `10`)
- `hashslot.slots.total`: Number of virtual slots (default: `256`)
- `hashslot.replica.count`: Number of replicas per slot (default: `1`)

## Metrics
### Message Processing Rate Reporting
The application monitors and reports count of messages processed/failed for each consumer node.
Metrics are reported to the log and also stored in Redis TimeSeries
- Redis TimeSeries
  - Processed messages counter
    - key `metrics:messages:processed:{consumerId}:count`
    - labels `app=redis` `consumer={consumerId}` `metric=messages:processed:count`
  - Failed messages counter
      - key `metrics:messages:failed:{consumerId}:count`
      - labels `app=redis` `consumer={consumerId} 'metric=messages:failed:count'`

### Example metrics queries 
- Total Messages processed per 10m
  ```
  TS.MRANGE  - + WITHLABELS ALIGN start AGGREGATION sum 600000 FILTER app=redis metric=messages:processed:rate GROUPBY app REDUCE sum
  ```
- Messages processed per consumer  per 10m
  ```
  TS.MRANGE  - + WITHLABELS ALIGN start AGGREGATION sum 600000 FILTER app=redis metric=messages:processed:rate GROUPBY consumer REDUCE sum
  ```
- Messages processed by given consumer  per 3s
  ```
  TS.MRANGE  - + WITHLABELS ALIGN start AGGREGATION sum 3000 FILTER app=redis metric=messages:processed:rate GROUPBY consumer REDUCE sum
  ```

The default interval for reporting metrics is 3 seconds configurable by `metrics.report.period.seconds` property.
Here's the revised wording and formatting for the Java project README.md section containing the list of future improvements:

## Alternative Approaches
1. **Update Existing Publishers to Push Directly to Redis STREAM**
  - Utilize available STREAM GROUPS.
  - Requires control over client-side publishing.

2. **Use Proxy Consumer to Read from the PUB/SUB Channel and Push to STREAM**
  - Attach a STREAM group.
  - Note: The proxy service itself becomes a point of failure and needs evaluation to determine if it still needs to be scaled horizontally.

3. **Use Consistent Hashing to Distribute Messages Among Consumers**
  - Implement consistent hashing as an alternative to locking.
