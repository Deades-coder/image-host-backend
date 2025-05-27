package com.yang.imagehostbackend.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yang.imagehostbackend.mapper.PictureMapper;
import com.yang.imagehostbackend.mapper.ThumbMapper;
import com.yang.imagehostbackend.model.dto.thumb.ThumbEvent;
import com.yang.imagehostbackend.model.entity.Picture;
import com.yang.imagehostbackend.model.entity.Thumb;
import com.yang.imagehostbackend.service.PictureService;
import com.yang.imagehostbackend.service.impl.PictureServiceExtension;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * 简化版RabbitMQ消费者
 * @Author 小小星仔
 * @Create 2025-05-13 20:41
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ThumbMQConsumer {
    private final ThumbMapper thumbMapper;
    private final PictureMapper pictureMapper;
    private final PictureServiceExtension pictureServiceExtension;
    private final RedisTemplate<String, Object> redisTemplate;
    
    // 使用接口而不是实现类
    private final PictureService pictureService;
    
    /**
     * 监听点赞队列的消息
     * @param thumbEvent 点赞事件
     */
    @RabbitListener(queues = "${rabbitmq.queue:thumb-queue}")
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(ThumbEvent thumbEvent) {
        try {
            log.info("开始处理点赞事件: userId={}, pictureId={}, type={}", 
                    thumbEvent.getUserId(), thumbEvent.getPictureId(), thumbEvent.getType());

            Long userId = thumbEvent.getUserId();
            Long pictureId = thumbEvent.getPictureId();

            // 检查图片是否存在
            log.info("检查图片是否存在: pictureId={}", pictureId);
            Picture picture = pictureMapper.selectById(pictureId);
            if (picture == null) {
                log.warn("图片不存在: userId={}, pictureId={}", userId, pictureId);
                return;
            }
            log.info("图片存在: pictureId={}, name={}", pictureId, picture.getName());
            
            // 确保thumbCount列存在
            log.info("开始确保thumbCount列存在");
            try {
                pictureServiceExtension.addThumbCountColumnIfNotExists();
                log.info("thumbCount列添加成功或已存在");
            } catch (Exception e) {
                log.warn("添加thumbCount列失败，可能列已存在: {}", e.getMessage());
            }

            if (ThumbEvent.EventType.INCR.equals(thumbEvent.getType())) {
                log.info("开始处理点赞操作: userId={}, pictureId={}", userId, pictureId);
                processThumbUp(userId, pictureId);
            } else if (ThumbEvent.EventType.DECR.equals(thumbEvent.getType())) {
                log.info("开始处理取消点赞操作: userId={}, pictureId={}", userId, pictureId);
                processThumbDown(userId, pictureId);
            }

            log.info("处理成功: userId={}, pictureId={}, type={}", 
                    thumbEvent.getUserId(), thumbEvent.getPictureId(), thumbEvent.getType());

        } catch (Exception e) {
            log.error("处理点赞事件失败: userId={}, pictureId={}, type={}, error={}, stackTrace={}", 
                    thumbEvent.getUserId(), thumbEvent.getPictureId(), thumbEvent.getType(), 
                    e.getMessage(), e.getStackTrace());
        }
    }

    /**
     * 处理点赞事件
     */
    private void processThumbUp(Long userId, Long pictureId) {
        // 检查是否已经点赞，避免重复处理
        log.info("检查用户是否已点赞: userId={}, pictureId={}", userId, pictureId);
        LambdaQueryWrapper<Thumb> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Thumb::getUserId, userId)
                .eq(Thumb::getPictureId, pictureId);

        if (thumbMapper.selectCount(queryWrapper) > 0) {
            log.info("用户已点赞，无需重复处理: userId={}, pictureId={}", userId, pictureId);
            return;
        }
        log.info("用户未点赞，继续处理: userId={}, pictureId={}", userId, pictureId);

        // 插入点赞记录
        log.info("开始插入点赞记录: userId={}, pictureId={}", userId, pictureId);
        Thumb thumb = new Thumb();
        thumb.setUserId(userId);
        thumb.setPictureId(pictureId);
        thumb.setCreateTime(new Date());
        int rows = thumbMapper.insert(thumb);
        log.info("点赞记录插入结果: rows={}, userId={}, pictureId={}", rows, userId, pictureId);

        if (rows > 0) {
            // 更新图片点赞数
            log.info("开始更新图片点赞数: pictureId={}", pictureId);
            int updatedRows = pictureServiceExtension.incrementThumbCount(pictureId);
            log.info("图片点赞数更新结果: updatedRows={}, pictureId={}", updatedRows, pictureId);
            
            // 清除图片相关缓存
            log.info("开始清除图片缓存: pictureId={}", pictureId);
            pictureService.clearPictureCache(pictureId);
            log.info("图片缓存清除完成: pictureId={}", pictureId);
            
            log.info("点赞成功: userId={}, pictureId={}", userId, pictureId);
        }
    }

    /**
     * 处理取消点赞事件
     */
    private void processThumbDown(Long userId, Long pictureId) {
        // 检查是否已点赞
        log.info("检查用户是否已点赞: userId={}, pictureId={}", userId, pictureId);
        LambdaQueryWrapper<Thumb> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Thumb::getUserId, userId)
                .eq(Thumb::getPictureId, pictureId);

        if (thumbMapper.selectCount(queryWrapper) == 0) {
            log.info("用户未点赞，无需取消: userId={}, pictureId={}", userId, pictureId);
            return;
        }
        log.info("用户已点赞，继续处理取消点赞: userId={}, pictureId={}", userId, pictureId);

        // 删除点赞记录
        log.info("开始删除点赞记录: userId={}, pictureId={}", userId, pictureId);
        int rows = thumbMapper.delete(queryWrapper);
        log.info("点赞记录删除结果: rows={}, userId={}, pictureId={}", rows, userId, pictureId);

        if (rows > 0) {
            // 更新图片点赞数
            log.info("开始更新图片点赞数: pictureId={}", pictureId);
            int updatedRows = pictureServiceExtension.decrementThumbCount(pictureId);
            log.info("图片点赞数更新结果: updatedRows={}, pictureId={}", updatedRows, pictureId);
            
            // 清除图片相关缓存
            log.info("开始清除图片缓存: pictureId={}", pictureId);
            pictureService.clearPictureCache(pictureId);
            log.info("图片缓存清除完成: pictureId={}", pictureId);
            
            log.info("取消点赞成功: userId={}, pictureId={}", userId, pictureId);
        }
    }
}
