package com.redis.examples.consumer;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MessageProcessor {

    public Message process(Message message, String consumerId) {
        // Simulate processing time
        try {
            Thread.sleep((long) (Math.random() * 100));
        } catch (InterruptedException e) {
            throw new RuntimeException("Error observed while processing message!", e);
        }
        message.setProcessedBy(consumerId);
        message.setRandomProperty(UUID.randomUUID().toString());

        return message;
    }
}
