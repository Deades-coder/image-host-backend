package com.yang.imagehostbackend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置类，定义队列、交换机、绑定，以及死信队列
 * @Author 小小星仔
 * @Create 2025-05-09 21:21
 */
@Configuration
@Slf4j
public class RabbitMQConfig {
    // 队列名称
    public static final String FILE_UPLOAD_QUEUE = "file.upload.queue";
    // 交换机名称
    public static final String FILE_UPLOAD_EXCHANGE = "file.upload.exchange";
    // 路由键
    public static final String FILE_UPLOAD_ROUTING_KEY = "file.upload";

    // 死信队列和交换机
    public static final String DEAD_LETTER_QUEUE = "file.upload.dead.letter.queue";
    public static final String DEAD_LETTER_EXCHANGE = "file.upload.dead.letter.exchange";
    public static final String DEAD_LETTER_ROUTING_KEY = "file.upload.dead";

    @Bean
    public Queue fileUploadQueue() {
        return QueueBuilder.durable(FILE_UPLOAD_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    //定义文件上传交换机
    @Bean
    public DirectExchange fileUploadExchange() {
        return new DirectExchange(FILE_UPLOAD_EXCHANGE);
    }


     //绑定主队列到交换机
    @Bean
    public Binding bindingFileUploadQueue(Queue fileUploadQueue, DirectExchange fileUploadExchange) {
        return BindingBuilder.bind(fileUploadQueue)
                .to(fileUploadExchange)
                .with(FILE_UPLOAD_ROUTING_KEY);
    }

     //定义死信队列
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE)
                .build();
    }

     //定义死信交换机
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

     //绑定死信队列到死信交换机
    @Bean
    public Binding bindingDeadLetterQueue(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(DEAD_LETTER_ROUTING_KEY);
    }


     //配置消息转换器，使用 JSON 序列化
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 配置 RabbitTemplate，支持发布确认和返回
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("消息发送失败: " + (correlationData != null ? correlationData.getId() : "") + ", 原因: " + cause);
            }
        });
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setReturnsCallback(returned -> {
            log.error("消息未路由到队列: " + returned.getReplyText());
        });
        return rabbitTemplate;
    }

}
