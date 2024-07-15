package com.redis.examples.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.List;

import static com.redis.examples.consumer.Constants.S_KEY_PROCESSED;
import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
//Test requires external Redis server
//TODO : Add Embeded Redis server for the tests;
class ProcessMessageIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(ProcessMessageIntegrationTest.class);

    @Autowired
    RedisConsumerService redisConsumerService;

    @Autowired
    RedisTemplate redisTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void processMessage() throws JsonProcessingException {
        Message testMessage = new Message("test-message-1");

        String strMessage = objectMapper.writer().writeValueAsString(testMessage);
        redisConsumerService.onMessage(strMessage, "not-used");

        List<ObjectRecord<String, Message>> records = redisTemplate
                .opsForStream()
                .read(Message.class,
                        StreamReadOptions.empty().block(Duration.ofSeconds(10)).count(1),
                        StreamOffset.fromStart(S_KEY_PROCESSED));

        assertThat(records).hasSize(1);
        Message message = records.get(0).getValue();
        logger.info(objectMapper.writer().writeValueAsString(message));
        assertThat(message.getMessageId()).isEqualTo(testMessage.getMessageId());
        assertThat(message.getProcessedBy()).isNotNull();
        assertThat(message.getRandomProperty()).isNotNull();
    }
}