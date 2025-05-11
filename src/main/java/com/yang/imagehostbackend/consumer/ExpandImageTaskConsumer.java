package com.yang.imagehostbackend.consumer;

import com.yang.imagehostbackend.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.yang.imagehostbackend.model.dto.picture.ExpandImageTaskMessage;
import com.yang.imagehostbackend.model.entity.ExpendImageTask;
import com.yang.imagehostbackend.service.ExpendImageTaskService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 扩图任务消费者 - 优化版
 * @author 小小星仔
 * @Create 2025-05-11 22:26
 */
@Component
@Slf4j
public class ExpandImageTaskConsumer {

    @Resource
    private ExpendImageTaskService expendImageTaskService;
    
    @Resource
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${kafka.topics.expendimage-task}")
    private String expendImageTaskTopic;
    
    // 创建定时任务线程池
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    // 最大重试次数 - 增加到10次以适应长时间运行的AI任务
    private static final int MAX_RETRY_COUNT = 10;
    
    // 成功完成的状态
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    
    // 失败的状态
    private static final String STATUS_FAILED = "FAILED";
    
    // 正在处理的状态
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    
    /**
     * 消费扩图任务消息
     */
    @KafkaListener(topics = "${kafka.topics.expendimage-task}", groupId = "expand-image-task-group", 
                  concurrency = "3") // 提高并发度
    public void consumeExpandImageTask(ConsumerRecord<String, ExpandImageTaskMessage> record, Acknowledgment ack) {
        ExpandImageTaskMessage message = record.value();
        String taskId = message.getTaskId();
        log.info("收到扩图任务消息，taskId={}, 重试次数={}", taskId, message.getRetryCount());
        
        try {
            // 检查数据库中的任务状态 - 避免重复处理已完成的任务
            ExpendImageTask existingTask = expendImageTaskService.getByTaskId(taskId);
            if (existingTask != null && (STATUS_SUCCEEDED.equals(existingTask.getTask_status()) 
                    || STATUS_FAILED.equals(existingTask.getTask_status()))) {
                log.info("任务已处于终态，无需再处理：taskId={}, status={}", taskId, existingTask.getTask_status());
                ack.acknowledge();
                return;
            }
            
            // 查询任务状态
            GetOutPaintingTaskResponse taskResponse = expendImageTaskService.getOutPaintingTaskResult(taskId);
            if (taskResponse == null || taskResponse.getOutput() == null) {
                log.error("获取扩图任务结果失败，taskId={}", taskId);
                scheduleRetry(message, ack);
                return;
            }
            
            String taskStatus = taskResponse.getOutput().getTaskStatus();
            log.info("扩图任务状态：taskId={}，status={}", taskId, taskStatus);
            
            // 更新数据库中的任务状态 - 始终保持最新状态
            updateTaskStatus(taskId, taskStatus, taskResponse.getOutput().getOutputImageUrl());
            
            // 根据任务状态处理
            if (STATUS_SUCCEEDED.equals(taskStatus) || STATUS_FAILED.equals(taskStatus)) {
                // 任务已完成（成功或失败），处理结果
                expendImageTaskService.processOutPaintingTaskResult(taskId);
                log.info("扩图任务处理完成，taskId={}, status={}", taskId, taskStatus);
                ack.acknowledge();
            } else if (STATUS_PENDING.equals(taskStatus) || STATUS_RUNNING.equals(taskStatus)) {
                // 任务仍在进行中，重新发送到队列
                scheduleRetry(message, ack);
            } else {
                // 未知状态，也进行重试
                log.warn("未知的任务状态：taskId={}，status={}", taskId, taskStatus);
                scheduleRetry(message, ack);
            }
        } catch (Exception e) {
            log.error("处理扩图任务消息异常，taskId={}，错误信息：{}", taskId, e.getMessage(), e);
            scheduleRetry(message, ack);
        }
    }
    
    /**
     * 异步安排重试 - 不阻塞消费者线程
     */
    @Async
    protected void scheduleRetry(ExpandImageTaskMessage message, Acknowledgment ack) {
        try {
            // 增加重试次数
            int retryCount = message.getRetryCount() == null ? 0 : message.getRetryCount();
            message.setRetryCount(retryCount + 1);
            
            // 检查是否超过最大重试次数
            if (retryCount >= MAX_RETRY_COUNT) {
                log.warn("扩图任务重试次数超过最大限制，不再重试，taskId={}", message.getTaskId());
                // 更新数据库状态为失败
                updateTaskStatus(message.getTaskId(), STATUS_FAILED, null);
                ack.acknowledge();
                return;
            }
            
            // 计算延迟时间，采用指数退避策略
            // 初始延迟5秒, 随后 10s, 20s, 40s, 80s, 160s... 等
            long delaySeconds = (long) (5 * Math.pow(2, Math.min(retryCount, 7)));
            
            // 更新消息时间戳
            message.setCreateTime(LocalDateTime.now());
            
            // 使用ScheduledExecutorService进行延迟执行
            scheduler.schedule(() -> {
                try {
                    kafkaTemplate.send(expendImageTaskTopic, message.getTaskId(), message);
                    log.info("扩图任务已重新入队，taskId={}，重试次数={}，延迟={}秒", 
                            message.getTaskId(), message.getRetryCount(), delaySeconds);
                } catch (Exception e) {
                    log.error("扩图任务重新入队失败，taskId={}，错误信息：{}", 
                            message.getTaskId(), e.getMessage(), e);
                }
            }, delaySeconds, TimeUnit.SECONDS);
            
            // 确认当前消息已处理
            ack.acknowledge();
        } catch (Exception e) {
            log.error("安排重试任务失败", e);
            // 出现异常，也需要确认消息
            ack.acknowledge();
        }
    }
    
    /**
     * 更新任务状态
     */
    private void updateTaskStatus(String taskId, String taskStatus, String outputImageUrl) {
        try {
            ExpendImageTask task = new ExpendImageTask();
            task.setTask_id(taskId);
            task.setTask_status(taskStatus);
            task.setUpdate_time(new Date());
            
            // 如果有输出URL，也更新
            if (outputImageUrl != null) {
                task.setOutput_image_url(outputImageUrl);
            }
            
            expendImageTaskService.lambdaUpdate()
                .eq(ExpendImageTask::getTask_id, taskId)
                .update(task);
        } catch (Exception e) {
            log.error("更新任务状态失败，taskId={}，错误信息：{}", taskId, e.getMessage(), e);
        }
    }
} 