package com.yang.imagehostbackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.yang.imagehostbackend.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
// 异步任务需要此注解
@EnableAsync
// 启用定时任务
@EnableScheduling
public class ImageHostBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImageHostBackendApplication.class, args);
    }

}
