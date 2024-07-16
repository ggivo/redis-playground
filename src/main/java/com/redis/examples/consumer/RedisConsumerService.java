package com.redis.examples.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.api.sync.RedisCommands;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RedisConsumerService {
    private static final Logger logger = LoggerFactory.getLogger(RedisConsumerService.class);

    private final String consumerId = "Consumer-" + UUID.randomUUID();

    // Number of successfully processed messages since last reported
    private final AtomicInteger successCount = new AtomicInteger(0);
    // Total number of processed messages
    private Counter successCountTotal;

    // Number of errors since we last reported them
    private final AtomicInteger errorCount = new AtomicInteger(0);
    // Total number of errors
    private Counter errorCountTotal;

    // Number of messages not in current consumer managed slots.
    private final AtomicInteger skippedCount = new AtomicInteger(0);

    @Value("${redis.lock.expiration.seconds}")
    private long lockExpirationSeconds;

    @Value("${metrics.report.period.seconds}")
    private long metricsReportPeriodSeconds;

    @Autowired
    MessageProcessor messageProcessor;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisCommands<String, String> redisCommands;

    @Autowired
    private RedisTimeSeriesCommands tsCmds;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    HashSlotManager slotManager;

    @PostConstruct
    public void init() {
        successCountTotal = Counter.builder("messages:processed:success:count")
                .description("Number of messages processed")
                .register(meterRegistry);
        errorCountTotal = Counter.builder("messages:processed:failed:count")
                .description("Number of messages processed")
                .register(meterRegistry);
    }

    /**
     * Checks if the current message should be processed by this consumer using HashSlotManager.
     *
     * Consumers serving the same slot use explicit locking based on the message ID to ensure each message is processed only once.
     * Consequently, there will be exactly {@code replicaCount} consumers attempting to acquire a lease for processing a given message,
     * reducing the load on the Redis server.
     */
    public void onMessage(String message, String channel) {

        logger.trace("{} - Received message:", consumerId, message);

        try {
            Message msg = objectMapper.readValue(message, Message.class);

            // Try to acquire the lock
            String lockKey = "lock:" + msg.getMessageId();
            String messageId = msg.getMessageId();
            String lockValue = consumerId;

            if (slotManager.isProcessedBy(messageId, consumerId)) {
                // Try to acquire lease with an expiration time to prevent other notes processing same message
                boolean leaseAcquired = acquireLock(lockKey, lockValue);
                if (leaseAcquired) {
                    // Process the message
                    Message processed = messageProcessor.process(msg, consumerId);
                    logger.debug("{} - Processed message: {}", consumerId, objectMapper.writeValueAsString(msg));

                    // Store the processed message in Redis Stream
                    ObjectRecord<String, Message> record = StreamRecords.newRecord()
                            .in("messages:processed")
                            .ofObject(processed);
                    redisTemplate
                            .opsForStream()
                            .add(record);

                    // Update processed messages count
                    incrementSuccessCount();
                } else {
                    logger.debug("{} - Message already processed by another consumer: {}", consumerId, messageId);
                }
            } else {
                skippedCount.incrementAndGet();
            }
        } catch (Exception e) {
            // Update the error count
            incrementErrorCount();
            logger.error("{} - Error processing message: {}", consumerId, e.getMessage(), e);
        }
    }

    private void incrementErrorCount() {
        errorCountTotal.increment();
        errorCount.incrementAndGet();
    }

    private void incrementSuccessCount() {
        successCountTotal.increment();
        successCount.incrementAndGet();
    }

    private boolean acquireLock(String lockKey, String value) {
        // Try to acquire lease with configured lease expiration time
        Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(lockKey, consumerId, lockExpirationSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(lockAcquired);
    }

    @Scheduled(fixedRateString = "${metrics.report.period.seconds}000")
    private void reportMetrics() {
        int processed = successCount.getAndSet(0);
        int errors = errorCount.getAndSet(0);
        int skipped = skippedCount.getAndSet(0);

        logger.info("Messages processed: {}, failed: {}, skipped: {}", processed, errors, skipped);

        tsCmds.tsAdd(getProcessedMessagesTsKey() + ":count", System.currentTimeMillis(), successCountTotal.count());
        tsCmds.tsAdd(getProcessedMessagesTsKey() + ":rate", System.currentTimeMillis(), processed);

        tsCmds.tsAdd(getFailedMessagesTsKey() + ":count", System.currentTimeMillis(), errorCountTotal.count());
        tsCmds.tsAdd(getFailedMessagesTsKey()+ ":rate", System.currentTimeMillis(), errors);
    }

    public String getProcessedMessagesTsKey() {
        return "metrics:messages:processed:" + consumerId;
    }

    public String getFailedMessagesTsKey() {
        return "metrics:messages:failed:" + consumerId;
    }

    public String getConsumerId() {
        return consumerId;
    }

}
