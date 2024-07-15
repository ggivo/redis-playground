package com.redis.examples.consumer;

import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AppInitializer {

    // Redis TimeSeries metric labels
    public static final String L_CONSUMER = "consumer";
    public static final String L_APP = "app";
    public static final String L_METRIC = "metric";

    private static final Logger logger = LoggerFactory.getLogger(AppInitializer.class);
    @Autowired
    RedisTimeSeriesCommands tsCmd;

    @Autowired
    RedisCommands redisCommands;

    @Autowired
    RedisConsumerService consumer;

    @Autowired
    HeartbeatService heartbeatService;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        registerConsumer();
    }

    @EventListener(ContextClosedEvent.class)
    public void onShutdown() {
        unregisterConsumer();
    }

    @EventListener(ContextRefreshedEvent.class)
    public void initRedisTS() {

        // Redis TimeSeries metric labels
        try {
            tsCmd.tsCreateWithRetentionAndLabels(consumer.getProcessedMessagesTsKey() + ":count",
                    L_CONSUMER, consumer.getConsumerId(),
                    L_APP, "redis",
                    L_METRIC, "messages:processed:count");

            tsCmd.tsCreateWithRetentionAndLabels(consumer.getFailedMessagesTsKey()  + ":count",
                    L_CONSUMER, consumer.getConsumerId(),
                    L_APP, "redis",
                    L_METRIC, "messages:failed:count");

        } catch (Exception e) {
            logger.debug("TimeSeries {} already exists.", consumer.getProcessedMessagesTsKey());
        }
    }

    private void registerConsumer() {
        //Send one heart beat before registering the app as active
        //to make sure other nodes does not remove it from active consumers list.
        heartbeatService.sendHeartbeat();
        redisCommands.lpush("consumer:ids", consumer.getConsumerId());
        logger.info("{} - Registered", consumer.getConsumerId());
    }

    private void unregisterConsumer() {
        redisCommands.lrem("consumer:ids", 1, consumer.getConsumerId());
        logger.info("{} - Deregistered", consumer.getConsumerId());
    }

}
