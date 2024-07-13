package com.redis.examples.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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


    public HeartbeatService() {
    }

    @Scheduled(fixedRateString = "${heartbeat.interval}")
    public void sendHeartbeat() {
        String heartbeatKey = HEARTBEAT_PREFIX + redisService.getConsumerId();
        String timestamp = Instant.now().toString();
        Duration ttl = Duration.ofMillis(heartbeatInterval * allowedMissedHeartbeats);
        redisTemplate.opsForValue().set(heartbeatKey, timestamp, ttl);
        logger.debug("Heartbeat for {} updated at {} with TTL {}.", redisService.getConsumerId(), timestamp, ttl);
    }

    public List<String> getInactiveConsumers() {
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

    @Scheduled(fixedRateString = "${heartbeat.interval}", initialDelayString = "#{${heartbeat.interval}*${allowed.missed.heartbeats}}")
    public void checkMissedHeartbeats() {
        List<String> missedHeartbeats = getInactiveConsumers();

        if (!missedHeartbeats.isEmpty()) {
            // Remove inactive consumer IDs from "consumer:ids"
            for (String consumerId : missedHeartbeats) {
                redisTemplate.opsForList().remove(CONSUMER_IDS_KEY, 1, consumerId);
                logger.debug("Removed inactive consumer ID {} from {}", consumerId, CONSUMER_IDS_KEY);
            }
        }
    }
}
