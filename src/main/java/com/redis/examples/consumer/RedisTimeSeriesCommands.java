package com.redis.examples.consumer;

import io.lettuce.core.dynamic.Commands;
import io.lettuce.core.dynamic.annotation.Command;
import io.lettuce.core.dynamic.annotation.Param;


public interface RedisTimeSeriesCommands extends Commands {

    @Command("TS.CREATE :key LABELS")
    String tsCreateWithRetentionAndLabels(@Param("key") String key,
                                          @Param("labels") String... labels);

    @Command("TS.ADD :key :timestamp :value")
    Long tsAdd(@Param("key") String key,
               @Param("timestamp") long timestamp,
               @Param("value") double value);
}