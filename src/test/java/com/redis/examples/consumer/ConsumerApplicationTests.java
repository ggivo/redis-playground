package com.redis.examples.consumer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
class ConsumerApplicationTests {

    @Autowired
    private RedisConsumerService myService;

    @Test
    void contextLoads() {
        assertThat(myService).isNotNull();
    }

}
