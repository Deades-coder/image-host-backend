package com.yang.imagehostbackend.producer;

import com.yang.imagehostbackend.model.dto.picture.ExpandImageTaskMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import javax.annotation.Resource;

/**
 * Kafka扩图任务生产者
 * @author 小小星仔
 * @Create 2025-05-11 22:40
 */
@Component
@Slf4j
public class ExpandImageTaskProducer {

    @Resource
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.expendimage-task}")
    private String expendImageTaskTopic;

    /**
     * 发送扩图任务消息到Kafka
     * @param expandImageTaskMessage 扩图任务消息
     */
    public void sendMessage(ExpandImageTaskMessage expandImageTaskMessage) {
        String taskId = expandImageTaskMessage.getTaskId();
        log.info("开始发送扩图任务消息到Kafka，taskId={}", taskId);
        
        ListenableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                expendImageTaskTopic,
                taskId,
                expandImageTaskMessage
        );

        future.addCallback(new ListenableFutureCallback<SendResult<String, Object>>() {
            @Override
            public void onFailure(Throwable ex) {
                log.error("发送扩图任务消息到Kafka失败，taskId={}，错误信息：{}", taskId, ex.getMessage());
            }

            @Override
            public void onSuccess(SendResult<String, Object> result) {
                log.info("发送扩图任务消息到Kafka成功，taskId={}，offset={}", 
                        taskId, result.getRecordMetadata().offset());
            }
        });
    }
} 