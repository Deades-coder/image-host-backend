package com.yang.imagehostbackend.job;

import cn.hutool.core.date.DateUtil;
import com.yang.imagehostbackend.service.PictureSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * 图片-ES数据同步定时任务
 * @Author 小小星仔
 * @Create 2025-06-09 22:11
 */
@Component
@Slf4j
public class PictureEsSyncJob {

    @Resource
    private PictureSearchService pictureSearchService;

    @Value("${elasticsearch.sync.cron}")
    private String syncCron;

    /**
     * 记录上次同步时间
     */
    private volatile long lastSyncTimestamp = 0;

    /**
     * 初始化ES索引和数据（应用启动时执行一次）
     */
    //@PostConstruct  // 取消注释以启用应用启动时初始化
    public void initIndex() {
        try {
            log.info("开始初始化图片ES索引...");
            pictureSearchService.initPictureIndex();
            log.info("图片ES索引初始化完成");
            lastSyncTimestamp = System.currentTimeMillis();
        } catch (Exception e) {
            log.error("初始化图片ES索引失败", e);
        }
    }

    /**
     * 定时增量同步MySQL数据到ES
     * 根据配置的cron表达式执行
     */
    @Scheduled(cron = "${elasticsearch.sync.cron}")
    public void syncIncremental() {
        try {
            long currentTimestamp = System.currentTimeMillis();
            
            // 如果是第一次同步，则取当前时间5分钟前
            if (lastSyncTimestamp == 0) {
                lastSyncTimestamp = currentTimestamp - TimeUnit.MINUTES.toMillis(5);
            }
            
            log.info("开始增量同步图片数据到ES，时间范围：{} 至 {}", 
                    DateUtil.formatDateTime(new Date(lastSyncTimestamp)), 
                    DateUtil.formatDateTime(new Date(currentTimestamp)));
            
            // 执行增量同步
            int count = pictureSearchService.syncPictureIncremental(lastSyncTimestamp, currentTimestamp);
            
            // 更新同步时间戳
            lastSyncTimestamp = currentTimestamp;
            
            log.info("增量同步图片数据到ES完成，同步记录数：{}", count);
        } catch (Exception e) {
            log.error("增量同步图片数据到ES失败", e);
        }
    }
    
    /**
     * 执行一次全量同步（可手动触发）
     */
    public void fullSync() {
        try {
            log.info("开始全量同步图片数据到ES...");
            pictureSearchService.initPictureIndex();
            log.info("全量同步图片数据到ES完成");
            lastSyncTimestamp = System.currentTimeMillis();
        } catch (Exception e) {
            log.error("全量同步图片数据到ES失败", e);
        }
    }
} 