package com.redis.examples.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service for managing heartbeats of consumers to ensure they are active and to remove inactive consumers from Redis.
 */
@Service
public class HeartbeatService {
    private static final Logger logger = LoggerFactory.getLogger(RedisTimeSeriesCommands.class);
    private static final String HEARTBEAT_PREFIX = "heartbeat:consumer:";
    private static final String CONSUMER_IDS_KEY = "consumer:ids";

    @Value("${heartbeat.interval}")
    private long heartbeatInterval;
    @Value("${allowed.missed.heartbeats}")
    private int allowedMissedHeartbeats;

    @Autowired
    private RedisConsumerService redisService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private List<String> activeConsumers = Collections.emptyList();

    public HeartbeatService() {
    }

    /**
     * Sends a heartbeat to the Redis server at a fixed rate specified by the {@code heartbeat.interval} property.
     * This method is scheduled to run at regular intervals to update the heartbeat timestamp for the current consumer.
     *
     * The heartbeat key is constructed using a prefix and the consumer ID obtained from {@code redisService}.
     * The current timestamp is recorded, and the key is set in Redis with a time-to-live (TTL) duration calculated
     * based on the heartbeat interval and the allowed number of missed heartbeats.
     */
    @Scheduled(fixedRateString = "${heartbeat.interval}")
    public void sendHeartbeat() {
        String heartbeatKey = HEARTBEAT_PREFIX + redisService.getConsumerId();
        String timestamp = Instant.now().toString();
        Duration ttl = Duration.ofMillis(heartbeatInterval * allowedMissedHeartbeats);
        redisTemplate.opsForValue().set(heartbeatKey, timestamp, ttl);
        logger.debug("Heartbeat for {} updated at {} with TTL {}.", redisService.getConsumerId(), timestamp, ttl);
    }

    /**
     * Validates active consumers and removes inactive consumers from the list of active consumer IDs in Redis.
     *
     * This method is scheduled to run at a fixed rate specified by the {@code heartbeat.interval} property,
     * with an initial delay determined by the product of {@code heartbeat.interval} and {@code allowed.missed.heartbeats}.
     *
     * The method retrieves a list of inactive consumer IDs by calling {@code getInactiveConsumers()}.
     * If any inactive consumers are found, their IDs are removed from the Redis list identified by {@code CONSUMER_IDS_KEY}.
     * A debug log entry is created for each removed consumer ID.
     */
    @Scheduled(fixedRateString = "${heartbeat.interval}", initialDelayString = "#{${heartbeat.interval}*${allowed.missed.heartbeats}}")
    public void validateActiveConsumers() {
        List<String> missedHeartbeats = getInactiveConsumers();

        if (!missedHeartbeats.isEmpty()) {
            // Remove inactive consumer IDs from "consumer:ids"
            for (String consumerId : missedHeartbeats) {
                redisTemplate.opsForList().remove(CONSUMER_IDS_KEY, 1, consumerId);
                logger.debug("Removed inactive consumer ID {} from {}", consumerId, CONSUMER_IDS_KEY);
            }
        }

        List<String> currentConsumerIds = getCurrentConsumerIds();
        if (!currentConsumerIds.equals(activeConsumers)) {
            activeConsumers = currentConsumerIds;
            eventPublisher.publishEvent(new ActiveConsumersChangedEvent(currentConsumerIds));
        }
    }


    /**
     * Retrieves a list of inactive consumer IDs.
     * An inactive consumer is determined by the absence of its heartbeat key in Redis.
     *
     * @return a list of inactive consumer IDs
     */
    private List<String> getInactiveConsumers() {
        List<String> inactiveConsumers = new ArrayList<>();
        List<String> consumerIds = redisTemplate.opsForList().range(CONSUMER_IDS_KEY, 0, -1);

        if (consumerIds != null) {
            for (String appId : consumerIds) {
                String heartbeatKey = HEARTBEAT_PREFIX + appId;
                Boolean keyExists = redisTemplate.hasKey(heartbeatKey);

                if (keyExists != null && !keyExists) {
                    inactiveConsumers.add(appId);
                }
            }
        }

        return inactiveConsumers;
    }

    /**
     * Retrieves the current list of active consumer IDs from Redis.
     * The list is sorted to ensure consistent comparison.
     *
     * @return a sorted list of current consumer IDs
     */
    private List<String> getCurrentConsumerIds() {
        List<String> consumerIds = redisTemplate.opsForList().range(CONSUMER_IDS_KEY, 0, -1);
        if (consumerIds != null) {
            Collections.sort(consumerIds);
        }
        return consumerIds != null ? consumerIds : new ArrayList<>();
    }
}
