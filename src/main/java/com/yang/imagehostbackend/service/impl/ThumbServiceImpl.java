package com.yang.imagehostbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yang.imagehostbackend.constant.RedisLuaScriptConstant;
import com.yang.imagehostbackend.exception.BusinessException;
import com.yang.imagehostbackend.exception.ErrorCode;
import com.yang.imagehostbackend.model.dto.thumb.DoThumbRequest;
import com.yang.imagehostbackend.model.dto.thumb.ThumbEvent;
import com.yang.imagehostbackend.model.entity.Thumb;
import com.yang.imagehostbackend.model.entity.User;
import com.yang.imagehostbackend.model.enums.LuaStatusEnum;
import com.yang.imagehostbackend.service.ThumbService;
import com.yang.imagehostbackend.mapper.ThumbMapper;
import com.yang.imagehostbackend.service.UserService;
import com.yang.imagehostbackend.utils.RedisKeyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;

/**
* @author Decades
* @description 针对表【thumb】的数据库操作Service实现
* @createDate 2025-05-13 16:10:01
*/
@Service
@Slf4j
@RequiredArgsConstructor
public class ThumbServiceImpl extends ServiceImpl<ThumbMapper, Thumb>
        implements ThumbService {

    private final UserService userService;

    private final RedisTemplate<String, Object> redisTemplate;

    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange:thumb-exchange}")
    private String thumbExchange;

    @Value("${rabbitmq.routing-key:thumb-routing-key}")
    private String thumbRoutingKey;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getPictureId() == null) {
            throw new RuntimeException("参数错误");
        }
        User loginUser = userService.getLoginUser(request);
        Long loginUserId = loginUser.getId();
        Long pictureId = doThumbRequest.getPictureId();
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUserId);

        try {
            // 执行 Lua 脚本，点赞存入 Redis
            long result = redisTemplate.execute(
                    RedisLuaScriptConstant.THUMB_SCRIPT_MQ,
                    List.of(userThumbKey),
                    pictureId
            );
            if (LuaStatusEnum.FAIL.getValue() == result) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"用户已点赞");
            }

            ThumbEvent thumbEvent = ThumbEvent.builder()
                    .pictureId(pictureId)
                    .userId(loginUserId)
                    .type(ThumbEvent.EventType.INCR)
                    .eventTime(LocalDateTime.now())
                    .build();
            log.info("发送到rabbitmq之前");
            // 发送消息到RabbitMQ
            try {
                rabbitTemplate.convertAndSend(thumbExchange, thumbRoutingKey, thumbEvent);
                log.info("点赞消息发送成功: userId={}, pictureId={}", loginUserId, pictureId);
            } catch (Exception e) {
                // 发送失败时，从Redis中删除点赞记录
                redisTemplate.opsForHash().delete(userThumbKey, pictureId.toString());
                log.error("点赞事件发送失败: userId={}, pictureId={}", loginUserId, pictureId, e);
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"点赞操作失败，请稍后重试");
            }

            return true;
        } catch (Exception e) {
            log.error("点赞操作失败: userId={}, pictureId={}", loginUserId, pictureId, e);
            // 确保Redis中没有点赞记录
            redisTemplate.opsForHash().delete(userThumbKey, pictureId.toString());
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getPictureId() == null) {
            throw new RuntimeException("参数错误");
        }
        User loginUser = userService.getLoginUser(request);
        Long loginUserId = loginUser.getId();
        Long pictureId = doThumbRequest.getPictureId();
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUserId);

        try {
            // 执行 Lua 脚本，点赞记录从 Redis 删除
            long result = redisTemplate.execute(
                    RedisLuaScriptConstant.UNTHUMB_SCRIPT_MQ,
                    List.of(userThumbKey),
                    pictureId
            );
            if (LuaStatusEnum.FAIL.getValue() == result) {
                throw new RuntimeException("用户未点赞");
            }

            ThumbEvent thumbEvent = ThumbEvent.builder()
                    .pictureId(pictureId)
                    .userId(loginUserId)
                    .type(ThumbEvent.EventType.DECR)
                    .eventTime(LocalDateTime.now())
                    .build();

            // 发送消息到RabbitMQ
            try {
                rabbitTemplate.convertAndSend(thumbExchange, thumbRoutingKey, thumbEvent);
                log.info("取消点赞消息发送成功: userId={}, pictureId={}", loginUserId, pictureId);
            } catch (Exception e) {
                // 发送失败时，恢复Redis中的点赞记录
                redisTemplate.opsForHash().put(userThumbKey, pictureId.toString(), true);
                log.error("取消点赞事件发送失败: userId={}, pictureId={}", loginUserId, pictureId, e);
                throw new RuntimeException("取消点赞操作失败，请稍后重试");
            }

            return true;
        } catch (Exception e) {
            log.error("取消点赞操作失败: userId={}, pictureId={}", loginUserId, pictureId, e);
            // 确保Redis中恢复点赞记录
            redisTemplate.opsForHash().put(userThumbKey, pictureId.toString(), true);
            throw e;
        }
    }

    @Override
    public Boolean hasThumb(Long pictureId, Long userId) {
        // 先从Redis缓存中查询
        String userThumbKey = RedisKeyUtil.getUserThumbKey(userId);
        Boolean hasThumb = redisTemplate.opsForHash().hasKey(userThumbKey, pictureId.toString());
        
        // 如果Redis中有记录，则直接返回
        if (Boolean.TRUE.equals(hasThumb)) {
            return true;
        }
        
        // Redis中没有记录，查询数据库
        LambdaQueryWrapper<Thumb> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Thumb::getUserId, userId)
                .eq(Thumb::getPictureId, pictureId);
        long count = this.count(queryWrapper);
        
        // 如果数据库中有记录，则更新Redis缓存并返回true
        if (count > 0) {
            redisTemplate.opsForHash().put(userThumbKey, pictureId.toString(), true);
            return true;
        }
        
        return false;
    }
}




