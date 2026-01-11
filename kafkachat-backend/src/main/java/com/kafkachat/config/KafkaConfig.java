package com.kafkachat.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic chatMessageTopic() {
        return TopicBuilder.name("chat-messages")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic chatEventTopic() {
        return TopicBuilder.name("chat-events")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
