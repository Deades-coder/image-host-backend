package com.yang.imagehostbackend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 * @Author 小小星仔
 * @Create 2025-05-13 20:39
 */
@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.exchange:thumb-exchange}")
    private String thumbExchange;

    @Value("${rabbitmq.queue:thumb-queue}")
    private String thumbQueue;

    @Value("${rabbitmq.routing-key:thumb-routing-key}")
    private String thumbRoutingKey;

    @Bean
    public DirectExchange thumbExchange() {
        return new DirectExchange(thumbExchange);
    }

    @Bean
    public Queue thumbQueue() {
        return new Queue(thumbQueue, true);
    }

    @Bean
    public Binding thumbBinding() {
        return BindingBuilder
                .bind(thumbQueue())
                .to(thumbExchange())
                .with(thumbRoutingKey);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }
}