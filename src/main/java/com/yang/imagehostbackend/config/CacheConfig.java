package com.yang.imagehostbackend.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yang.imagehostbackend.model.vo.PictureVO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * caffine配置，单例模式
 * @Author 小小星仔
 * @Create 2025-05-10 19:24
 */
@Configuration
public class CacheConfig {
    @Bean
    public Cache<String, String> listVOPage(){
        return Caffeine.newBuilder().initialCapacity(1024)
                .maximumSize(10000L)
                // 缓存 5 分钟移除
                .expireAfterWrite(5L, TimeUnit.MINUTES)
                .build();
    }

    @Bean
    public Cache<Long, PictureVO> hotImage(){
        return  Caffeine.newBuilder().initialCapacity(1024)
                .maximumSize(10000L)
                // 缓存 5 分钟移除
                .expireAfterWrite(5L, TimeUnit.MINUTES)
                .build();
    }


}
