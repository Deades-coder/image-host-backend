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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 点赞对账任务 - 使用@Async注解简化
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

    /**
     * 定时任务入口（每天凌晨2点执行）
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void run() {
        long startTime = System.currentTimeMillis();
        AtomicInteger processedUsers = new AtomicInteger(0);
        AtomicInteger totalEvents = new AtomicInteger(0);

        try {
            // 1. 获取该分片下的所有用户ID
            Set<Long> userIds = new HashSet<>();
            String pattern = ThumbConstant.USER_THUMB_KEY_PREFIX + "*";
            try (Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions().match(pattern).count(1000).build())) {
                while (cursor.hasNext()) {
                    String key = cursor.next();
                    Long userId = Long.valueOf(key.replace(ThumbConstant.USER_THUMB_KEY_PREFIX, ""));
                    userIds.add(userId);
                }
            }

            int totalUsers = userIds.size();
            log.info("开始点赞对账任务，总用户数: {}", totalUsers);

            if (totalUsers == 0) {
                log.info("没有需要对账的用户，任务结束");
                return;
            }

            // 2. 分批处理用户ID列表
            List<List<Long>> batchedUserIds = Lists.partition(new ArrayList<>(userIds), batchSize);

            // 3. 异步处理每批用户
            for (List<Long> batchUserIds : batchedUserIds) {
                processUserBatchAsync(batchUserIds, processedUsers, totalEvents);
            }

            // 简化版本不等待所有任务完成
            long duration = System.currentTimeMillis() - startTime;
            log.info("点赞对账任务启动完成，总用户数: {}, 耗时: {}ms", totalUsers, duration);

        } catch (Exception e) {
            log.error("点赞对账任务异常", e);
        }
    }

    /**
     * 异步处理一批用户
     */
    @Async("taskExecutor")
    public void processUserBatchAsync(List<Long> userIds, AtomicInteger processedUsers, AtomicInteger totalEvents) {
        int batchEvents = 0;
        
        for (Long userId : userIds) {
            try {
                batchEvents += processUser(userId);
                processedUsers.incrementAndGet();
            } catch (Exception e) {
                log.error("处理用户[{}]对账异常", userId, e);
            }
        }
        
        totalEvents.addAndGet(batchEvents);
        log.info("批量处理完成，处理用户数: {}, 处理事件数: {}", userIds.size(), batchEvents);
    }

    /**
     * 处理单个用户的对账逻辑
     * @param userId 用户ID
     * @return 处理的事件数量
     */
    private int processUser(Long userId) {
        String userThumbKey = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;

        // 1. 获取Redis中的点赞记录
        Set<Long> redisPictureIds = redisTemplate.opsForHash().keys(userThumbKey)
                .stream()
                .map(obj -> Long.valueOf(obj.toString()))
                .collect(Collectors.toSet());

        if (redisPictureIds.isEmpty()) {
            return 0;
        }

        // 2. 获取MySQL中的点赞记录
        Set<Long> mysqlPictureIds = Optional.ofNullable(thumbService.lambdaQuery()
                        .eq(Thumb::getUserId, userId)
                        .list()
                ).orElse(new ArrayList<>())
                .stream()
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
     * 发送补偿事件到RabbitMQ
     */
    private void sendCompensationEvents(Long userId, Set<Long> pictureIds) {
        if (pictureIds.isEmpty()) {
            return;
        }

        int successCount = 0;
        int failCount = 0;

        for (Long pictureId : pictureIds) {
            ThumbEvent thumbEvent = new ThumbEvent(userId, pictureId, ThumbEvent.EventType.INCR, LocalDateTime.now());
            try {
                rabbitTemplate.convertAndSend(thumbExchange, thumbRoutingKey, thumbEvent);
                successCount++;
            } catch (Exception ex) {
                failCount++;
                log.error("补偿事件发送失败: userId={}, pictureId={}", userId, pictureId, ex);
            }
        }

        if (successCount > 0) {
            log.info("用户[{}]补偿事件发送结果: 成功={}, 失败={}", userId, successCount, failCount);
        }
    }
}