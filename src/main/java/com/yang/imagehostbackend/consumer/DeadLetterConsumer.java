package com.yang.imagehostbackend.consumer;

import com.rabbitmq.client.Channel;
import com.yang.imagehostbackend.model.dto.FileUploadMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * @Author 小小星仔
 * @Create 2025-05-09 21:35
 */
@Component
@Slf4j
public class DeadLetterConsumer {
    @RabbitListener(queues = "file.upload.dead.letter.queue")
    public void processDeadLetter(FileUploadMessage message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.error("收到死信消息，任务 ID: {}, COS key: {}, 文件路径: {}, 原始文件名: {}",
                message.getTaskId(), message.getCosKey(), message.getFilePath(), message.getOriginalFilename());

        try {
            channel.basicAck(deliveryTag, false);
        } catch (IOException e) {
            log.error("死信消息确认失败，任务 ID: {}", message.getTaskId(), e);
        }
    }
}
