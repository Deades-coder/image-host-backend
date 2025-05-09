package com.yang.imagehostbackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@MapperScan("com.yang.imagehostbackend.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
public class ImageHostBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImageHostBackendApplication.class, args);
    }

}
