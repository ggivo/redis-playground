package com.redis.examples.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RedisConsumerService {
    private static final Logger logger = LoggerFactory.getLogger(RedisConsumerService.class);

    private final String consumerId = "Consumer-" + UUID.randomUUID();

    private final AtomicInteger messageCount = new AtomicInteger(0);
    @Autowired
    MessageProcessor messsageProcessor;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Value("${redis.lock.expiration.seconds}")
    private long lockExpirationSeconds;
    @Value("${metrics.report.period.seconds}")
    private long metricsReportPeriodSeconds;
    @Autowired
    private RedisCommands<String, String> redisCommands;
    @Autowired
    private RedisTimeSeriesCommands tsCmds;

    public void onMessage(String message, String channel) {
        logger.trace("{} - Received message:", consumerId, message);

        try {
            Message msg = objectMapper.readValue(message, Message.class);

            // Try to acquire the lock
            String lockKey = "lock:" + msg.getMessageId();
            String messageId = msg.getMessageId();
            String lockValue = consumerId;

            // Try to set the lock with an expiration time to prevent other notes processing same message
            boolean lockAcquired = acquireLock(lockKey, lockValue);
            if (lockAcquired) {
                try {
                    // Process the message
                    // TODO: implement retry onError logic
                    Message processed = messsageProcessor.process(msg, consumerId);
                    logger.debug("{} - Processed message: {}", consumerId, objectMapper.writeValueAsString(msg));

                    // Store the processed message in Redis Stream
                    Map<String, String> processedMessage = convertToMap(msg);
                    redisTemplate.opsForStream().add("messages:processed", processedMessage);

                    // Update the message count
                    messageCount.incrementAndGet();
                } finally {
                    // Do not release lock explicitly, but relly on ttl
                    // this way we make sure other consumers do not process same message
                }
            } else {
                logger.debug("{} - Message already processed by another consumer: {}", consumerId, messageId);
            }
        } catch (Exception e) {
            logger.error("{} - Error processing message: {}", consumerId, e.getMessage(), e);
        }
    }

    private boolean acquireLock(String lockKey, String value) {
        // Try to set the lock with an expiration time
        Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(lockKey, consumerId, lockExpirationSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(lockAcquired);
    }

    private Map<String, String> convertToMap(Message msg) {
        Map<String, String> map = new HashMap<>();
        map.put("message_id", msg.getMessageId());
        map.put("processed_by", msg.getProcessedBy());
        map.put("random_property", msg.getRandomProperty());
        return map;
    }

    @Scheduled(fixedRateString = "${metrics.report.period.seconds}000")
    private void reportMessageRate() {
        int count = messageCount.getAndSet(0);
        double rate = count / (double) metricsReportPeriodSeconds;
        logger.info("Messages processed per second: {}", rate);
        reportToRedisTimeSeries(rate);
    }

    private void reportToRedisTimeSeries(double rate) {
        String key = getProcessedMessagesTsKey();

        // TODO : use redis server time "*"?
        tsCmds.tsAdd(key, System.currentTimeMillis(), rate);
        logger.debug("Reported messages processed per second to Redis TimeSeries: {}", rate);
    }

    public String getProcessedMessagesTsKey() {
        return "metrics:messages:processed:rate:" + consumerId;
    }

    public String getConsumerId() {
        return consumerId;
    }

}
