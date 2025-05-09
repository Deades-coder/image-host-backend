package com.yang.imagehostbackend.aop;

import com.yang.imagehostbackend.annotation.RateLimit;
import com.yang.imagehostbackend.exception.BusinessException;
import com.yang.imagehostbackend.exception.ErrorCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.util.Collections;

/**
 * @Author 小小星仔
 * @Create 2025-05-09 18:41
 */
@Aspect
@Component
public class RedisRateLimitAspect {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Around("@annotation(rateLimit)")
    public Object rateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        int capacity = rateLimit.capacity();
        int refillRate = rateLimit.refillRate();

        String key = "rate_limit:" + method.getName();
        String luaScript = "local current = tonumber(redis.call('GET', KEYS[1]) or 0) " +
                "local refillRate = tonumber(ARGV[1]) " +
                "local capacity = tonumber(ARGV[2]) " +
                "local currentTime = tonumber(redis.call('TIME')[1]) " +
                "local lastRefillTime = tonumber(redis.call('HGET', KEYS[1], 'lastRefillTime') or currentTime) " +
                "local tokensToAdd = (currentTime - lastRefillTime) * refillRate " +
                "current = math.min(capacity, current + tokensToAdd) " +
                "redis.call('HSET', KEYS[1], 'lastRefillTime', currentTime) " +
                "if current >= 1 then " +
                "   redis.call('HSET', KEYS[1], 'tokens', current - 1) " +
                "   return 1 " +
                "else " +
                "   return 0 " +
                "end";

        // 使用RedisScript执行Lua脚本
        RedisScript<Long> script = RedisScript.of(luaScript, Long.class);
        Long result = stringRedisTemplate.execute(script,
                Collections.singletonList(key),
                String.valueOf(refillRate), // 每秒填充的令牌数
                String.valueOf(capacity)); // 桶的最大容量

        if (result == 0) {
            throw new BusinessException(ErrorCode.TIME_LIMIT_ERROR,"请求过于频繁，请稍后再试");
        }

        return joinPoint.proceed();
    }
}