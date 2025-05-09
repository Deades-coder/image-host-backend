package com.yang.imagehostbackend.consumer;

import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.rabbitmq.client.Channel;
import com.yang.imagehostbackend.manager.FileManager;
import com.yang.imagehostbackend.model.dto.FileUploadMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * @Author 小小星仔
 * @Create 2025-05-09 21:27
 */
@Component
@Slf4j
public class FileUploadConsumer {

    @Resource
    private FileManager cosManager;

    private static final int MAX_RETRY_COUNT = 3;

    @RabbitListener(queues = "file.upload.queue")
    public void processFileUpload(FileUploadMessage message, Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                  @Header(name = "x-death", required = false) Map<String, Object> xDeath) {

        File file = new File(message.getFilePath());
        long retryCount = getRetryCount(xDeath);
        String taskId = message.getTaskId();

        try {

            // 检查重试次数
            if (retryCount >= MAX_RETRY_COUNT) {
                handleMaxRetriesExceeded(taskId, file, channel, deliveryTag);
                return;
            }

            // 验证文件
            if (!file.exists()) {
                handleFileNotFound(taskId, file, channel, deliveryTag);
                return;
            }

            // 上传文件
            uploadToCosAndCleanup(message, file, channel, deliveryTag, taskId);

        } catch (CosServiceException e) {
            handleCosServiceError(taskId, e, channel, deliveryTag, retryCount, file);
        } catch (InterruptedException e) {
            handleInterruptedError(taskId, e, channel, deliveryTag, retryCount, file);
        } catch (Exception e) {
            handleUnexpectedError(taskId, e, channel, deliveryTag, retryCount, file);
        }
    }

    // ==== 私有方法 ====
    private long getRetryCount(Map<String, Object> xDeath) {
        return xDeath != null && xDeath.containsKey("count") ? (Long) xDeath.get("count") : 0;
    }

    private void uploadToCosAndCleanup(FileUploadMessage message, File file,
                                       Channel channel, long deliveryTag, String taskId)
            throws CosClientException, InterruptedException, IOException {

        cosManager.uploadFileAndWait(message.getCosKey(), file);
        log.info("任务 {} 文件上传成功, 存储路径: {}", taskId, message.getCosKey());
        channel.basicAck(deliveryTag, false);
        deleteTempFile(file, taskId);
    }

    private void handleMaxRetriesExceeded(String taskId, File file,
                                          Channel channel, long deliveryTag) throws IOException {

        log.error("任务 {} 达到最大重试次数 {}", taskId, MAX_RETRY_COUNT);
        channel.basicNack(deliveryTag, false, false);
        deleteTempFile(file, taskId);
        // 可扩展：发送到死信队列后触发告警
    }

    private void handleFileNotFound(String taskId, File file,
                                    Channel channel, long deliveryTag) throws IOException {

        log.error("任务 {} 文件不存在: {}", taskId, file.getPath());
        channel.basicNack(deliveryTag, false, false);
    }

    private void handleCosServiceError(String taskId, CosServiceException e,
                                       Channel channel, long deliveryTag, long retryCount, File file) {

        log.error("COS服务异常, 任务 {}: {}", taskId, e.getErrorMessage());
        handleRetryOrDeadLetter(channel, deliveryTag, retryCount, file, taskId);
    }

    private void handleInterruptedError(String taskId, InterruptedException e,
                                        Channel channel, long deliveryTag, long retryCount, File file) {

        log.error("任务 {} 被中断", taskId);
        Thread.currentThread().interrupt(); // 恢复中断状态
        handleRetryOrDeadLetter(channel, deliveryTag, retryCount, file, taskId);
    }

    private void handleUnexpectedError(String taskId, Exception e,
                                       Channel channel, long deliveryTag, long retryCount, File file) {

        log.error("任务 {} 未知错误: {}", taskId, e.getMessage());
        handleRetryOrDeadLetter(channel, deliveryTag, retryCount, file, taskId);
    }

    private void handleRetryOrDeadLetter(Channel channel, long deliveryTag,
                                         long retryCount, File file, String taskId) {

        try {
            if (retryCount < MAX_RETRY_COUNT - 1) {
                log.info("任务 {} 准备重试, 当前次数: {}", taskId, retryCount + 1);
                channel.basicNack(deliveryTag, false, true);
            } else {
                log.warn("任务 {} 转入死信队列", taskId);
                channel.basicNack(deliveryTag, false, false);
            }
        } catch (IOException ex) {
            log.error("任务 {} 消息拒绝失败: {}", taskId, ex.getMessage());
        } finally {
            deleteTempFile(file, taskId);
        }
    }

    private void deleteTempFile(File file, String taskId) {
        if (file != null && file.exists() && !file.delete()) {
            log.warn("任务 {} 临时文件删除失败: {}", taskId, file.getPath());
        }
    }
}
