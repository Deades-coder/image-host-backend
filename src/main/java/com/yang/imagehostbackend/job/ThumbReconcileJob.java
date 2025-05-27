package com.yang.imagehostbackend.job;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.yang.imagehostbackend.constant.ThumbConstant;
import com.yang.imagehostbackend.model.dto.thumb.ThumbEvent;
import com.yang.imagehostbackend.model.entity.Thumb;
import com.yang.imagehostbackend.service.ThumbService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 点赞对账任务 - 优化版本
 * @Author 小小星仔
 * @Create 2025-05-13 16:58
 */
@Service
@Slf4j
public class ThumbReconcileJob {
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private ThumbService thumbService;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange:thumb-exchange}")
    private String thumbExchange;

    @Value("${rabbitmq.routing-key:thumb-routing-key}")
    private String thumbRoutingKey;

    @Value("${thumb.reconcile.batch-size:100}")
    private int batchSize;
    
    @Value("${thumb.reconcile.message-batch-size:50}")
    private int messageBatchSize = 50;
    
    @Value("${thumb.reconcile.max-retries:3}")
    private int maxRetries = 3;

    /**
     * 定时任务入口（每天凌晨2点执行）
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void run() {
        long startTime = System.currentTimeMillis();
        AtomicInteger processedUsers = new AtomicInteger(0);
        AtomicInteger totalEvents = new AtomicInteger(0);
        AtomicInteger usersWithDiff = new AtomicInteger(0);

        try {
            log.info("开始点赞对账任务");
            
            // 使用分段处理 Redis scan 结果
            List<CompletableFuture<Integer>> futures = new ArrayList<>();
            List<Long> currentBatch = new ArrayList<>(batchSize);
            
            String pattern = ThumbConstant.USER_THUMB_KEY_PREFIX + "*";
            try (Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions().match(pattern).count(1000).build())) {
                while (cursor.hasNext()) {
                    String key = cursor.next();
                    Long userId = Long.valueOf(key.replace(ThumbConstant.USER_THUMB_KEY_PREFIX, ""));
                    currentBatch.add(userId);
                    
                    if (currentBatch.size() >= batchSize) {
                        // 处理当前批次
                        futures.add(processUserBatchAsync(new ArrayList<>(currentBatch), processedUsers, totalEvents, usersWithDiff));
                        currentBatch.clear();
                    }
                }
                
                // 处理最后一批
                if (!currentBatch.isEmpty()) {
                    futures.add(processUserBatchAsync(new ArrayList<>(currentBatch), processedUsers, totalEvents, usersWithDiff));
                }
            }
            
            // 等待所有任务完成
            if (!futures.isEmpty()) {
                CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                        futures.toArray(new CompletableFuture[0])
                );
                
                // 获取总处理事件数
                allFutures.thenApply(v -> 
                    futures.stream()
                            .map(CompletableFuture::join)
                            .reduce(0, Integer::sum)
                ).thenAccept(total -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("点赞对账任务全部完成，总用户数: {}, 具有差异的用户数: {}, 处理事件总数: {}, 总耗时: {}ms", 
                            processedUsers.get(), usersWithDiff.get(), total, duration);
                }).join(); // 等待计算完成
            } else {
                log.info("没有需要对账的用户，任务结束");
            }

        } catch (Exception e) {
            log.error("点赞对账任务异常", e);
        }
    }

    /**
     * 异步处理一批用户
     * @return CompletableFuture 包含处理的事件数
     */
    @Async("taskExecutor")
    public CompletableFuture<Integer> processUserBatchAsync(List<Long> userIds, AtomicInteger processedUsers, 
                                                           AtomicInteger totalEvents, AtomicInteger usersWithDiff) {
        int batchEvents = 0;
        
        try {
            if (userIds.isEmpty()) {
                return CompletableFuture.completedFuture(0);
            }
            
            // 1. 批量查询所有用户的点赞数据
            List<Thumb> allThumbsInBatch = thumbService.lambdaQuery()
                    .in(Thumb::getUserId, userIds)
                    .list();
            
            // 2. 按用户ID分组
            Map<Long, List<Thumb>> thumbsByUser = allThumbsInBatch.stream()
                    .collect(Collectors.groupingBy(Thumb::getUserId));
            
            // 3. 处理每个用户
            for (Long userId : userIds) {
                try {
                    // 获取该用户在MySQL中的点赞记录
                    List<Thumb> userThumbs = thumbsByUser.getOrDefault(userId, Collections.emptyList());
                    
                    // 处理用户数据
                    int userEvents = processUser(userId, userThumbs);
                    if (userEvents > 0) {
                        usersWithDiff.incrementAndGet();
                    }
                    
                    batchEvents += userEvents;
                    processedUsers.incrementAndGet();
                } catch (Exception e) {
                    log.error("处理用户[{}]对账异常", userId, e);
                }
            }
            
            totalEvents.addAndGet(batchEvents);
            log.info("批量处理完成，处理用户数: {}, 处理事件数: {}", userIds.size(), batchEvents);
            
        } catch (Exception e) {
            log.error("批量处理用户异常", e);
        }
        
        return CompletableFuture.completedFuture(batchEvents);
    }

    /**
     * 处理单个用户的对账逻辑
     * @param userId 用户ID
     * @param userThumbs 用户在MySQL中的点赞记录
     * @return 处理的事件数量
     */
    private int processUser(Long userId, List<Thumb> userThumbs) {
        String userThumbKey = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;

        // 1. 获取Redis中的点赞记录
        Set<Long> redisPictureIds = redisTemplate.opsForHash().keys(userThumbKey)
                .stream()
                .map(obj -> Long.valueOf(obj.toString()))
                .collect(Collectors.toSet());

        if (redisPictureIds.isEmpty()) {
            return 0;
        }

        // 2. 转换MySQL中的点赞记录
        Set<Long> mysqlPictureIds = userThumbs.stream()
                .map(Thumb::getPictureId)
                .collect(Collectors.toSet());

        // 3. 计算差异（Redis有但MySQL无）
        Set<Long> diffPictureIds = Sets.difference(redisPictureIds, mysqlPictureIds);

        if (diffPictureIds.isEmpty()) {
            return 0;
        }

        log.info("用户[{}]对账检测到差异，Redis点赞数: {}, MySQL点赞数: {}, 差异数: {}",
                userId, redisPictureIds.size(), mysqlPictureIds.size(), diffPictureIds.size());

        // 4. 发送补偿事件
        sendCompensationEvents(userId, diffPictureIds);

        return diffPictureIds.size();
    }

    /**
     * 发送补偿事件到RabbitMQ（批量发送 + 重试机制）
     */
    private void sendCompensationEvents(Long userId, Set<Long> pictureIds) {
        if (pictureIds.isEmpty()) {
            return;
        }

        int successCount = 0;
        int failCount = 0;
        List<ThumbEvent> batchEvents = new ArrayList<>(messageBatchSize);
        
        for (Long pictureId : pictureIds) {
            ThumbEvent thumbEvent = new ThumbEvent(userId, pictureId, ThumbEvent.EventType.INCR, LocalDateTime.now());
            batchEvents.add(thumbEvent);
            
            // 每达到批次大小就发送一批
            if (batchEvents.size() >= messageBatchSize) {
                boolean sent = sendEventBatchWithRetry(batchEvents);
                if (sent) {
                    successCount += batchEvents.size();
                } else {
                    failCount += batchEvents.size();
                }
                batchEvents.clear();
            }
        }
        
        // 发送剩余的消息
        if (!batchEvents.isEmpty()) {
            boolean sent = sendEventBatchWithRetry(batchEvents);
            if (sent) {
                successCount += batchEvents.size();
            } else {
                failCount += batchEvents.size();
            }
        }

        if (successCount > 0) {
            log.info("用户[{}]补偿事件发送结果: 成功={}, 失败={}", userId, successCount, failCount);
        }
    }
    
    /**
     * 批量发送事件，带重试机制
     * @return 是否发送成功
     */
    private boolean sendEventBatchWithRetry(List<ThumbEvent> events) {
        if (events.isEmpty()) {
            return true;
        }
        
        // 尝试分别发送每个事件（如果RabbitMQ客户端支持批量发送，可以改为批量）
        int successCount = 0;
        
        for (ThumbEvent event : events) {
            int attempts = 0;
            boolean success = false;
            
            while (!success && attempts < maxRetries) {
                try {
                    rabbitTemplate.convertAndSend(thumbExchange, thumbRoutingKey, event);
                    success = true;
                    successCount++;
                } catch (Exception ex) {
                    attempts++;
                    if (attempts >= maxRetries) {
                        log.error("补偿事件发送最终失败: userId={}, pictureId={}, 尝试次数={}",
                                event.getUserId(), event.getPictureId(), attempts, ex);
                    } else {
                        log.warn("补偿事件发送失败(尝试 {}/{}): userId={}, pictureId={}",
                                attempts, maxRetries, event.getUserId(), event.getPictureId());
                        // 简单的退避策略
                        try {
                            Thread.sleep(100 * attempts);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
        }
        
        return successCount == events.size();
    }
}