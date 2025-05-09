package com.yang.imagehostbackend.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限流注解
 * @Author 小小星仔
 * @Create 2025-05-09 18:41
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    // 桶的最大容量
    int capacity() default 100;

    // 每秒填充的令牌数
    int refillRate() default 10;
}
