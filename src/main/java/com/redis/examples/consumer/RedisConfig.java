package com.redis.examples.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.dynamic.RedisCommandFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericToStringSerializer<Object>(Object.class));

        return template;
    }


    /**
     * Used for statistic reporting.
     * <p>
     * Dedicated client for reporting processed messages count toward redis server
     *
     * @param redisHost
     * @param redisPort
     * @return
     */
    // TODO: igaydajiev check if there is a way to use already configured RedisTemplate
    @Bean
    public RedisClient redisClient(@Value("${spring.data.redis.host}") String redisHost,
                                   @Value("${spring.data.redis.port}") int redisPort) {
        return RedisClient.create("redis://" + redisHost + ":" + redisPort);
    }

    @Bean
    public RedisCommandFactory redisCommandFactory(StatefulRedisConnection<String, String> connection) {
        return new RedisCommandFactory(connection);
    }


    @Bean
    public StatefulRedisConnection<String, String> redisConnection(RedisClient redisClient) {
        return redisClient.connect();
    }

    @Bean
    public RedisCommands<String, String> redisCommands(StatefulRedisConnection<String, String> redisConnection) {
        return redisConnection.sync();
    }

    @Bean
    public RedisTimeSeriesCommands redisTimeSeriesCommands(RedisCommandFactory factory) {
        return factory.getCommands(RedisTimeSeriesCommands.class);
    }

    @Bean
    ChannelTopic channelTopic(@Value("${spring.redis.channel:messages:published}") String pattern) {

        return ChannelTopic.of(pattern);
    }


    @Bean
    RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory,
                                                                MessageListenerAdapter listener,
                                                                ChannelTopic messagesTopic) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listener, messagesTopic);
        return container;
    }

    @Bean
    public MessageListenerAdapter messageListenerAdapter(RedisConsumerService redisConsumerService) {

        return new MessageListenerAdapter(redisConsumerService, "onMessage");
    }
}