package com.yang.imagehostbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务
 * @Author 小小星仔
 * @Create 2025-05-10 10:13
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "uploadTaskExecutor")
    public ExecutorService uploadTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10); // 核心线程数
        executor.setMaxPoolSize(50); // 最大线程数
        executor.setQueueCapacity(200); // 队列容量
        executor.setThreadNamePrefix("Upload-Thread-"); // 线程名前缀
        executor.setKeepAliveSeconds(60); // 空闲线程存活时间
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy()); // 拒绝策略
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }
    
    @Bean(name = "taskExecutor")
    public ExecutorService taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5); // 核心线程数
        executor.setMaxPoolSize(10); // 最大线程数
        executor.setQueueCapacity(100); // 队列容量
        executor.setThreadNamePrefix("Thumb-Thread-"); // 线程名前缀
        executor.setKeepAliveSeconds(60); // 空闲线程存活时间
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy()); // 拒绝策略
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }
}