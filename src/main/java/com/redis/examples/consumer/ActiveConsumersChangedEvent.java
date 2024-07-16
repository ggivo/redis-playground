package com.redis.examples.consumer;

import java.util.List;

/**
 * Event representing a change in the list of active consumers.
 */
public class ActiveConsumersChangedEvent {
    private final List<String> newConsumerIds;

    public ActiveConsumersChangedEvent(List<String> newConsumerIds) {
        this.newConsumerIds = newConsumerIds;
    }

    public List<String> getNewConsumerIds() {
        return newConsumerIds;
    }
}