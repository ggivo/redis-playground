package com.redis.examples.consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MessageProcessorTest {

    private String consumerId = "Consumer1";

    @Test
    void process() {
        Message message = new Message("test-message-1");

        MessageProcessor mp = new MessageProcessor();
        Message processed = mp.process(message, consumerId);

        assertThat(processed.getMessageId()).isEqualTo("test-message-1");
        assertThat(processed.getProcessedBy()).isEqualTo(consumerId);
        assertThat(processed.getRandomProperty()).isNotNull();
    }
}