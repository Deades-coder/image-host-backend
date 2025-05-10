package com.yang.imagehostbackend.controller;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
/**
 * @Author 小小星仔
 * @Create 2025-05-09 21:05
 */
@RestController
//@RequestMapping("/upload")
public class UploadController {

    /**
     * 上传文件，异步处理
     *
     * @param file 前端上传的文件
     * @return 任务 ID
     */
    @PostMapping("/file")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        return null;
    }
}