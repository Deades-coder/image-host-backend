package com.yang.imagehostbackend.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka配置
 * @Author 小小星仔
 * @Create 2025-05-11 22:39
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Value("${kafka.topics.expendimage-task}")
    private String expendImageTaskTopic;

    /**
     * Kafka管理Bean，用于创建topic
     */
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    /**
     * 创建扩图任务主题
     * 分区数：3，副本数：1
     */
    @Bean
    public NewTopic outpaintingTaskTopic() {
        return TopicBuilder.name(expendImageTaskTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

}