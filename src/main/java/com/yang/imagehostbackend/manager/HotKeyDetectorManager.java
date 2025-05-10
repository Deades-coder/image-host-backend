package com.yang.imagehostbackend.manager;

import com.github.benmanes.caffeine.cache.Cache;
import com.yang.imagehostbackend.model.entity.Picture;
import com.yang.imagehostbackend.model.vo.PictureVO;
import com.yang.imagehostbackend.service.PictureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Set;

/**
 * 定时任务热点数据检测
 * @Author 小小星仔
 * @Create 2025-05-10 19:22
 */
@Component
@EnableScheduling
@Slf4j
public class HotKeyDetectorManager {
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private PictureService pictureService;

    @Autowired
    private Cache<Long, PictureVO> hotImage;

    private static final String HOT_PICTURES_ZSET = "hot:pictures:";
    private static final int HOT_THRESHOLD = 1; // 访问量阈值

    // 每分钟检查一次热点图片
    @Scheduled(fixedRate = 6000)
    public void checkHotPictures() {
        // 获取访问量超过阈值的前10个图片ID
        Set<String> hotPictureIds = redisTemplate.opsForZSet()
                .rangeByScore(HOT_PICTURES_ZSET, HOT_THRESHOLD, Double.MAX_VALUE, 0, 10);
        if(hotPictureIds == null){
            System.out.println("Hot picture IDs: null");
        }
        for(String idStr : hotPictureIds){
            System.out.println("Hot picture ID: " + idStr);
        }
        if (hotPictureIds != null && !hotPictureIds.isEmpty()) {
            for (String idStr : hotPictureIds) {
                Long id = Long.parseLong(idStr);
                // 检查是否已在缓存中
                if (hotImage.getIfPresent(id) == null) {
                    // 直接查询数据库并生成PictureVO
                    Picture picture = pictureService.getById(id);
                    // 加载PictureVO到缓存
                    if (picture != null) {
                        PictureVO pictureVO = pictureService.getPictureVO(picture, null);
                        hotImage.put(id, pictureVO);
                        log.info("Hot picture cached: ID=" + id + LocalDateTime.now());
                    }
                }
            }
        }
    }
}
