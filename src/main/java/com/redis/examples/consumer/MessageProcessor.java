package com.redis.examples.consumer;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MessageProcessor {

    public Message process(Message message, String consumerId) {
        message.setProcessedBy(consumerId);
        message.setRandomProperty(UUID.randomUUID().toString());

        return message;
    }
}
